package com.ojashealing.family.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.OjasApp
import com.ojashealing.family.data.social.LinkPreviewData

/**
 * A card for the first web address in [text]: the page's picture, site, title and a line about it,
 * the way messengers show a shared link. Tapping it opens the page. Nothing shows while it loads,
 * or when the page has nothing to show.
 */
@Composable
fun LinkPreviewFor(text: String, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.surfaceVariant) {
    val url = remember(text) { findLinks(text).firstOrNull()?.url } ?: return
    val app = LocalContext.current.applicationContext as? OjasApp ?: return
    val preview = produceState<LinkPreviewData?>(null, url) { value = app.container.linkPreviews.load(url) }.value ?: return
    LinkPreviewCard(preview, modifier, background)
}

@Composable
fun LinkPreviewCard(p: LinkPreviewData, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.surfaceVariant) {
    val uri = LocalUriHandler.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable { runCatching { uri.openUri(p.url) } }
    ) {
        if (p.image.isNotEmpty()) ShimmerImage(
            p.image, Modifier.fillMaxWidth().aspectRatio(1.91f).heightIn(max = 180.dp), shape = RectangleShape,
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (p.site.isNotEmpty()) Text(
                p.site, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                p.title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (p.description.isNotEmpty()) Text(
                p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
