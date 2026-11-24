package com.maslak.gps.data

import android.content.Context
import android.location.Geocoder
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * يحوّل مدخلات البحث إلى إحداثيات:
 *  - إحداثيات مباشرة: "24.7136, 46.6753" (يدعم الأرقام العربية والفاصلة العربية)
 *  - روابط Google Maps بكل أشكالها (بما فيها الروابط المختصرة maps.app.goo.gl)
 *  - روابط geo:
 *  - اسم مكان (Geocoder ثم OpenStreetMap Nominatim كاحتياط)
 */
object LocationParser {

    private const val NUM = "(-?\\d{1,3}(?:\\.\\d+)?)"
    private val pairRegex = Regex("^\\s*$NUM\\s*[,،;\\s]\\s*$NUM\\s*$")

    // مرتبة حسب الدقة: !3d!4d هو موقع الدبوس الفعلي، أما @ فهو مركز الشاشة
    private val urlPatterns = listOf(
        Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)"),
        Regex("[?&](?:q|query|ll|sll|center|destination|daddr|saddr)=(?:loc:)?(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)"),
        Regex("/place/(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)"),
        Regex("/search/(-?\\d+\\.\\d+)\\s*,\\s*\\+?(-?\\d+\\.\\d+)"),
        Regex("/dir/[^@]*?/(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)"),
        Regex("geo:(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)"),
        Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
    )

    sealed class Result {
        data class Found(val point: LatLng, val label: String?) : Result()
        data class NotFound(val reason: String) : Result()
    }

    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun valid(lat: Double, lng: Double) = lat in -90.0..90.0 && lng in -180.0..180.0

    fun parseCoordinates(text: String): LatLng? {
        val m = pairRegex.find(normalizeDigits(text)) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (valid(lat, lng)) LatLng(lat, lng) else null
    }

    fun parseUrl(url: String): LatLng? {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        for (p in urlPatterns) {
            val m = p.find(decoded) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lng = m.groupValues[2].toDoubleOrNull() ?: continue
            if (valid(lat, lng)) return LatLng(lat, lng)
        }
        return null
    }

    /** يستخرج اسم المكان من الرابط إن لم توجد إحداثيات */
    private fun placeNameFromUrl(url: String): String? {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        Regex("[?&]q=([^&]+)").find(decoded)?.let { return it.groupValues[1].replace('+', ' ') }
        Regex("/place/([^/@]+)").find(decoded)?.let { return it.groupValues[1].replace('+', ' ') }
        return null
    }

    private fun extractUrl(text: String): String? =
        Regex("(https?://\\S+|geo:\\S+)").find(text)?.value

    /** يتتبع إعادة التوجيه في الروابط المختصرة */
    private fun expandUrl(url: String): String {
        var current = url
        repeat(8) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile")
            }
            try {
                val code = c.responseCode
                val loc = c.getHeaderField("Location")
                if (code in 300..399 && loc != null) {
                    current = URL(URL(current), loc).toString()
                    if (parseUrl(current) != null) return current
                } else {
                    if (parseUrl(current) == null && code == 200) {
                        // بعض الصفحات تضع الإحداثيات داخل المحتوى فقط
                        val body = c.inputStream.bufferedReader().use { r -> r.readText().take(400_000) }
                        Regex("(?:center|ll)=(-?\\d+\\.\\d+)(?:%2C|,)(-?\\d+\\.\\d+)").find(body)?.let { m ->
                            return "geo:${m.groupValues[1]},${m.groupValues[2]}"
                        }
                    }
                    return current
                }
            } finally {
                c.disconnect()
            }
        }
        return current
    }

    @Suppress("DEPRECATION")
    private fun geocode(context: Context, query: String): Result {
        if (Geocoder.isPresent()) {
            runCatching {
                val list = Geocoder(context, Locale("ar")).getFromLocationName(query, 1)
                val a = list?.firstOrNull()
                if (a != null) {
                    return Result.Found(LatLng(a.latitude, a.longitude), a.getAddressLine(0) ?: query)
                }
            }
        }
        // احتياط: OpenStreetMap Nominatim
        return runCatching {
            val u = URL(
                "https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=ar&q=" +
                    URLEncoder.encode(query, "UTF-8")
            )
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "MaslakGPS-Android/1.0")
            }
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val arr = JSONArray(txt)
            if (arr.length() == 0) Result.NotFound("لم يتم العثور على المكان")
            else {
                val o = arr.getJSONObject(0)
                Result.Found(LatLng(o.getString("lat").toDouble(), o.getString("lon").toDouble()), o.optString("display_name"))
            }
        }.getOrElse { Result.NotFound("تعذر البحث، تحقق من الاتصال بالإنترنت") }
    }

    /** يجب استدعاؤها من خيط خلفي (IO) */
    fun resolve(context: Context, input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.NotFound("اكتب مكاناً أو إحداثيات أو رابطاً")

        parseCoordinates(text)?.let { return Result.Found(it, null) }

        val url = extractUrl(text)
        if (url != null) {
            parseUrl(url)?.let { return Result.Found(it, null) }
            val expanded = runCatching { expandUrl(url) }.getOrNull()
            if (expanded != null) {
                parseUrl(expanded)?.let { return Result.Found(it, null) }
                placeNameFromUrl(expanded)?.let { return geocode(context, it) }
            }
            return Result.NotFound("تعذر قراءة الإحداثيات من الرابط")
        }
        return geocode(context, text)
    }
}
