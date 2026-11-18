package com.ojashealing.family.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A tab's icon and name side by side (so the row stays short), with a red count of what is new. */
@Composable
fun TabLabel(icon: ImageVector, label: String, count: Int = 0) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f, fill = false)) { FitText(label, MaterialTheme.typography.labelLarge) }
        if (count > 0) Badge(containerColor = Color(0xFFE11D48), contentColor = Color.White) {
            Text(if (count > 99) "99+" else "$count")
        }
    }
}
