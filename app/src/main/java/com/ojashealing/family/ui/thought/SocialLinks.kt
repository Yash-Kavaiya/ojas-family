package com.ojashealing.family.ui.thought

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojashealing.family.R
import com.ojashealing.family.data.about.AboutLink
import com.ojashealing.family.data.about.OJAS_YOUTUBE
import com.ojashealing.family.ui.theme.Spacing

/** The places Ojas Healing can be followed, in the order the icons are shown. */
enum class Social(val label: String, val color: Color, val hosts: List<String>) {
    WhatsApp("WhatsApp", Color(0xFF25D366), listOf("wa.me", "whatsapp.com")),
    YouTube("YouTube", Color(0xFFFF0000), listOf("youtube.com", "youtu.be")),
    Facebook("Facebook", Color(0xFF1877F2), listOf("facebook.com", "fb.com", "fb.me")),
    Instagram("Instagram", Color(0xFFE1306C), listOf("instagram.com")),
    Threads("Threads", Color(0xFF101010), listOf("threads.net", "threads.com")),
    Telegram("Telegram", Color(0xFF26A5E4), listOf("t.me", "telegram.me", "telegram.org")),
    Reddit("Reddit", Color(0xFFFF4500), listOf("reddit.com")),
    Quora("Quora", Color(0xFFB92B27), listOf("quora.com")),
}

/** Which platform a link belongs to, from its web address; null for any other link. */
fun socialOf(url: String): Social? {
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase().removePrefix("www.").removePrefix("m.")
    return Social.entries.firstOrNull { s -> s.hosts.any { host == it || host.endsWith(".$it") } }
}

/**
 * One link per platform, in the fixed order, from the links admins set in About (the first link of each
 * platform wins). YouTube is always there: the channel is known without any setting.
 */
fun socialLinks(links: List<AboutLink>): List<Pair<Social, String>> {
    val found = LinkedHashMap<Social, String>()
    for (l in links) socialOf(l.url)?.let { found.putIfAbsent(it, l.url) }
    found.putIfAbsent(Social.YouTube, OJAS_YOUTUBE)
    return Social.entries.mapNotNull { s -> found[s]?.let { s to it } }
}

/** A row of round icons at the bottom of Home, like the links at the bottom of a WhatsApp profile; each opens its app or page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialRow(links: List<AboutLink>, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val shown = socialLinks(links)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.social_follow), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            shown.forEach { (social, url) ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape).socialBackground(social)
                        .clickable(role = Role.Button, onClickLabel = social.label) { runCatching { uri.openUri(url) } },
                    contentAlignment = Alignment.Center,
                ) { SocialGlyph(social) }
            }
        }
    }
}

private fun Modifier.socialBackground(social: Social): Modifier =
    if (social == Social.Instagram) background(Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))))
    else background(social.color)

private fun materialGlyph(social: Social): ImageVector? = when (social) {
    Social.WhatsApp -> Icons.Filled.Whatsapp
    Social.Facebook -> Icons.Filled.Facebook
    else -> null
}

/** The white mark inside the circle. Platforms without a ready icon are drawn simply: a play button, a camera, a paper plane, letters. */
@Composable
private fun SocialGlyph(social: Social) {
    val icon = materialGlyph(social)
    when {
        icon != null -> Icon(icon, contentDescription = social.label, tint = Color.White, modifier = Modifier.size(28.dp))
        social == Social.Threads -> Text("@", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        social == Social.Reddit -> Text("r/", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        social == Social.Quora -> Text("Q", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        else -> Canvas(Modifier.size(26.dp)) {
            if (social == Social.Telegram) {
                // A paper plane
                val plane = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.48f)
                    lineTo(size.width * 0.92f, size.height * 0.14f)
                    lineTo(size.width * 0.76f, size.height * 0.86f)
                    lineTo(size.width * 0.5f, size.height * 0.64f)
                    lineTo(size.width * 0.38f, size.height * 0.84f)
                    lineTo(size.width * 0.36f, size.height * 0.6f)
                    close()
                }
                drawPath(plane, Color.White)
            } else if (social == Social.YouTube) {
                val play = Path().apply {
                    moveTo(size.width * 0.34f, size.height * 0.22f)
                    lineTo(size.width * 0.82f, size.height * 0.5f)
                    lineTo(size.width * 0.34f, size.height * 0.78f)
                    close()
                }
                drawPath(play, Color.White)
            } else {
                // Instagram: a rounded square, a lens and the small flash dot
                val stroke = Stroke(width = size.width * 0.09f)
                drawRoundRect(Color.White, Offset(size.width * 0.1f, size.height * 0.1f), Size(size.width * 0.8f, size.height * 0.8f), CornerRadius(size.width * 0.24f), style = stroke)
                drawCircle(Color.White, radius = size.width * 0.2f, style = stroke)
                drawCircle(Color.White, radius = size.width * 0.055f, center = Offset(size.width * 0.71f, size.height * 0.29f))
            }
        }
    }
}
