package com.maslak.gps.data

data class LatLng(val lat: Double, val lng: Double) {
    fun format(): String = "%.6f, %.6f".format(java.util.Locale.US, lat, lng)
}

data class SavedPlace(val name: String, val point: LatLng)

enum class MockMode { FIXED, ROUTE, RANDOM }

/** إعدادات التخصيص */
data class MockSettings(
    /** دقة الموقع المُبلّغ عنها بالمتر (كلما قلّت كان الموقع أدق) */
    val accuracy: Float = 3f,
    /** الفاصل بين تحديثات الموقع بالمللي ثانية */
    val intervalMs: Long = 500L,
    /** الارتفاع عن سطح البحر بالمتر */
    val altitude: Double = 20.0,
    /** سرعة الحركة في وضع المسار والعشوائي (كم/س) */
    val speedKmh: Float = 5f,
    /** نصف قطر الحركة العشوائية بالمتر */
    val randomRadius: Float = 60f,
    /** اهتزاز طبيعي خفيف يحاكي GPS الحقيقي */
    val naturalDrift: Boolean = false,
)

/** الجدولة: تأخير البدء ومدة التشغيل (0 = بلا حد) */
data class Schedule(val startDelayMin: Int = 0, val durationMin: Int = 0) {
    val isActive get() = startDelayMin > 0 || durationMin > 0
}

data class MockRequest(
    val mode: MockMode,
    val center: LatLng,
    val route: List<LatLng> = emptyList(),
    val loop: Boolean = true,
    val startDelayMs: Long = 0,
    val durationMs: Long = 0,
)
