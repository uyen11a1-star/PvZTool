package com.anon.pvztool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusBar: TextView
    private lateinit var savedOffsets: TextView

    private val SHIZUKU_REQ = 9001
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { ensureAllFiles() }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { req, res ->
        if (req == SHIZUKU_REQ) runOnUiThread {
            if (res == PackageManager.PERMISSION_GRANTED) refreshStatus()
            else toast("Từ chối Shizuku")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusBar = findViewById(R.id.statusBar)
        savedOffsets = findViewById(R.id.savedOffsets)

        findViewById<MaterialButton>(R.id.btnShizuku).setOnClickListener { requestShizuku() }
        findViewById<MaterialButton>(R.id.btnBubble).setOnClickListener {
            startFloatingService()
        }
        findViewById<MaterialButton>(R.id.btnBackup).setOnClickListener {
            val r = BackupManager.backup(Const.PP_DAT, "manual")
            toast(if (r.startsWith("OK")) "Đã lưu backup" else r)
            refreshStatus()
        }
        findViewById<MaterialButton>(R.id.btnScanner).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnSlider).setOnClickListener {
            startActivity(Intent(this, SliderActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnBackups).setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnInfo).setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        Shizuku.addRequestPermissionResultListener(shizukuListener)
        ensureAllFiles()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun startFloatingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Cần quyền vẽ trên app khác")
                .setMessage("Cấp quyền để hiện bubble nổi trên PvZ2.")
                .setPositiveButton("Mở Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Hủy", null)
                .show()
            return
        }
        val i = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        toast("Đã bật bubble — quay lại màn hình chính để thấy nút PvZ")
    }

    private fun ensureAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                toast("Cấp 'All files access' cho PvZ Tool")
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.parse("package:$packageName")
                try { permLauncher.launch(i) } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                return
            }
        }
        refreshStatus()
    }

    private fun requestShizuku() {
        if (!ShizukuShell.isRunning()) {
            toast("Shizuku chưa chạy. Mở app Shizuku và start service.")
            return
        }
        if (ShizukuShell.isGranted()) { toast("Đã có quyền Shizuku"); return }
        try { Shizuku.requestPermission(SHIZUKU_REQ) } catch (e: Throwable) { toast("Lỗi: ${e.message}") }
    }

    private fun refreshStatus() {
        val files = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        val sh = ShizukuShell.isGranted()
        val ppExists = File(Const.PP_DAT).exists() ||
                ShizukuShell.exec("ls ${ShizukuShell.quote(Const.PP_DAT)} 2>&1").contains("pp.dat")

        val sb = StringBuilder()
        sb.append(if (files) "✓" else "✗").append(" Files access\n")
        sb.append(if (sh) "✓" else "✗").append(" Shizuku\n")
        sb.append(if (ppExists) "✓" else "✗").append(" pp.dat tìm thấy\n")
        val backups = BackupManager.listBackups().size
        sb.append("• Backup: $backups file")
        statusBar.text = sb.toString()

        // Saved offsets
        val lines = mutableListOf<String>()
        for (k in OffsetStore.allKeys()) {
            OffsetStore.get(this, k)?.let { f ->
                lines.add("%-10s  0x%08X  (%s)".format(k, f.offset, f.type))
            }
        }
        savedOffsets.text = if (lines.isEmpty()) "Chưa lưu offset nào. Vào Scanner → quét → lưu."
        else "Offset đã lưu:\n" + lines.joinToString("\n")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
