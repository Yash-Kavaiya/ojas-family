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
import com.ojashealing.family.domain.Permissions
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

/** Why a plan can't be bought yet, if it can't. */
sealed interface PlanGate {
    data object Open : PlanGate
    data class NeedsLevel(val level: Int) : PlanGate
    data class NeedsMember(val level: Int) : PlanGate
}

// Spec 4.2 / 4.3 and design §9: weekly packs from Level 2, monthly chat Member 2, anytime calls Member 3
fun gateFor(product: Product, v: Viewer): PlanGate = when (product) {
    Product.Membership -> PlanGate.Open
    Product.ChatWeek -> if (Permissions.canBuyWeeklyChat(v)) PlanGate.Open else PlanGate.NeedsLevel(2)
    Product.CallWeek -> if (Permissions.canBuyWeeklyCalls(v)) PlanGate.Open else PlanGate.NeedsLevel(2)
    Product.ChatMonth -> if (Permissions.canBuyMonthlyChat(v)) PlanGate.Open else PlanGate.NeedsMember(2)
    Product.CallMonth -> if (Permissions.canBuyAnytimeCalls(v)) PlanGate.Open else PlanGate.NeedsMember(3)
}

data class PlanUi(val product: Product, val offer: Offer?, val gate: PlanGate, val activeUntil: Long?)

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
                PlanUi(prod, offers?.firstOrNull { it.product == prod }, gateFor(prod, p.viewer), e[prod.access]?.takeIf { it.isActive(now) }?.expiresAt)
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
    Product.Membership -> R.string.plan_membership
    Product.ChatWeek -> R.string.plan_chat_week
    Product.ChatMonth -> R.string.plan_chat_month
    Product.CallWeek -> R.string.plan_call_week
    Product.CallMonth -> R.string.plan_call_month
}

@StringRes private fun Product.desc() = when (this) {
    Product.Membership -> R.string.plan_membership_desc
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
                when (val g = plan.gate) {
                    is PlanGate.NeedsLevel -> Text(stringResource(R.string.plan_requires_level, g.level), style = MaterialTheme.typography.bodySmall)
                    is PlanGate.NeedsMember -> Text(stringResource(R.string.plan_requires_member, g.level), style = MaterialTheme.typography.bodySmall)
                    PlanGate.Open -> plan.offer?.let { offer ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Button(
                                onClick = { context.activity()?.let { vm.buy(it, offer) } },
                                enabled = u.purchase != PurchaseState.Working,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.plan_buy, offer.price)) }
                        }
                    }
                }
            }
        }
    }
}
