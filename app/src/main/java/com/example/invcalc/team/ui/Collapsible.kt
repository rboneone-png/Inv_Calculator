package com.example.invcalc.team.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 可展開／收合的區塊，預設收合。level 0 為外層卡片，level 1 為卡片內的子區塊。
 * 內容只在展開時才組合（收合時圖表等重的內容不會被建立）。
 */
@Composable
fun Collapsible(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    key: String = title,
    level: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val inner: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = if (level == 0) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (level == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
            }
            Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
    if (level == 0) {
        Card(modifier.fillMaxWidth()) { Column(content = inner) }
    } else {
        Card(
            modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) { Column(content = inner) }
    }
}
