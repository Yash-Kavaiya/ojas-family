package com.ojashealing.family.ui.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ojashealing.family.R
import kotlinx.coroutines.flow.distinctUntilChanged

/** Loads the next page once the last rows are on screen. Restarts only when the list or cursor changes, so an empty page cannot spin. */
@Composable
fun LoadOlderWhenEnd(state: LazyListState, hasMore: Boolean, itemCount: Int, onLoad: () -> Unit) {
    LaunchedEffect(state, hasMore, itemCount) {
        if (!hasMore || itemCount == 0) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            last >= info.totalItemsCount - 2
        }.distinctUntilChanged().collect { near ->
            if (near) onLoad()
        }
    }
}

fun LazyListScope.loadOlderRow(hasMore: Boolean, onLoad: () -> Unit) {
    if (!hasMore) return
    item(key = "older", contentType = "older") {
        TextButton(onClick = onLoad, modifier = Modifier.fillMaxWidth().testTag("load_older")) {
            Text(stringResource(R.string.feed_older))
        }
    }
}
