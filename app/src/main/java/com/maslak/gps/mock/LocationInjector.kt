package com.maslak.gps.mock

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.maslak.gps.data.Geo
import com.maslak.gps.data.LatLng
import com.maslak.gps.data.MockSettings
import kotlin.random.Random

class MockNotAllowedException : Exception()

/**
 * يحقن الموقع الوهمي في كل مصادر الموقع في النظام:
 * GPS + Network + Fused (النظام) + Fused (خدمات Google Play)
 * حتى تقرأ جميع التطبيقات نفس الموقع بدقة.
 */
class LocationInjector(context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    private val providers = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }
    private val active = mutableListOf<String>()
    private var fusedMock = false

    // اهتزاز طبيعي (إزاحة صغيرة تتغير بنعومة)
    private var driftBearing = Random.nextDouble(0.0, 360.0)
    private var driftMeters = 0.0

    @SuppressLint("MissingPermission")
    fun enable() {
        disable()
        var denied = false
        for (p in providers) {
            try {
                runCatching { lm.removeTestProvider(p) }
                lm.addTestProvider(
                    p,
                    /* requiresNetwork */ false,
                    /* requiresSatellite */ false,
                    /* requiresCell */ false,
                    /* hasMonetaryCost */ false,
                    /* supportsAltitude */ true,
                    /* supportsSpeed */ true,
                    /* supportsBearing */ true,
                    /* powerRequirement = POWER_LOW */ 1,
                    /* accuracy = ACCURACY_FINE */ 1,
                )
                lm.setTestProviderEnabled(p, true)
                active += p
            } catch (e: SecurityException) {
                denied = true
            } catch (_: Exception) {
                // بعض الأجهزة لا تسمح بمصدر معيّن — نتجاهله ونكمل مع البقية
            }
        }
        if (active.isEmpty()) {
            if (denied) throw MockNotAllowedException()
            throw IllegalStateException("تعذر تفعيل مصادر الموقع الوهمي")
        }
        try {
            fused.setMockMode(true)
            fusedMock = true
        } catch (_: Exception) {
            fusedMock = false
        }
    }

    private fun build(provider: String, s: Sample, set: MockSettings): Location {
        var point: LatLng = s.point
        var acc = set.accuracy
        if (set.naturalDrift) {
            driftBearing += Random.nextDouble(-25.0, 25.0)
            driftMeters = (driftMeters + Random.nextDouble(-0.4, 0.4)).coerceIn(0.0, set.accuracy * 0.6)
            point = Geo.destination(point, driftBearing, driftMeters)
            acc = (set.accuracy + Random.nextDouble(-0.6, 1.2).toFloat()).coerceAtLeast(1f)
        }
        return Location(provider).apply {
            latitude = point.lat
            longitude = point.lng
            altitude = set.altitude + if (set.naturalDrift) Random.nextDouble(-0.8, 0.8) else 0.0
            accuracy = acc
            bearing = s.bearing
            speed = s.speed
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = acc * 1.5f
                speedAccuracyMetersPerSecond = 0.3f
                bearingAccuracyDegrees = if (s.speed > 0f) 5f else 30f
            }
            extras = Bundle().apply { putInt("satellites", 14) }
        }
    }

    @SuppressLint("MissingPermission")
    fun push(sample: Sample, settings: MockSettings) {
        for (p in active) {
            try {
                lm.setTestProviderLocation(p, build(p, sample, settings))
            } catch (_: Exception) {
            }
        }
        if (fusedMock) {
            try {
                fused.setMockLocation(build("fused", sample, settings))
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disable() {
        for (p in active) {
            runCatching { lm.setTestProviderEnabled(p, false) }
            runCatching { lm.removeTestProvider(p) }
        }
        active.clear()
        if (fusedMock) {
            runCatching { fused.setMockMode(false) }
            fusedMock = false
        }
    }
}
