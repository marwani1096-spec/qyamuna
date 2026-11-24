package com.maslak.gps.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** زر ملوّن بنمط التصميم: خلفية شفافة بلون مميز + حد رفيع */
@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    selected: Boolean = false,
    textColor: Color = accent,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = if (selected) 0.42f else 0.20f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) 0.9f else 0.35f)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun Chip(text: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Palette.Card,
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** مفتاح تبديل ثنائي (عادي / قمر صناعي) */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Card)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSel) Color.White else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) Color.Black else Color(0xFFCFCFD4),
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
