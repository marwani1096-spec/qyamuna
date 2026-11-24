package com.maslak.gps.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("maslak", Context.MODE_PRIVATE)

    var lastPin: LatLng?
        get() {
            if (!sp.contains("pin_lat")) return null
            return LatLng(
                java.lang.Double.longBitsToDouble(sp.getLong("pin_lat", 0)),
                java.lang.Double.longBitsToDouble(sp.getLong("pin_lng", 0)),
            )
        }
        set(v) {
            if (v == null) return
            sp.edit()
                .putLong("pin_lat", java.lang.Double.doubleToRawLongBits(v.lat))
                .putLong("pin_lng", java.lang.Double.doubleToRawLongBits(v.lng))
                .apply()
        }

    fun loadSaved(): List<SavedPlace> = runCatching {
        val arr = JSONArray(sp.getString("saved", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedPlace(o.getString("name"), LatLng(o.getDouble("lat"), o.getDouble("lng")))
        }
    }.getOrDefault(emptyList())

    fun storeSaved(list: List<SavedPlace>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("lat", it.point.lat).put("lng", it.point.lng))
        }
        sp.edit().putString("saved", arr.toString()).apply()
    }

    fun loadSettings() = MockSettings(
        accuracy = sp.getFloat("accuracy", 3f),
        intervalMs = sp.getLong("interval", 500L),
        altitude = sp.getFloat("altitude", 20f).toDouble(),
        speedKmh = sp.getFloat("speed", 5f),
        randomRadius = sp.getFloat("radius", 60f),
        naturalDrift = sp.getBoolean("drift", false),
    )

    fun storeSettings(s: MockSettings) {
        sp.edit()
            .putFloat("accuracy", s.accuracy)
            .putLong("interval", s.intervalMs)
            .putFloat("altitude", s.altitude.toFloat())
            .putFloat("speed", s.speedKmh)
            .putFloat("radius", s.randomRadius)
            .putBoolean("drift", s.naturalDrift)
            .apply()
    }
}
