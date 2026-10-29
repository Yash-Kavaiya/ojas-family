package com.ojashealing.family.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing

// Each language is named in its own script so a first-time user can find theirs before anything is translated
private val languages = listOf(
    Triple("hi", "हिन्दी", "Hindi"),
    Triple("gu", "ગુજરાતી", "Gujarati"),
    Triple("en", "English", "English")
)

@Composable
fun LanguageScreen(onChoose: (String) -> Unit) {
    OnboardingLayout(
        title = stringResource(R.string.onboarding_choose_language),
        subtitle = stringResource(R.string.onboarding_language_subtitle)
    ) {
        languages.forEach { (tag, native, english) ->
            OjasCard(onClick = { onChoose(tag) }, modifier = Modifier.testTag("language_$tag")) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(native, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        if (native != english) {
                            Text(english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
