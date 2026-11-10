package com.ojashealing.family.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The locale the screen is drawn in. Unlike Locale.getDefault(), reading it here redraws the text
 * when the language changes.
 */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
