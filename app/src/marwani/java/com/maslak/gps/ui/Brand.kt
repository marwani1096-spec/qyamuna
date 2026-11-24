package com.maslak.gps.ui

import androidx.compose.ui.graphics.Color

/** ألوان واسم نسخة Marwani: خلفية بنية محمّصة + أبيض وبرتقالي */
object Palette {
    const val NAME = "Marwani"

    val Bg = Color(0xFF24160D)
    val Header = Color(0xFF382213)
    val Card = Color(0xFF3A2416)
    val CardBorder = Color(0xFF5E3C25)
    val TextDim = Color(0xFFD8B898)

    // الألوان المميزة: درجات البرتقالي والأبيض فقط
    val Green = Color(0xFFFF8A1F)   // اللون الأساسي (المفتاح، الشعار)
    val Orange = Color(0xFFFFA94D)
    val Red = Color(0xFFFF5A36)     // يبقى للإيقاف
    val Teal = Color(0xFFFFC58A)
    val Purple = Color(0xFFFF8A1F)
    val Blue = Color(0xFFFFFFFF)
    val Gray = Color(0xFFE8D5C4)

    /** صبغة بنية دافئة للخريطة */
    val MapTint = floatArrayOf(
        1.25f, 0f, 0f, 0f, 40f,
        0f, 1.05f, 0f, 0f, 22f,
        0f, 0f, 0.85f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f,
    )
}
