package com.ojashealing.family.ui.paid

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.paid.Entitlements
import com.ojashealing.family.data.paid.Offer
import com.ojashealing.family.data.paid.PaidAccess
import com.ojashealing.family.data.paid.PlayBilling
import com.ojashealing.family.data.paid.Product
import com.ojashealing.family.data.paid.PurchaseState
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.Plan
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.onboarding.OnboardingLayout
import com.ojashealing.family.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything is on sale to everyone (plan table, 2026-11-20); [activeUntil] is set while it runs. */
data class PlanUi(val product: Product, val offer: Offer?, val activeUntil: Long?)

/** Until when [product] is running for this member: Silver and Gold share the membership, told apart by plan. */
fun activeUntil(product: Product, v: Viewer, e: Entitlements, now: Long): Long? {
    val until = e[product.access]?.takeIf { it.isActive(now) }?.expiresAt ?: return null
    return when (product) {
        Product.Silver -> until.takeIf { v.plan != Plan.Gold }
        Product.Gold -> until.takeIf { v.plan == Plan.Gold }
        else -> until
    }
}

data class PaidUi(
    val profile: UserProfile,
    val entitlements: Entitlements,
    val plans: List<PlanUi>,
    /** false when Play has no products (store not set up yet): show "contact the team" instead of prices. */
    val storeReady: Boolean,
    val purchase: PurchaseState
)

class PaidViewModel(
    profile: Flow<UserProfile?>,
    entitlements: Flow<Entitlements>,
    private val billing: PlayBilling
) : ViewModel() {

    init {
        billing.refresh()
    }

    val ui: StateFlow<PaidUi?> = combine(profile, entitlements, billing.offers, billing.state) { p, e, offers, state ->
        if (p == null) return@combine null
        val now = System.currentTimeMillis()
        PaidUi(
            profile = p,
            entitlements = e,
            plans = Product.entries.map { prod ->
                PlanUi(prod, offers?.firstOrNull { it.product == prod }, activeUntil(prod, p.viewer, e, now))
            },
            storeReady = !offers.isNullOrEmpty(),
            purchase = state
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun buy(activity: Activity, offer: Offer) {
        val uid = ui.value?.profile?.uid ?: return
        billing.buy(activity, offer, uid)
    }

    fun dismissResult() = billing.resetState()
}

fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@StringRes private fun Product.title() = when (this) {
    Product.Silver -> R.string.plan_membership
    Product.Gold -> R.string.plan_gold
    Product.ChatWeek -> R.string.plan_chat_week
    Product.ChatMonth -> R.string.plan_chat_month
    Product.CallWeek -> R.string.plan_call_week
    Product.CallMonth -> R.string.plan_call_month
}

@StringRes private fun Product.desc() = when (this) {
    Product.Silver -> R.string.plan_membership_desc
    Product.Gold -> R.string.plan_gold_desc
    Product.ChatWeek -> R.string.plan_chat_week_desc
    Product.ChatMonth -> R.string.plan_chat_month_desc
    Product.CallWeek -> R.string.plan_call_week_desc
    Product.CallMonth -> R.string.plan_call_month_desc
}

/** All paid plans with Play prices; [only] narrows to one access kind (e.g. from the chat card). */
@Composable
fun PlansScreen(vm: PaidViewModel, onBack: () -> Unit, only: PaidAccess? = null) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.dismissResult() }
    OnboardingLayout(title = stringResource(R.string.membership_plans), subtitle = null, onBack = onBack) {
        val u = ui ?: return@OnboardingLayout LoadingState()
        when (val s = u.purchase) {
            PurchaseState.Done -> Text(stringResource(R.string.purchase_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            PurchaseState.Pending -> Text(stringResource(R.string.purchase_pending), style = MaterialTheme.typography.bodyLarge)
            is PurchaseState.Failed -> Text(stringResource(R.string.purchase_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            else -> Unit
        }
        if (!u.storeReady) {
            OjasCard { Text(stringResource(R.string.store_unavailable), style = MaterialTheme.typography.bodyLarge) }
        }
        u.plans.filter { only == null || it.product.access == only }.forEach { plan ->
            OjasCard {
                Text(stringResource(plan.product.title()), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(plan.product.desc()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                plan.activeUntil?.let { Text(stringResource(R.string.plan_active, formatDate(it)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                // Gold already running: nothing to buy on the Silver card
                val goldRunning = plan.product == Product.Silver && u.plans.any { it.product == Product.Gold && it.activeUntil != null }
                if (!goldRunning) plan.offer?.let { offer ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(
                            onClick = { context.activity()?.let { vm.buy(it, offer) } },
                            enabled = u.purchase != PurchaseState.Working,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.plan_buy, offer.price)) }
                        if (plan.product == Product.Gold && u.plans.any { it.product == Product.Silver && it.activeUntil != null }) {
                            Text(stringResource(R.string.plan_gold_upgrade_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
