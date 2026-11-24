package com.maslak.gps.mock

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.maslak.gps.MainActivity
import com.maslak.gps.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * أيقونة دائرية عائمة فوق كل التطبيقات.
 * تظهر عند إخفاء التطبيق، تُسحب لأي مكان، والضغطة ترجع التطبيق.
 * حلقة خضراء تعني أن الموقع الوهمي يعمل، ورمادية تعني متوقف.
 */
class BubbleService : Service() {

    companion object {
        const val ACTION_SHOW = "com.maslak.gps.BUBBLE_SHOW"
        const val ACTION_HIDE = "com.maslak.gps.BUBBLE_HIDE"

        fun canDraw(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)

        fun show(ctx: Context) {
            if (!canDraw(ctx)) return
            ctx.startService(Intent(ctx, BubbleService::class.java).setAction(ACTION_SHOW))
        }

        fun hide(ctx: Context) {
            ctx.startService(Intent(ctx, BubbleService::class.java).setAction(ACTION_HIDE))
        }
    }

    private var wm: WindowManager? = null
    private var view: View? = null
    private var ring: ImageView? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> { removeView(); stopSelf() }
            else -> addView()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addView() {
        if (view != null) return
        if (!canDraw(this)) { stopSelf(); return }
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager

        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this)
        val ringView = ImageView(this).apply {
            setImageResource(R.drawable.bubble_bg)
            setPadding(6, 6, 6, 6)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            val p = (14 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        container.addView(ringView, FrameLayout.LayoutParams(sizePx, sizePx))
        container.addView(icon, FrameLayout.LayoutParams(sizePx, sizePx))
        ring = ringView

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx - 24
            y = resources.displayMetrics.heightPixels / 3
        }

        container.setOnTouchListener(DragClickListener(params) { manager.updateViewLayout(container, params) })
        runCatching { manager.addView(container, params) }
            .onFailure { stopSelf(); return }
        view = container

        // تحديث لون الحلقة حسب حالة الموقع الوهمي
        watchJob?.cancel()
        watchJob = scope.launch {
            MockState.phase.collect { phase ->
                ringView.setImageResource(
                    if (phase == RunPhase.RUNNING) R.drawable.bubble_bg else R.drawable.bubble_bg_off
                )
            }
        }
    }

    /** يميّز بين السحب والضغط، ويعيد التطبيق عند الضغط */
    private inner class DragClickListener(
        private val params: WindowManager.LayoutParams,
        private val apply: () -> Unit,
    ) : View.OnTouchListener {
        private var initX = 0; private var initY = 0
        private var touchX = 0f; private var touchY = 0f
        private var moved = false
        private val slop = (8 * resources.displayMetrics.density)

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX; val dy = e.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    params.x = initX + dx.toInt()
                    params.y = initY + dy.toInt()
                    apply()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openApp()
                    return true
                }
            }
            return false
        }
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
        removeView()
        stopSelf()
    }

    private fun removeView() {
        watchJob?.cancel()
        view?.let { runCatching { wm?.removeView(it) } }
        view = null
        ring = null
    }

    override fun onDestroy() {
        removeView()
        scope.cancel()
        super.onDestroy()
    }
}
