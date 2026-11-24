package com.maslak.gps.ui

import androidx.compose.ui.graphics.Color

/** ألوان واسم نسخة Marwani: خلفية فحمية + أبيض وبرتقالي */
object Palette {
    const val NAME = "Marwani"

    val Bg = Color(0xFF1A1A1A)
    val Header = Color(0xFF242424)
    val Card = Color(0xFF2B2B2B)
    val CardBorder = Color(0xFF3D3D3D)
    val TextDim = Color(0xFFB3B3B3)

    // الألوان المميزة: درجات البرتقالي والأبيض فقط
    val Green = Color(0xFFFF8A1F)   // اللون الأساسي (المفتاح، الشعار)
    val Orange = Color(0xFFFFA94D)
    val Red = Color(0xFFFF5A36)     // يبقى للإيقاف
    val Teal = Color(0xFFFFC58A)
    val Purple = Color(0xFFFF8A1F)
    val Blue = Color(0xFFFFFFFF)
    val Gray = Color(0xFFD9D9D9)

    /** صبغة فحمية محايدة للخريطة مع لمسة دافئة خفيفة */
    val MapTint = floatArrayOf(
        1.08f, 0f, 0f, 0f, 12f,
        0f, 1.04f, 0f, 0f, 9f,
        0f, 0f, 1.0f, 0f, 6f,
        0f, 0f, 0f, 1f, 0f,
    )
}
