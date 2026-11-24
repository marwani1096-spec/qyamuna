package com.maslak.gps.ui

import androidx.compose.ui.graphics.Color

/** ألوان واسم نسخة MaslakGPS */
object Palette {
    const val NAME = "MaslakGPS"

    val Bg = Color(0xFF1B1B1F)
    val Header = Color(0xFF26272B)
    val Card = Color(0xFF2A2B30)
    val CardBorder = Color(0xFF3A3B41)
    val TextDim = Color(0xFF9A9AA2)

    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFF5A524)
    val Red = Color(0xFFFF453A)
    val Teal = Color(0xFF30D5C8)
    val Purple = Color(0xFFBF5AF2)
    val Blue = Color(0xFF32ADE6)
    val Gray = Color(0xFF8E8E93)

    /** صبغة كحلية للخريطة */
    val MapTint = floatArrayOf(
        1.05f, 0f, 0f, 0f, 18f,
        0f, 1.10f, 0f, 0f, 26f,
        0f, 0f, 1.25f, 0f, 48f,
        0f, 0f, 0f, 1f, 0f,
    )
}
