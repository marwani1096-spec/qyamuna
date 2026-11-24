package com.maslak.gps.data

import kotlin.math.*

object Geo {
    private const val R = 6_371_008.8 // نصف قطر الأرض بالمتر

    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(h)))
    }

    /** الاتجاه من a إلى b بالدرجات (0..360) */
    fun bearing(a: LatLng, b: LatLng): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lng - a.lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** نقطة على مسافة meters واتجاه bearingDeg من start */
    fun destination(start: LatLng, bearingDeg: Double, meters: Double): LatLng {
        val d = meters / R
        val br = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(start.lat)
        val l1 = Math.toRadians(start.lng)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(br))
        val l2 = l1 + atan2(sin(br) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return LatLng(Math.toDegrees(p2), (Math.toDegrees(l2) + 540) % 360 - 180)
    }
}
