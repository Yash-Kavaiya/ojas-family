package com.ojashealing.family.data.paid

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

/** A product as Google Play prices it for this user; [price] is already formatted ("₹100.00"). */
data class Offer(val product: Product, val price: String, internal val details: ProductDetails, internal val offerToken: String?)

sealed interface PurchaseState {
    data object Idle : PurchaseState
    data object Working : PurchaseState
    data object Done : PurchaseState
    data object Pending : PurchaseState // e.g. UPI or cash payment not finished yet
    data class Failed(val code: Int) : PurchaseState
}

/**
 * Google Play Billing. The app never grants anything itself: each purchase token goes to the
 * verifyPurchase function, which checks it with Google and writes the entitlement.
 */
class PlayBilling(context: Context, private val paid: PaidRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _offers = MutableStateFlow<List<Offer>?>(null)
    /** null while loading; empty when Play has no products (store not set up yet, or no Play Store). */
    val offers: StateFlow<List<Offer>?> = _offers.asStateFlow()
    private val _state = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val state: StateFlow<PurchaseState> = _state.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchases(result, purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /** Loads prices and re-sends any purchase the server has not confirmed yet (e.g. the app closed mid-way). */
    fun refresh() {
        scope.launch {
            if (kotlinx.coroutines.withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connect() } != true) {
                _offers.value = emptyList()
                return@launch
            }
            _offers.value = query(BillingClient.ProductType.SUBS) + query(BillingClient.ProductType.INAPP)
            for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
                // Quietly: a failed retry here must not show "payment failed" to someone who only opened the screen
                ownedPurchases(type).filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                    .forEach { verify(it, quiet = true) }
            }
        }
    }

    private suspend fun query(type: String): List<Offer> {
        val products = Product.entries.filter { (it.subscription && type == BillingClient.ProductType.SUBS) || (!it.subscription && type == BillingClient.ProductType.INAPP) }
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            products.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(type).build() }
        ).build()
        val details: List<ProductDetails> = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { _, result -> if (cont.isActive) cont.resume(result.productDetailsList) }
        }
        return details.mapNotNull { d ->
            val product = Product.fromId(d.productId) ?: return@mapNotNull null
            if (product.subscription) {
                val offer = d.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
                Offer(product, price, d, offer.offerToken)
            } else {
                val price = d.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
                Offer(product, price, d, null)
            }
        }
    }

    private suspend fun ownedPurchases(type: String): List<Purchase> = suspendCancellableCoroutine { cont ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { _, list ->
            if (cont.isActive) cont.resume(list)
        }
    }

    fun buy(activity: Activity, offer: Offer, uid: String) {
        _state.value = PurchaseState.Working
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
            .apply { offer.offerToken?.let { setOfferToken(it) } }.build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            // Lets Google flag a purchase made for a different account than the one signed in
            .setObfuscatedAccountId(sha256(uid))
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) _state.value = PurchaseState.Failed(result.responseCode)
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PENDING) _state.value = PurchaseState.Pending
                else if (p.purchaseState == Purchase.PurchaseState.PURCHASED) scope.launch { verify(p) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.value = PurchaseState.Idle
            else -> _state.value = PurchaseState.Failed(result.responseCode)
        }
    }

    private suspend fun verify(p: Purchase, quiet: Boolean = false) {
        val productId = p.products.firstOrNull() ?: return
        paid.verify(productId, p.purchaseToken)
            .onSuccess { _state.value = PurchaseState.Done }
            .onFailure { if (!quiet) _state.value = PurchaseState.Failed(-1) }
    }

    fun resetState() {
        _state.value = PurchaseState.Idle
    }

    private companion object {
        // Play normally answers at once; this only stops the price list loading forever when it never does
        const val CONNECT_TIMEOUT_MS = 15_000L
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
