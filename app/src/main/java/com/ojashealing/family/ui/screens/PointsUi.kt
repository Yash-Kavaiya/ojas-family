package com.ojashealing.family.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.points.GiftRecipient
import com.ojashealing.family.data.points.LevelTargets
import com.ojashealing.family.data.points.PointsConfig
import com.ojashealing.family.data.points.PointsRepository
import com.ojashealing.family.data.points.TransferRow
import com.ojashealing.family.data.points.serverMessage
import com.ojashealing.family.ui.theme.BrandNumberStyle
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.theme.staggeredEntrance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where a points action stands: idle, working, done (with a number to show) or refused. */
sealed interface PointsAction {
    data object Idle : PointsAction
    data object Working : PointsAction
    data class Done(val value: Int) : PointsAction
    /** [message] is the server's own explanation (a limit, the balance); null means a generic error. */
    data class Failed(val message: String?) : PointsAction
}

/** Swamiji's and admins' controls and the activity amounts, for the points screens. */
class PointsActionsViewModel(private val repo: PointsRepository) : ViewModel() {
    val config: StateFlow<PointsConfig> = repo.observePointsConfig().catch { emit(PointsConfig()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointsConfig())

    private val _action = MutableStateFlow<PointsAction>(PointsAction.Idle)
    val action: StateFlow<PointsAction> = _action
    private val _transfers = MutableStateFlow<List<TransferRow>?>(null)
    val transfers: StateFlow<List<TransferRow>?> = _transfers
    private val _frozen = MutableStateFlow(false)
    val frozen: StateFlow<Boolean> = _frozen
    private fun run(block: suspend () -> Result<Int>) {
        if (_action.value == PointsAction.Working) return
        _action.value = PointsAction.Working
        viewModelScope.launch {
            _action.value = block().fold({ PointsAction.Done(it) }, { PointsAction.Failed(serverMessage(it)) })
        }
    }

    fun adjust(uid: String, mode: String, amount: Int, countForLevel: Boolean, note: String) =
        run { repo.staffAdjust(uid, mode, amount, countForLevel, note.trim()) }
    fun freeze(uid: String, frozen: Boolean) = run { repo.setTransfersFrozen(uid, frozen).map { if (frozen) 1 else 0 } }
    fun reverse(id: String, ownerUid: String) = run {
        repo.reverseTransfer(id).onSuccess { loadTransfers(ownerUid) }
    }
    fun saveConfig(values: Map<String, Any>) = run { repo.updatePointsConfig(values).map { 0 } }

    fun loadTransfers(uid: String) {
        viewModelScope.launch { _transfers.value = repo.recentTransfers(uid).getOrElse { emptyList() } }
        viewModelScope.launch { _frozen.value = repo.transfersFrozen(uid).getOrElse { false } }
    }

    fun reset() {
        _action.value = PointsAction.Idle
    }
}

// ---------------------------------------------------------------------------------------------
// How Moksha Points work: three short pages
// ---------------------------------------------------------------------------------------------

private data class EarnRow(val icon: ImageVector, val color: Color, @StringRes val label: Int, val points: Int, @StringRes val cap: Int? = null)

private fun earnRows(c: PointsConfig): List<EarnRow> = listOf(
    EarnRow(Icons.Filled.WbSunny, Color(0xFFF59E0B), R.string.earn_daily, c.dailyOpen, R.string.earn_cap_daily),
    EarnRow(Icons.Filled.SelfImprovement, Color(0xFF0284C7), R.string.earn_practice, c.practice, R.string.earn_cap_each_practice),
    EarnRow(Icons.Filled.CheckCircle, Color(0xFF0F9D8A), R.string.earn_all_practices, c.allPracticesBonus, R.string.earn_cap_daily),
    EarnRow(Icons.Filled.LocalFireDepartment, Color(0xFFFF7A2F), R.string.earn_streak, c.streakBonus, null),
    EarnRow(Icons.Outlined.Forum, Color(0xFFE11D48), R.string.earn_community, c.like, R.string.earn_cap_community),
    EarnRow(Icons.Filled.Person, Color(0xFF7C3AED), R.string.earn_profile_item, c.profileField, R.string.earn_cap_once),
    EarnRow(Icons.Filled.Stars, Color(0xFFD69E00), R.string.earn_profile_complete, c.profileComplete, R.string.earn_cap_once),
    EarnRow(Icons.Filled.AutoAwesome, Color(0xFF2F7CF6), R.string.earn_ai, c.aiDaily, R.string.earn_cap_daily),
    EarnRow(Icons.Outlined.QuestionAnswer, Color(0xFF9A5B00), R.string.earn_goals, 0, null),
)

/**
 * Explains Moksha Points before anything else: what they are, every way to earn them (amounts
 * read from config/points, so it always matches the server), and Levels, gifts and limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsExplainerSheet(config: PointsConfig, targets: LevelTargets, onDismiss: () -> Unit, onEditAmounts: (() -> Unit)? = null) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().heightIn(min = 380.dp)) { page ->
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    when (page) {
                        0 -> ExplainWhat()
                        1 -> ExplainEarn(config)
                        else -> ExplainLevels(config, targets)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(3) { i ->
                        val w by animateFloatAsState(if (pager.currentPage == i) 22f else 8f, tween(Durations.Base, easing = OjasEasing.Emphasized), label = "dot")
                        Box(
                            Modifier.height(8.dp).width(w.dp).clip(CircleShape)
                                .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                if (onEditAmounts != null && pager.currentPage == 1) {
                    TextButton(onClick = onEditAmounts) { Text(stringResource(R.string.points_edit_amounts)) }
                }
                Button(onClick = {
                    if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onDismiss()
                }) { Text(stringResource(if (pager.currentPage < 2) R.string.action_next else R.string.points_explain_done)) }
            }
        }
    }
}

@Composable
private fun ExplainHeader(icon: ImageVector, title: String, body: String) {
    Box(
        Modifier.staggeredEntrance(0, key = title).size(64.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.gold)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color(0xFF6B3A00), modifier = Modifier.size(34.dp)) }
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.staggeredEntrance(1, key = title))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.staggeredEntrance(2, key = title))
}

@Composable
private fun ExplainWhat() {
    ExplainHeader(Icons.Filled.Stars, stringResource(R.string.points_explain_what_title), stringResource(R.string.points_explain_what_body))
    BulletRow(Icons.Filled.TrendingUp, stringResource(R.string.points_explain_what_1))
    BulletRow(Icons.Filled.CheckCircle, stringResource(R.string.points_explain_what_3))
}

@Composable
private fun ExplainEarn(config: PointsConfig) {
    ExplainHeader(Icons.Filled.SelfImprovement, stringResource(R.string.points_explain_earn_title), stringResource(R.string.points_explain_earn_body))
    earnRows(config).forEachIndexed { i, row ->
        Row(
            Modifier.fillMaxWidth().staggeredEntrance(3 + i, key = "earn").clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(row.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(row.icon, null, tint = row.color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (row.label == R.string.earn_streak) stringResource(row.label, config.streakBonusEvery) else stringResource(row.label),
                    style = MaterialTheme.typography.titleSmall,
                )
                row.cap?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (row.points > 0) Text("+${row.points}", style = BrandNumberStyle, color = Color(0xFF15803D))
        }
    }
}

@Composable
private fun ExplainLevels(config: PointsConfig, targets: LevelTargets) {
    ExplainHeader(Icons.Filled.TrendingUp, stringResource(R.string.points_explain_levels_title), stringResource(R.string.points_explain_levels_body))
    listOf(2 to targets.l2, 3 to targets.l3, 4 to targets.l4).forEach { (lvl, t) ->
        BulletRow(Icons.Filled.Stars, stringResource(R.string.points_explain_level_row, lvl, t.days, t.points))
    }
}

@Composable
private fun BulletRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Swamiji and admins: full control over one member's points
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePointsSheet(vm: PointsActionsViewModel, uid: String, name: String, onDismiss: () -> Unit) {
    val action by vm.action.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val frozen by vm.frozen.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("add") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var forLevel by rememberSaveable { mutableStateOf(false) }
    var isFrozen by remember(frozen) { mutableStateOf(frozen) }
    LaunchedEffect(uid) { vm.reset(); vm.loadTransfers(uid) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.reset(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.points_manage_title, name), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf("add" to R.string.points_mode_add, "deduct" to R.string.points_mode_deduct, "set" to R.string.points_mode_set).forEach { (m, label) ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(stringResource(label)) })
                }
            }
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it.filter(Char::isDigit).take(7) },
                label = { Text(stringResource(R.string.points_amount)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(200) },
                label = { Text(stringResource(R.string.points_reason)) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.points_for_level), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.points_for_level_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = forLevel, onCheckedChange = { forLevel = it })
            }
            val amount = amountText.toIntOrNull()
            when (val a = action) {
                is PointsAction.Failed -> Text(a.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                is PointsAction.Done -> Text(stringResource(R.string.points_saved), color = Color(0xFF15803D))
                else -> Unit
            }
            Button(
                onClick = { if (amount != null) vm.adjust(uid, mode, amount, forLevel, note) },
                enabled = amount != null && (amount > 0 || mode == "set") && note.isNotBlank() && action != PointsAction.Working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.points_apply)) }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.points_freeze), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.points_freeze_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isFrozen, onCheckedChange = { isFrozen = it; vm.freeze(uid, it) })
            }
            HorizontalDivider()
            Text(stringResource(R.string.points_recent_gifts), style = MaterialTheme.typography.titleSmall)
            val list = transfers
            when {
                list == null -> CircularProgressIndicator(Modifier.size(24.dp))
                list.isEmpty() -> Text(stringResource(R.string.points_no_gifts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> list.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (t.sent) stringResource(R.string.points_gift_to, t.amount, t.toName) else stringResource(R.string.points_gift_from, t.amount, t.fromName),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (t.note.isNotEmpty()) Text("“${t.note}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (t.reversed) Text(stringResource(R.string.points_reversed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else TextButton(onClick = { vm.reverse(t.id, uid) }) { Text(stringResource(R.string.points_reverse)) }
                    }
                }
            }
        }
    }
}

/** Swamiji and admins set how many points each activity earns, and the gifting limits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsAmountsSheet(vm: PointsActionsViewModel, onDismiss: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val values = remember(config) { mutableStateMapOf<String, String>().apply { config.earningNumbers().forEach { (k, v) -> put(k, "$v") } } }
    LaunchedEffect(Unit) { vm.reset() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.reset(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.points_edit_amounts), style = MaterialTheme.typography.headlineSmall)
            config.earningNumbers().forEach { (key, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(amountLabel(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = values[key].orEmpty(), onValueChange = { v -> values[key] = v.filter(Char::isDigit).take(6) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp),
                    )
                }
            }
            when (val a = action) {
                is PointsAction.Failed -> Text(a.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                is PointsAction.Done -> Text(stringResource(R.string.points_saved), color = Color(0xFF15803D))
                else -> Unit
            }
            Spacer(Modifier.height(Spacing.s))
            Button(
                onClick = {
                    val out = HashMap<String, Any>()
                    values.forEach { (k, v) -> v.toIntOrNull()?.let { out[k] = it } }
                    // Members can no longer send points to each other
                    out["transfersEnabled"] = false
                    vm.saveConfig(out)
                },
                enabled = action != PointsAction.Working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun amountLabel(key: String): String = stringResource(
    when (key) {
        "dailyOpen" -> R.string.earn_daily
        "practice" -> R.string.earn_practice
        "allPracticesBonus" -> R.string.earn_all_practices
        "streakBonus" -> R.string.points_cfg_streak_bonus
        "streakBonusEvery" -> R.string.points_cfg_streak_every
        "like" -> R.string.points_src_like_thought
        "comment" -> R.string.points_src_comment
        "question" -> R.string.points_src_question
        "answer" -> R.string.points_src_answer
        "profileField" -> R.string.earn_profile_item
        "profileComplete" -> R.string.earn_profile_complete
        else -> R.string.earn_ai
    }
)
