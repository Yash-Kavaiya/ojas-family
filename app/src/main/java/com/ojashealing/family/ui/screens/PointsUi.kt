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
import androidx.compose.runtime.collectAsState
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

/** Sending points, Swamiji's and admins' controls, and the activity amounts, for the points screens. */
class PointsActionsViewModel(private val repo: PointsRepository) : ViewModel() {
    val config: StateFlow<PointsConfig> = repo.observePointsConfig().catch { emit(PointsConfig()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointsConfig())

    private val _results = MutableStateFlow<List<GiftRecipient>?>(emptyList())
    val results: StateFlow<List<GiftRecipient>?> = _results
    private val _action = MutableStateFlow<PointsAction>(PointsAction.Idle)
    val action: StateFlow<PointsAction> = _action
    private val _transfers = MutableStateFlow<List<TransferRow>?>(null)
    val transfers: StateFlow<List<TransferRow>?> = _transfers
    private val _frozen = MutableStateFlow(false)
    val frozen: StateFlow<Boolean> = _frozen
    private var searchJob: Job? = null

    fun search(text: String) {
        searchJob?.cancel()
        if (text.trim().length < 2) {
            _results.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _results.value = null
            _results.value = repo.findGiftRecipients(text.trim()).getOrElse { emptyList() }
        }
    }

    private fun run(block: suspend () -> Result<Int>) {
        if (_action.value == PointsAction.Working) return
        _action.value = PointsAction.Working
        viewModelScope.launch {
            _action.value = block().fold({ PointsAction.Done(it) }, { PointsAction.Failed(serverMessage(it)) })
        }
    }

    fun send(to: GiftRecipient, amount: Int, note: String) = run { repo.sendPoints(to.uid, amount, note.trim()) }
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
    BulletRow(Icons.Filled.CardGiftcard, stringResource(R.string.points_explain_what_2))
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
    HorizontalDivider()
    Text(stringResource(R.string.points_explain_gift_title), style = MaterialTheme.typography.titleMedium)
    BulletRow(Icons.Filled.CardGiftcard, stringResource(R.string.points_explain_gift_1, config.minLevelToSend))
    BulletRow(Icons.Filled.TrendingUp, stringResource(R.string.points_explain_gift_2))
    BulletRow(Icons.Filled.CheckCircle, stringResource(R.string.points_explain_gift_3, config.maxTransfer, config.dailySendCap))
    BulletRow(Icons.Filled.Person, stringResource(R.string.points_explain_gift_4))
}

@Composable
private fun BulletRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Sending points to another member
// ---------------------------------------------------------------------------------------------

/**
 * Find a member by name, choose how many points to give and add a short note. The confirm step
 * says plainly what will happen to both balances before anything moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendPointsSheet(vm: PointsActionsViewModel, balance: Int, myLevel: Int, frozen: Boolean, onDismiss: () -> Unit) {
    val config by vm.config.collectAsState()
    val results by vm.results.collectAsState()
    val action by vm.action.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var to by remember { mutableStateOf<GiftRecipient?>(null) }
    var amount by rememberSaveable { mutableIntStateOf(5) }
    var note by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.reset() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { vm.reset(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.xl).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.points_send_title), style = MaterialTheme.typography.headlineSmall)
            val blocked = when {
                !config.transfersEnabled -> stringResource(R.string.points_send_off)
                frozen -> stringResource(R.string.points_send_frozen)
                myLevel < config.minLevelToSend -> stringResource(R.string.points_send_level, config.minLevelToSend)
                balance <= 0 -> stringResource(R.string.points_send_empty)
                else -> null
            }
            if (blocked != null) {
                Text(blocked, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_close)) }
                return@Column
            }
            AnimatedContent(
                targetState = when {
                    action is PointsAction.Done -> 2
                    to != null -> 1
                    else -> 0
                },
                transitionSpec = { fadeIn(tween(Durations.Base, easing = OjasEasing.Emphasized)) togetherWith fadeOut(tween(Durations.Quick)) },
                label = "sendStep",
            ) { step ->
                when (step) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it; vm.search(it) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            placeholder = { Text(stringResource(R.string.points_send_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val list = results
                        when {
                            list == null -> Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            query.trim().length < 2 -> Text(stringResource(R.string.points_send_search_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            list.isEmpty() -> Text(stringResource(R.string.points_send_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                                items(list, key = { it.uid }) { m -> RecipientRow(m) { to = m; amount = amount.coerceIn(1, minOf(config.maxTransfer, balance)) } }
                            }
                        }
                    }
                    1 -> to?.let { who ->
                        val max = minOf(config.maxTransfer, balance).coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            RecipientRow(who, onClick = { to = null })
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                FilledTonalIconButton(onClick = { amount = (amount - 1).coerceAtLeast(1) }, enabled = amount > 1) { Icon(Icons.Filled.Remove, stringResource(R.string.points_less)) }
                                Text("$amount", style = com.ojashealing.family.ui.theme.BrandNumberLargeStyle, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
                                FilledTonalIconButton(onClick = { amount = (amount + 1).coerceAtMost(max) }, enabled = amount < max) { Icon(Icons.Filled.Add, stringResource(R.string.points_more)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                                listOf(5, 11, 21, 51).filter { it <= max }.forEach { v ->
                                    FilterChip(selected = amount == v, onClick = { amount = v }, label = { Text("$v") })
                                }
                            }
                            OutlinedTextField(
                                value = note, onValueChange = { note = it.take(200) },
                                placeholder = { Text(stringResource(R.string.points_send_note)) },
                                modifier = Modifier.fillMaxWidth(), maxLines = 3,
                            )
                            Text(
                                stringResource(R.string.points_send_confirm, amount, who.name, balance - amount),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            (action as? PointsAction.Failed)?.let {
                                Text(it.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(
                                onClick = { vm.send(who, amount, note) },
                                enabled = action != PointsAction.Working,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                if (action == PointsAction.Working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else Text(pluralStringResource(R.plurals.points_send_button, amount, amount, who.name))
                            }
                        }
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.gold)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CardGiftcard, null, tint = Color(0xFF6B3A00), modifier = Modifier.size(36.dp))
                        }
                        Text(stringResource(R.string.points_sent_title, amount, to?.name.orEmpty()), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.points_sent_balance, (action as? PointsAction.Done)?.value ?: 0), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { vm.reset(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.points_explain_done)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(m: GiftRecipient, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (m.photoUrl.isNotEmpty()) {
            AsyncImage(m.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(m.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.profile_level, m.userLevel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Swamiji and admins: full control over one member's points
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePointsSheet(vm: PointsActionsViewModel, uid: String, name: String, onDismiss: () -> Unit) {
    val action by vm.action.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val frozen by vm.frozen.collectAsState()
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
    val config by vm.config.collectAsState()
    val action by vm.action.collectAsState()
    val values = remember(config) { mutableStateMapOf<String, String>().apply { config.numbers().forEach { (k, v) -> put(k, "$v") } } }
    var enabled by remember(config) { mutableStateOf(config.transfersEnabled) }
    LaunchedEffect(Unit) { vm.reset() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.reset(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.points_edit_amounts), style = MaterialTheme.typography.headlineSmall)
            config.numbers().forEach { (key, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(amountLabel(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = values[key].orEmpty(), onValueChange = { v -> values[key] = v.filter(Char::isDigit).take(6) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.points_cfg_transfers), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
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
                    out["transfersEnabled"] = enabled
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
        "aiDaily" -> R.string.earn_ai
        "maxTransfer" -> R.string.points_cfg_max_gift
        "dailySendCap" -> R.string.points_cfg_daily_cap
        else -> R.string.points_cfg_min_level
    }
)
