package com.perfmonitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "perf_monitor_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.perfmonitor.STOP"
        private const val UPDATE_INTERVAL_MS = 1000L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var tvFps: TextView
    private lateinit var tvCpu: TextView
    private lateinit var tvCpuLoad: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvDdr: TextView

    private val fpsMonitor = FpsMonitor()
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createOverlayView()
        fpsMonitor.start()
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        fpsMonitor.stop()
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView() {
        val density = resources.displayMetrics.density

        // Root container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (8 * density).toInt(),
                (4 * density).toInt(),
                (8 * density).toInt(),
                (4 * density).toInt()
            )
            background = createRoundedBackground()
        }

        // Title row
        val titleTv = TextView(this).apply {
            text = "⚡ PerfMonitor"
            setTextColor(Color.parseColor("#4FC3F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(titleTv)

        // FPS
        tvFps = TextView(this).apply {
            text = "FPS: --"
            setTextColor(Color.parseColor("#76FF03"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        container.addView(tvFps)

        // CPU
        tvCpu = TextView(this).apply {
            text = "CPU: --"
            setTextColor(Color.parseColor("#FFD740"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        container.addView(tvCpu)

        // CPU Load
        tvCpuLoad = TextView(this).apply {
            text = "Load: --"
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        container.addView(tvCpuLoad)

        // Temperature
        tvTemp = TextView(this).apply {
            text = "Temp: --"
            setTextColor(Color.parseColor("#FF7043"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        container.addView(tvTemp)

        // DDR
        tvDdr = TextView(this).apply {
            text = "DDR: --"
            setTextColor(Color.parseColor("#FF8A65"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }
        container.addView(tvDdr)

        overlayView = container

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (80 * density).toInt()
        }

        // Make the overlay draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun createRoundedBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#CC1A1A2E"))
            cornerRadius = 12f * resources.displayMetrics.density
            setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                Color.parseColor("#44FFFFFF")
            )
        }
    }

    private fun updateStats() {
        // FPS
        val fps = fpsMonitor.getFps()
        val fpsColor = when {
            fps >= 55 -> "#76FF03"   // Green - good
            fps >= 30 -> "#FFD740"   // Yellow - ok
            else -> "#FF5252"        // Red - bad
        }
        tvFps.text = "FPS: $fps"
        tvFps.setTextColor(Color.parseColor(fpsColor))

        // Read system info in background to avoid blocking UI
        Thread {
            val cpuText = CpuFreqReader.readClusterFreqs()
            val ddrText = DdrFreqReader.readFormatted()

            // CPU Load
            val (totalLoad, coreLoads) = CpuLoadReader.readCpuLoad()
            val loadColor = when {
                totalLoad >= 80 -> "#FF5252"   // Red - high
                totalLoad >= 50 -> "#FFD740"   // Yellow - medium
                else -> "#76FF03"              // Green - low
            }
            val loadText = "Load: ${totalLoad}%"

            // Temperature
            val cpuTemp = TempReader.readCpuTemp()
            val tempColor = when {
                cpuTemp >= 80f -> "#FF5252"    // Red - hot
                cpuTemp >= 60f -> "#FFD740"    // Yellow - warm
                cpuTemp > 0f -> "#76FF03"      // Green - cool
                else -> "#90A4AE"              // Gray - N/A
            }
            val tempText = if (cpuTemp > 0f) {
                String.format("Temp: %.0f°C", cpuTemp)
            } else {
                "Temp: N/A"
            }

            handler.post {
                tvCpu.text = "CPU: $cpuText"
                tvCpuLoad.text = loadText
                tvCpuLoad.setTextColor(Color.parseColor(loadColor))
                tvTemp.text = tempText
                tvTemp.setTextColor(Color.parseColor(tempColor))
                tvDdr.text = "DDR: $ddrText"
            }
        }.start()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Performance Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows performance monitoring overlay"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PerfMonitor Running")
            .setContentText("Tap to open, or stop monitoring")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
