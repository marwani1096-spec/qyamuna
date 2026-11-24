package com.maslak.gps.mock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.maslak.gps.MainActivity
import com.maslak.gps.R
import com.maslak.gps.data.MockMode
import com.maslak.gps.data.MockRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "com.maslak.gps.START"
        const val ACTION_STOP = "com.maslak.gps.STOP"
        private const val CHANNEL = "mock_location"
        private const val NOTIF_ID = 7

        fun start(ctx: Context, req: MockRequest) {
            MockState.request = req
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, MockLocationService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(ctx: Context) {
            if (MockState.phase.value == RunPhase.IDLE) return
            ctx.startService(Intent(ctx, MockLocationService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var injector: LocationInjector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val req = MockState.request
        if (req == null) {
            shutdown()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(req, waiting = req.startDelayMs > 0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (e: Exception) {
            MockState.errors.tryEmit("تعذر تشغيل الخدمة: امنح إذن الموقع للتطبيق")
            shutdown()
            return START_NOT_STICKY
        }
        acquireWakeLock()

        job?.cancel()
        job = scope.launch { run(req) }
        return START_NOT_STICKY
    }

    private suspend fun run(req: MockRequest) {
        if (req.startDelayMs > 0) {
            MockState.phase.value = RunPhase.WAITING
            delay(req.startDelayMs)
            notifyUpdate(req)
        }
        val inj = injector ?: LocationInjector(this).also { injector = it }
        try {
            inj.enable()
        } catch (e: MockNotAllowedException) {
            MockState.errors.tryEmit("التطبيق غير مختار كتطبيق موقع وهمي في خيارات المطور")
            shutdown()
            return
        } catch (e: Exception) {
            MockState.errors.tryEmit(e.message ?: "خطأ غير معروف")
            shutdown()
            return
        }
        MockState.phase.value = RunPhase.RUNNING

        val mover = Mover.create(req)
        val endAt = if (req.durationMs > 0) SystemClock.elapsedRealtime() + req.durationMs else Long.MAX_VALUE
        var last = SystemClock.elapsedRealtime()
        val ctx = kotlin.coroutines.coroutineContext
        while (ctx.isActive) {
            val now = SystemClock.elapsedRealtime()
            val dt = (now - last) / 1000.0
            last = now
            val settings = MockState.settings
            val sample = mover.step(dt, settings)
            inj.push(sample, settings)
            MockState.current.value = sample.point
            if (now >= endAt) {
                MockState.errors.tryEmit("انتهت مدة الجدولة، تم إيقاف الموقع الوهمي")
                shutdown()
                return
            }
            delay(settings.intervalMs.coerceIn(100L, 5000L))
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "maslak:mock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun shutdown() {
        job?.cancel()
        job = null
        injector?.disable()
        injector = null
        runCatching { wakeLock?.release() }
        wakeLock = null
        MockState.phase.value = RunPhase.IDLE
        MockState.current.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        injector?.disable()
        runCatching { wakeLock?.release() }
        MockState.phase.value = RunPhase.IDLE
        MockState.current.value = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyUpdate(req: MockRequest) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIF_ID, buildNotification(req, waiting = false)) }
    }

    private fun buildNotification(req: MockRequest, waiting: Boolean): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "الموقع الوهمي", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP), flags
        )
        val modeText = when (req.mode) {
            MockMode.FIXED -> "موقع ثابت"
            MockMode.ROUTE -> "مسار (${req.route.size} نقاط)"
            MockMode.RANDOM -> "حركة عشوائية"
        }
        val title = if (waiting) "بانتظار موعد البدء…" else "الموقع الوهمي يعمل • $modeText"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(req.center.format())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "إيقاف", stop)
            .build()
    }
}
