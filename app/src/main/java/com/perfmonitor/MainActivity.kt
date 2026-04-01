package com.perfmonitor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST = 1002
    }

    private var isMonitoring = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createUI())
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun createUI(): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (32 * density).toInt(),
                (48 * density).toInt(),
                (32 * density).toInt(),
                (32 * density).toInt()
            )
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        // App icon/title
        val titleTv = TextView(this).apply {
            text = "⚡ PerfMonitor"
            setTextColor(Color.parseColor("#4FC3F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(titleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (8 * density).toInt()
        })

        // Subtitle
        val subtitleTv = TextView(this).apply {
            text = "实时性能监控悬浮窗"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }
        root.addView(subtitleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (32 * density).toInt()
        })

        // Info card
        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (16 * density).toInt(),
                (20 * density).toInt(),
                (16 * density).toInt()
            )
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#16213E"))
                cornerRadius = 16f * density
                setStroke((1 * density).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        val features = listOf(
            "📊 FPS - 实时帧率监控",
            "🔧 CPU - 各核心频率",
            "💾 DDR - 内存总线频率"
        )
        for (feature in features) {
            val featureTv = TextView(this).apply {
                text = feature
                setTextColor(Color.parseColor("#E0E0E0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
            infoCard.addView(featureTv)
        }

        root.addView(infoCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (32 * density).toInt()
        })

        // Status text
        tvStatus = TextView(this).apply {
            text = "状态: 未运行"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }
        root.addView(tvStatus, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (16 * density).toInt()
        })

        // Toggle button
        btnToggle = Button(this).apply {
            text = "开启监控"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#4FC3F7"))
                cornerRadius = 28f * density
            }
            setPadding(
                (32 * density).toInt(),
                (14 * density).toInt(),
                (32 * density).toInt(),
                (14 * density).toInt()
            )
            isAllCaps = false
            setOnClickListener { toggleMonitoring() }
        }
        root.addView(btnToggle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (8 * density).toInt()
        })

        // Hint
        val hintTv = TextView(this).apply {
            text = "悬浮窗可拖动 · 通知栏可关闭"
            setTextColor(Color.parseColor("#607D8B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        root.addView(hintTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (24 * density).toInt()
        })

        return root
    }

    private fun toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            return
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
                return
            }
        }

        // Start the overlay service
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        isMonitoring = true
        updateUI()
        Toast.makeText(this, "监控已开启", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        isMonitoring = false
        updateUI()
        Toast.makeText(this, "监控已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        if (isMonitoring) {
            btnToggle.text = "关闭监控"
            btnToggle.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FF5252"))
                cornerRadius = 28f * resources.displayMetrics.density
            }
            tvStatus.text = "状态: 运行中 ✅"
            tvStatus.setTextColor(Color.parseColor("#76FF03"))
        } else {
            btnToggle.text = "开启监控"
            btnToggle.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#4FC3F7"))
                cornerRadius = 28f * resources.displayMetrics.density
            }
            tvStatus.text = "状态: 未运行"
            tvStatus.setTextColor(Color.parseColor("#90A4AE"))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startMonitoring()
            } else {
                Toast.makeText(this, "悬浮窗权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            // Proceed regardless - notification permission is not strictly required
            startMonitoring()
        }
    }
}
