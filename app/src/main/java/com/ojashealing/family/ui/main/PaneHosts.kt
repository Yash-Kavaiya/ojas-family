package com.ojashealing.family.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.ojashealing.family.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
sealed interface CommunityKey : NavKey {
    @Serializable
    data object List : CommunityKey

    @Serializable
    data class Detail(val kind: String, val id: String) : CommunityKey
}

@Serializable
sealed interface AskKey : NavKey {
    @Serializable
    data object Hub : AskKey

    @Serializable
    data object Inbox : AskKey

    @Serializable
    data class Chat(val memberUid: String, val channel: String) : AskKey
}

private fun navConfig(module: SerializersModule) = SavedStateConfiguration { serializersModule = module }

private fun openDetail(backStack: MutableList<NavKey>, detail: NavKey) {
    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    backStack.add(detail)
}

private fun replaceRoot(backStack: MutableList<NavKey>, root: NavKey) {
    backStack.clear()
    backStack.add(root)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun paneStrategy(beside: Boolean): ListDetailSceneStrategy<NavKey> {
    val info = currentWindowAdaptiveInfoV2()
    val directive = remember(info, beside) {
        calculatePaneScaffoldDirective(info).copy(
            maxHorizontalPartitions = if (beside) 2 else 1,
            horizontalPartitionSpacerSize = 0.dp,
        )
    }
    return rememberListDetailSceneStrategy(directive = directive)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CommunityPanes(
    beside: Boolean,
    jump: String?,
    onJumpConsumed: () -> Unit,
    list: @Composable (onOpen: (String, String) -> Unit) -> Unit,
    detail: @Composable (kind: String, id: String, onBack: () -> Unit, showBack: Boolean) -> Unit,
) {
    val config = remember {
        navConfig(SerializersModule {
            polymorphic(NavKey::class) {
                subclass(CommunityKey.List::class, CommunityKey.List.serializer())
                subclass(CommunityKey.Detail::class, CommunityKey.Detail.serializer())
            }
        })
    }
    val backStack = rememberNavBackStack(config, CommunityKey.List)
    val showBack = !beside
    LaunchedEffect(jump) {
        val raw = jump ?: return@LaunchedEffect
        val parts = raw.split("/", limit = 2)
        val id = parts.getOrElse(1) { "" }
        if (id.isNotEmpty()) openDetail(backStack, CommunityKey.Detail(parts[0], id))
        onJumpConsumed()
    }
    val strategy = paneStrategy(beside)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        sceneStrategies = listOf(strategy),
        entryProvider = entryProvider {
            entry<CommunityKey.List>(
                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                    Text(stringResource(R.string.list_pick))
                })
            ) {
                list { kind, id -> openDetail(backStack, CommunityKey.Detail(kind, id)) }
            }
            entry<CommunityKey.Detail>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                detail(key.kind, key.id, { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }, showBack)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AskPanes(
    beside: Boolean,
    jump: String?,
    onJumpConsumed: () -> Unit,
    hub: @Composable (openChat: (String) -> Unit, openInbox: () -> Unit) -> Unit,
    inbox: @Composable (onOpen: (String, String) -> Unit, showBack: Boolean, onBack: () -> Unit) -> Unit,
    chat: @Composable (memberUid: String, channel: String, onBack: () -> Unit, showBack: Boolean) -> Unit,
) {
    val config = remember {
        navConfig(SerializersModule {
            polymorphic(NavKey::class) {
                subclass(AskKey.Hub::class, AskKey.Hub.serializer())
                subclass(AskKey.Inbox::class, AskKey.Inbox.serializer())
                subclass(AskKey.Chat::class, AskKey.Chat.serializer())
            }
        })
    }
    val backStack = rememberNavBackStack(config, AskKey.Hub)
    val showBack = !beside
    val inboxRoot = backStack.size == 1 && backStack.last() is AskKey.Inbox
    BackHandler(enabled = inboxRoot) { replaceRoot(backStack, AskKey.Hub) }
    LaunchedEffect(jump) {
        val raw = jump ?: return@LaunchedEffect
        when {
            raw == "inbox" -> replaceRoot(backStack, AskKey.Inbox)
            raw.startsWith("chat:") -> {
                val parts = raw.split(":")
                replaceRoot(backStack, AskKey.Hub)
                backStack.add(AskKey.Chat(parts.getOrElse(2) { "" }, parts.getOrElse(1) { "swamiji" }))
            }
            raw.startsWith("inbox:") -> {
                val parts = raw.split(":")
                val uid = parts.getOrElse(2) { "" }
                replaceRoot(backStack, AskKey.Inbox)
                if (uid.isNotEmpty()) backStack.add(AskKey.Chat(uid, parts.getOrElse(1) { "swamiji" }))
            }
        }
        onJumpConsumed()
    }
    val strategy = paneStrategy(beside)
    NavDisplay(
        backStack = backStack,
        onBack = {
            when {
                backStack.size > 1 -> backStack.removeAt(backStack.lastIndex)
                backStack.lastOrNull() is AskKey.Inbox -> replaceRoot(backStack, AskKey.Hub)
            }
        },
        sceneStrategies = listOf(strategy),
        entryProvider = entryProvider {
            entry<AskKey.Hub>(
                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                    Text(stringResource(R.string.list_pick))
                })
            ) {
                hub(
                    { channel -> openDetail(backStack, AskKey.Chat("", channel)) },
                    { replaceRoot(backStack, AskKey.Inbox) },
                )
            }
            entry<AskKey.Inbox>(
                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                    Text(stringResource(R.string.list_pick))
                })
            ) {
                inbox(
                    { uid, channel -> openDetail(backStack, AskKey.Chat(uid, channel)) },
                    showBack,
                    { replaceRoot(backStack, AskKey.Hub) },
                )
            }
            entry<AskKey.Chat>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                chat(key.memberUid, key.channel, { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }, showBack)
            }
        }
    )
}
