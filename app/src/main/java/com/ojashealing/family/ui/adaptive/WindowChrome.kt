package com.ojashealing.family.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration

/**
 * How the five tabs and a list-detail pair lay out for a window width.
 * Compact phones keep a bottom bar and replace the list with the selected item.
 * Medium widths switch to a rail but stay stacked. Expanded widths keep the rail
 * and show the list beside the detail.
 */
enum class PrimaryNav { BottomBar, Rail }

enum class DetailMode { Stacked, BesideList }

data class WindowChrome(val primaryNav: PrimaryNav, val detail: DetailMode)

/** Material width buckets: compact below 600dp, expanded from 840dp. */
const val COMPACT_WIDTH_DP = 600
const val EXPANDED_WIDTH_DP = 840

fun decideWindowChrome(widthDp: Int): WindowChrome = when {
    widthDp < COMPACT_WIDTH_DP -> WindowChrome(PrimaryNav.BottomBar, DetailMode.Stacked)
    widthDp >= EXPANDED_WIDTH_DP -> WindowChrome(PrimaryNav.Rail, DetailMode.BesideList)
    else -> WindowChrome(PrimaryNav.Rail, DetailMode.Stacked)
}

@Composable
fun rememberWindowChrome(): WindowChrome {
    val width = LocalConfiguration.current.screenWidthDp
    return remember(width) { decideWindowChrome(width) }
}

/** One inset owner. Embedded panes sit under the shell bars, so only the IME is applied here. */
@Composable
fun Modifier.ownedInsets(embedded: Boolean): Modifier =
    if (embedded) imePadding() else windowInsetsPadding(WindowInsets.safeDrawing)
