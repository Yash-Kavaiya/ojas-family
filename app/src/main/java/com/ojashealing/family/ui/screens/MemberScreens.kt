package com.ojashealing.family.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.goals.Goal
import com.ojashealing.family.data.location.LocationRepository
import com.ojashealing.family.data.paid.Entitlements
import com.ojashealing.family.data.paid.PaidAccess
import com.ojashealing.family.data.paid.PaidRepository
import com.ojashealing.family.data.social.toId
import com.ojashealing.family.data.user.MemberRow
import com.ojashealing.family.data.user.PublicProfile
import com.ojashealing.family.data.user.PublicProfileRepository
import com.ojashealing.family.data.user.ageFromBirthMonth
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.Role
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.goals.goalLanguage
import com.ojashealing.family.ui.paid.formatDate
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** Another member's profile (admins, staff, Member Level 3). Only age, never the date of birth. */
@Composable
fun PublicProfileScreen(profile: Flow<PublicProfile?>, goals: List<Goal>, locations: LocationRepository, onBack: () -> Unit) {
    val p by profile.collectAsStateWithLifecycle(initialValue = null)
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(p) { if (p != null) loaded = true }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2_000); loaded = true }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            BackBar(stringResource(R.string.profile_view_title), onBack)
            val u = p
            if (u == null) {
                if (!loaded) LoadingState() else Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Text(stringResource(R.string.profile_hidden), style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }
            Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                OjasCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Avatar(u.photoUrl, 88)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(u.displayName, style = MaterialTheme.typography.headlineSmall)
                            com.ojashealing.family.ui.components.RoleTick(u.role, 22.dp)
                        }
                        val age = ageFromBirthMonth(u.birthMonth, LocalDateLike.today())
                        val place = listOfNotNull(
                            u.village.takeIf { it.isNotEmpty() },
                            u.city.takeIf { it.isNotEmpty() },
                            locations.states(u.country, "").firstOrNull { it.code == u.state }?.name,
                            locations.country(u.country)?.name
                        ).joinToString(", ")
                        Text(listOfNotNull(age?.let { stringResource(R.string.profile_age, it) }, place.ifEmpty { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.ojashealing.family.ui.components.ProfileBadges(u.role, u.userLevel, u.memberLevel)
                        if (u.about.isNotBlank()) Text(u.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.profile_points) + ": " + u.mokshaPoints, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                val achieved = goals.filter { it.id in u.achievedGoals }
                if (achieved.isNotEmpty()) OjasCard {
                    Text(stringResource(R.string.profile_achieved), style = MaterialTheme.typography.titleLarge)
                    val lang = goalLanguage()
                    achieved.forEach { g ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(g.titleIn(lang), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String, size: Int) {
    Row(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        if (url.isNotEmpty()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        else Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Admin: members, roles and access granted outside the app ----

@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModel(
    private val profiles: PublicProfileRepository,
    private val paid: PaidRepository
) : ViewModel() {
    private val _rows = MutableStateFlow<List<MemberRow>?>(null)
    val rows: StateFlow<List<MemberRow>?> = _rows
    private val _selected = MutableStateFlow<MemberRow?>(null)
    val selected: StateFlow<MemberRow?> = _selected
    val entitlements: StateFlow<Entitlements> = _selected.flatMapLatest { m -> m?.let { paid.observe(it.uid) } ?: flowOf(Entitlements()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Entitlements())
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message

    init { search("") }

    fun search(prefix: String) {
        viewModelScope.launch { _rows.value = profiles.searchMembers(prefix).getOrElse { emptyList() } }
    }

    fun select(row: MemberRow?) {
        _selected.value = row
        _message.value = null
    }

    fun grant(access: PaidAccess, days: Int, credits: Int?, revoke: Boolean) {
        val m = _selected.value ?: return
        viewModelScope.launch {
            _message.value = if (paid.adminGrant(m.uid, access, days, credits, revoke).isSuccess) R.string.members_done else R.string.error_generic
        }
    }

    fun setRole(role: Role) {
        val m = _selected.value ?: return
        viewModelScope.launch {
            paid.setRole(m.uid, role.toId())
                .onSuccess { _selected.value = m.copy(role = role); _message.value = R.string.members_done }
                .onFailure { _message.value = R.string.error_generic }
        }
    }
}

@Composable
fun MembersScreen(vm: MembersViewModel, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val s = selected
            if (s != null) {
                BackBar(stringResource(R.string.members_grant_title, s.name), onBack = { vm.select(null) })
                GrantPanel(vm, s)
                return@Column
            }
            BackBar(stringResource(R.string.members_title), onBack)
            OutlinedTextField(
                value = query, onValueChange = { query = it; vm.search(it) }, singleLine = true,
                label = { Text(stringResource(R.string.members_search)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l)
            )
            val list = rows
            when {
                list == null -> LoadingState()
                list.isEmpty() -> Text(stringResource(R.string.members_empty), modifier = Modifier.padding(Spacing.l), style = MaterialTheme.typography.bodyLarge)
                else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(list, key = { it.uid }, contentType = { "member" }) { m ->
                        OjasCard(onClick = { vm.select(m) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(m.photoUrl, 40)
                                Column(Modifier.weight(1f)) {
                                    Text(m.name.ifEmpty { m.uid.take(8) }, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        listOf(stringResource(m.role.label()), stringResource(R.string.profile_level, m.userLevel),
                                            stringResource(R.string.profile_member_level, m.memberLevel), "${m.points}").joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Role.label() = when (this) {
    Role.Member -> R.string.role_member
    Role.SeniorMember -> R.string.role_senior_member
    Role.Swamiji -> R.string.role_swamiji
    Role.Admin -> R.string.role_admin
}

@Composable
private fun GrantPanel(vm: MembersViewModel, member: MemberRow) {
    val ents by vm.entitlements.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var access by rememberSaveable { mutableStateOf(PaidAccess.Membership) }
    var days by rememberSaveable { mutableStateOf("30") }
    var credits by rememberSaveable { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.members_role), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Role.entries.forEach { r -> FilterChip(selected = member.role == r, onClick = { vm.setRole(r) }, label = { Text(stringResource(r.label())) }) }
        }
        Text(stringResource(R.string.members_grant), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (a in PaidAccess.entries) {
                val label = when (a) { PaidAccess.Membership -> R.string.access_membership; PaidAccess.Chat -> R.string.access_chat; PaidAccess.Call -> R.string.access_call }
                FilterChip(selected = access == a, onClick = { access = a }, label = { Text(stringResource(label)) })
            }
        }
        ents[access]?.takeIf { it.isActive() }?.let { e ->
            Text(
                listOfNotNull(stringResource(R.string.plan_active, formatDate(e.expiresAt)), e.credits?.let { stringResource(R.string.ask_calls_left, it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.members_days)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (access == PaidAccess.Call) {
            OutlinedTextField(value = credits, onValueChange = { credits = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.members_credits)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { vm.grant(access, days.toIntOrNull() ?: 0, credits.toIntOrNull(), revoke = false) }, enabled = (days.toIntOrNull() ?: 0) in 1..366,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.members_grant)) }
        OutlinedButton(onClick = { vm.grant(access, 1, null, revoke = true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.members_revoke)) }
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
    }
}
