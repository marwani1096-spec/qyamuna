package com.maslak.gps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.maslak.gps.data.LatLng
import com.maslak.gps.data.LocationParser
import com.maslak.gps.data.MockMode
import com.maslak.gps.data.MockRequest
import com.maslak.gps.data.MockSettings
import com.maslak.gps.data.Prefs
import com.maslak.gps.data.SavedPlace
import com.maslak.gps.data.Schedule
import com.maslak.gps.map.CameraRequest
import com.maslak.gps.mock.MockLocationService
import com.maslak.gps.mock.MockState
import com.maslak.gps.mock.RunPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val prefs = Prefs(app)

    var pin by mutableStateOf(prefs.lastPin ?: LatLng(24.713552, 46.675297)); private set
    var pinLabel by mutableStateOf<String?>(null); private set
    var satellite by mutableStateOf(false)
    var mode by mutableStateOf(MockMode.FIXED); private set
    val routePoints = mutableStateListOf<LatLng>()
    var routeLoop by mutableStateOf(true)
    var saved by mutableStateOf(prefs.loadSaved()); private set
    var settings by mutableStateOf(prefs.loadSettings()); private set
    var schedule by mutableStateOf(Schedule()); private set
    var searching by mutableStateOf(false); private set
    var mockAllowed by mutableStateOf(false); private set
    var hasLocationPermission by mutableStateOf(false); private set
    var camera by mutableStateOf(CameraRequest(pin, 0, 17.0)); private set
    var showHelp by mutableStateOf(false)

    val phase: StateFlow<RunPhase> = MockState.phase
    val current: StateFlow<LatLng?> = MockState.current
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val history = ArrayDeque<LatLng>()
    private val isRunning get() = MockState.phase.value != RunPhase.IDLE

    init {
        MockState.settings = settings
        viewModelScope.launch { MockState.errors.collect { messages.emit(it) } }
        refreshStatus()
    }

    private fun msg(text: String) { messages.tryEmit(text) }

    // ───────────── الحالة والأذونات ─────────────

    fun refreshStatus() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        mockAllowed = isMockAppSelected()
    }

    private fun isMockAppSelected(): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    // ───────────── الخريطة ─────────────

    private fun moveCamera(p: LatLng, zoom: Double? = null) {
        camera = CameraRequest(p, camera.id + 1, zoom)
    }

    private fun setPinInternal(p: LatLng, label: String? = null, recenter: Boolean = false) {
        if (p != pin) {
            history.addLast(pin)
            while (history.size > 30) history.removeFirst()
        }
        pin = p
        pinLabel = label
        prefs.lastPin = p
        if (recenter) moveCamera(p, 17.0)
        // انتقال فوري إن كان الموقع الوهمي يعمل في الوضع الثابت أو العشوائي
        if (isRunning && mode != MockMode.ROUTE) startMock(live = true)
    }

    fun onMapTap(p: LatLng) {
        if (mode == MockMode.ROUTE && !isRunning) {
            routePoints.add(p)
        } else {
            setPinInternal(p)
        }
    }

    fun search(query: String) {
        if (searching) return
        searching = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { LocationParser.resolve(ctx, query) }
            searching = false
            when (r) {
                is LocationParser.Result.Found -> {
                    setPinInternal(r.point, r.label, recenter = true)
                    msg("تم تحديد الموقع: ${r.point.format()}")
                }
                is LocationParser.Result.NotFound -> msg(r.reason)
            }
        }
    }

    fun restore() {
        val prev = history.removeLastOrNull()
        if (prev == null) { msg("لا يوجد موقع سابق"); return }
        pin = prev
        pinLabel = null
        prefs.lastPin = prev
        moveCamera(prev)
        if (isRunning && mode != MockMode.ROUTE) startMock(live = true)
        msg("تمت استعادة الموقع السابق")
    }

    @SuppressLint("MissingPermission")
    fun goToMyLocation() {
        if (!hasLocationPermission) { msg("امنح إذن الموقع أولاً"); return }
        if (isRunning) msg("تنبيه: الموقع الوهمي يعمل، سيظهر الموقع الوهمي")
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) moveCamera(LatLng(loc.latitude, loc.longitude), 17.0)
                else msg("تعذر تحديد موقعك الحالي، فعّل GPS")
            }
            .addOnFailureListener { msg("تعذر تحديد موقعك الحالي") }
    }

    // ───────────── المحفوظات ─────────────

    fun savePlace(name: String) {
        val n = name.trim().ifEmpty { pinLabel ?: pin.format() }
        saved = saved + SavedPlace(n, pin)
        prefs.storeSaved(saved)
        msg("تم الحفظ: $n")
    }

    fun deletePlace(p: SavedPlace) {
        saved = saved - p
        prefs.storeSaved(saved)
    }

    fun goToPlace(p: SavedPlace) = setPinInternal(p.point, p.name, recenter = true)

    // ───────────── الأوضاع ─────────────

    fun toggleRouteMode() {
        mode = if (mode == MockMode.ROUTE) MockMode.FIXED else MockMode.ROUTE
        if (mode == MockMode.ROUTE && routePoints.isEmpty()) {
            msg("اضغط على الخريطة لإضافة نقاط المسار ثم فعّل تغيير الموقع")
        }
        if (isRunning) startMock(live = true)
    }

    fun toggleRandomMode() {
        mode = if (mode == MockMode.RANDOM) MockMode.FIXED else MockMode.RANDOM
        if (mode == MockMode.RANDOM) msg("حركة عشوائية ضمن ${settings.randomRadius.toInt()} م حول الدبوس")
        if (isRunning) startMock(live = true)
    }

    fun undoRoutePoint() { routePoints.removeLastOrNull() }
    fun clearRoute() { routePoints.clear() }

    fun updateSchedule(s: Schedule) { schedule = s }

    fun updateSettings(s: MockSettings) {
        settings = s
        MockState.settings = s
        prefs.storeSettings(s)
    }

    // ───────────── التشغيل ─────────────

    /** يرجع false إن كان هناك ما يمنع التشغيل */
    fun startMock(live: Boolean = false): Boolean {
        refreshStatus()
        if (!hasLocationPermission) { msg("امنح إذن الموقع للتطبيق أولاً"); return false }
        if (!mockAllowed) {
            msg("اختر هذا التطبيق كـ \"تطبيق الموقع الوهمي\" من خيارات المطور")
            showHelp = true
            return false
        }
        if (mode == MockMode.ROUTE && routePoints.size < 2) {
            msg("أضف نقطتين على الأقل للمسار")
            return false
        }
        val req = MockRequest(
            mode = mode,
            center = if (mode == MockMode.ROUTE) routePoints.first() else pin,
            route = routePoints.toList(),
            loop = routeLoop,
            startDelayMs = if (live) 0L else schedule.startDelayMin * 60_000L,
            durationMs = schedule.durationMin * 60_000L,
        )
        MockLocationService.start(ctx, req)
        return true
    }

    fun stopMock() = MockLocationService.stop(ctx)

    fun stopAll() {
        stopMock()
        mode = MockMode.FIXED
        routePoints.clear()
        schedule = Schedule()
        msg("تم إيقاف كل شيء وإرجاع الموقع الحقيقي")
    }
}
