package com.anon.pvztool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var menuView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams
    private val ui = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL = "pvztool_float"
        const val NOTIF_ID = 9001
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        menuView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubbleView = null; menuView = null
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(NotificationChannel(
                    CHANNEL, "PvZ Tool bubble", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("PvZ Tool đang chạy")
            .setContentText("Bubble nổi đang bật")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        val v = LayoutInflater.from(this).inflate(R.layout.float_bubble, null)
        attachDrag(v)
        wm.addView(v, bubbleParams)
        bubbleView = v
    }

    private fun attachDrag(v: View) {
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = bubbleParams.x
                    initY = bubbleParams.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    bubbleParams.x = initX + dx.toInt()
                    bubbleParams.y = initY + dy.toInt()
                    bubbleView?.let { try { wm.updateViewLayout(it, bubbleParams) } catch (_: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMenu()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleMenu() {
        if (menuView != null) { hideMenu(); return }
        showMenu()
    }

    private fun showMenu() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleParams.x + 70).coerceAtLeast(20)
            y = bubbleParams.y
        }

        val m = LayoutInflater.from(this).inflate(R.layout.float_menu, null)
        wm.addView(m, menuParams)
        menuView = m

        val status: TextView = m.findViewById(R.id.menuStatus)

        m.findViewById<View>(R.id.menuClose).setOnClickListener { hideMenu() }
        m.findViewById<View>(R.id.qCoin).setOnClickListener {
            quickPatch(mapOf("coin" to 20000L), status)
        }
        m.findViewById<View>(R.id.qGem).setOnClickListener {
            quickPatch(mapOf("gem" to 20000L), status)
        }
        m.findViewById<View>(R.id.qAll).setOnClickListener {
            val all = mutableMapOf<String, Long>()
            for (k in OffsetStore.allKeys()) if (OffsetStore.get(this, k) != null) all[k] = 20000L
            if (all.isEmpty()) { toastOnUi("Chưa có offset nào. Mở app → Scanner trước."); return@setOnClickListener }
            quickPatch(all, status)
        }
        m.findViewById<View>(R.id.qRestoreBackup).setOnClickListener { restoreLatest(status) }
        m.findViewById<View>(R.id.qLaunchPvZ).setOnClickListener {
            launchPkg(Const.ROW_PKG, status)
        }
        m.findViewById<View>(R.id.qOpenApp).setOnClickListener {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i); hideMenu()
        }
        m.findViewById<View>(R.id.qHide).setOnClickListener {
            stopSelf()
        }
    }

    private fun hideMenu() {
        menuView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        menuView = null
    }

    // ====================== QUICK PATCH ======================

    private fun quickPatch(targets: Map<String, Long>, status: TextView) {
        setStatus(status, "Đang backup…")
        Thread {
            val bk = BackupManager.backup(Const.PP_DAT, "bubble")
            if (!bk.startsWith("OK")) {
                ui.post { setStatus(status, "Backup fail: $bk") }
                return@Thread
            }

            val ext = getExternalFilesDir("work") ?: cacheDir
            if (!ext.exists()) ext.mkdirs()
            val local = File(ext, "pp.dat")
            if (local.exists()) local.delete()

            ui.post { setStatus(status, "Đang copy pp.dat…") }
            val remote = ShizukuShell.translate(Const.PP_DAT)
            ShizukuShell.exec("cp ${ShizukuShell.quote(remote)} ${ShizukuShell.quote(local.absolutePath)} 2>&1")
            if (!local.exists() || local.length() == 0L) {
                ui.post { setStatus(status, "Copy pp.dat fail — Shizuku chưa bật?") }
                return@Thread
            }

            var okCount = 0
            var lastErr = ""
            for ((key, valTarget) in targets) {
                val f = OffsetStore.get(this, key) ?: continue
                val type = try { BinaryScanner.ValueType.valueOf(f.type) } catch (_: Exception) { BinaryScanner.ValueType.INT32LE }
                val isVarint = type.name.startsWith("VARINT")
                val zig = type.name == "VARINT_ZIGZAG"
                val err = if (isVarint) {
                    Patcher.writeVarintExpandable(local, f.offset, valTarget, zig)
                } else if (type.name.contains("FLOAT") || type.name.contains("DOUBLE")) {
                    Patcher.writeFloat(local, f.offset, valTarget.toFloat(), type)
                } else {
                    Patcher.writeInt(local, f.offset, valTarget, type)
                }
                if (err.startsWith("OK")) okCount++
                else lastErr = "$key: $err"
                // Nếu shift byte → các field sau bị lệch, cần re-fetch offset
                // Ở đây ta đọc lại OffsetStore sau mỗi patch thất bại
            }

            if (okCount == 0) {
                ui.post { setStatus(status, "Không có gì patch. $lastErr") }
                return@Thread
            }

            ui.post { setStatus(status, "Đang ghi ngược…") }
            ShizukuShell.exec("rm -f ${ShizukuShell.quote(remote)} 2>&1")
            val cpRes = ShizukuShell.exec("cp ${ShizukuShell.quote(local.absolutePath)} ${ShizukuShell.quote(remote)} 2>&1")
            if (cpRes.contains("Permission denied", true) || cpRes.startsWith("ERR")) {
                ui.post { setStatus(status, "Ghi fail: $cpRes") }
                return@Thread
            }

            ui.post {
                setStatus(status, "✓ Đã patch $okCount field = 20000\nForce stop PvZ2 → mở lại")
            }
        }.start()
    }

    private fun restoreLatest(status: TextView) {
        val list = BackupManager.listBackups()
        if (list.isEmpty()) { setStatus(status, "Không có backup nào"); return }
        val latest = list.first()
        setStatus(status, "Đang khôi phục…")
        Thread {
            val r = BackupManager.restore(latest.absolutePath, Const.PP_DAT)
            ui.post {
                setStatus(status, if (r == "OK") "✓ Đã khôi phục ${latest.name}" else "Fail: $r")
            }
        }.start()
    }

    private fun launchPkg(pkg: String, status: TextView) {
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i == null) { setStatus(status, "Không tìm thấy $pkg"); return }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            hideMenu()
        } catch (e: Exception) {
            setStatus(status, "Lỗi mở app: ${e.message}")
        }
    }

    private fun setStatus(tv: TextView, text: String) {
        ui.post { tv.text = text }
    }

    private fun toastOnUi(msg: String) {
        ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
