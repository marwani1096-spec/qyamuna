package com.maslak.gps.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * يحمّل الخط من مجلد assets/fonts إن وُجد (مثل خط ثمانية بعد تنزيله من موقعه الرسمي).
 * إن لم يوجد ملف خط يُستخدم خط النظام.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberBrandFont(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            val files = ctx.assets.list("fonts").orEmpty()
                .filter { it.endsWith(".otf", true) || it.endsWith(".ttf", true) }
            if (files.isEmpty()) FontFamily.Default
            else FontFamily(files.map { f ->
                val n = f.lowercase()
                val w = when {
                    "black" in n || "heavy" in n -> FontWeight.Black
                    "extrabold" in n -> FontWeight.ExtraBold
                    "semibold" in n -> FontWeight.SemiBold
                    "bold" in n -> FontWeight.Bold
                    "medium" in n -> FontWeight.Medium
                    "light" in n -> FontWeight.Light
                    else -> FontWeight.Normal
                }
                Font("fonts/$f", ctx.assets, w)
            })
        }.getOrDefault(FontFamily.Default)
    }
}

private fun TextStyle.withFont(f: FontFamily) = copy(fontFamily = f)

@Composable
fun MaslakTheme(content: @Composable () -> Unit) {
    val f = rememberBrandFont()
    val t = Typography()
    MaterialTheme(
        typography = Typography(
            displayLarge = t.displayLarge.withFont(f), displayMedium = t.displayMedium.withFont(f),
            displaySmall = t.displaySmall.withFont(f), headlineLarge = t.headlineLarge.withFont(f),
            headlineMedium = t.headlineMedium.withFont(f), headlineSmall = t.headlineSmall.withFont(f),
            titleLarge = t.titleLarge.withFont(f), titleMedium = t.titleMedium.withFont(f),
            titleSmall = t.titleSmall.withFont(f), bodyLarge = t.bodyLarge.withFont(f),
            bodyMedium = t.bodyMedium.withFont(f), bodySmall = t.bodySmall.withFont(f),
            labelLarge = t.labelLarge.withFont(f), labelMedium = t.labelMedium.withFont(f),
            labelSmall = t.labelSmall.withFont(f),
        ),
        colorScheme = darkColorScheme(
            primary = Palette.Green,
            secondary = Palette.Blue,
            background = Palette.Bg,
            surface = Palette.Card,
            surfaceVariant = Palette.Card,
            onSurface = Color.White,
            onBackground = Color.White,
        ),
        content = content,
    )
}
