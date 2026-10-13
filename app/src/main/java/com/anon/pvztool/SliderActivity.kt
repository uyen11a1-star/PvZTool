package com.anon.pvztool

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

class SliderActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var info: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: SliderAdapter

    private val keyLabels = mapOf(
        "coin" to "Coins",
        "gem" to "Gems",
        "mint" to "Mints",
        "gauntlet" to "Gauntlet",
        "pennyfuel" to "Penny Fuel",
        "zensun" to "Zen Sun",
        "trophy" to "Trophy"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slider)

        toolbar = findViewById(R.id.sliderToolbar)
        info = findViewById(R.id.sliderInfo)
        recycler = findViewById(R.id.sliderRecycler)
        val applyAll: MaterialButton = findViewById(R.id.applyAll)
        val backupFirst: MaterialButton = findViewById(R.id.backupFirst)

        toolbar.setNavigationOnClickListener { finish() }

        adapter = SliderAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadFields()

        applyAll.setOnClickListener { applyAllFields() }
        backupFirst.setOnClickListener {
            val r = BackupManager.backup(Const.PP_DAT, "manual")
            toast(r)
        }
    }

    private fun loadFields() {
        val tmp = copyPpDatLocal()
        if (tmp == null) {
            info.text = "Lỗi: không copy được pp.dat. Đảm bảo Shizuku đã chạy + đã cấp quyền."
            return
        }
        val rows = mutableListOf<SliderAdapter.Row>()
        for (key in OffsetStore.allKeys()) {
            val f = OffsetStore.get(this, key) ?: continue
            val type = try { BinaryScanner.ValueType.valueOf(f.type) } catch (_: Exception) { BinaryScanner.ValueType.INT32LE }
            val curLong = if (type.name.startsWith("VARINT")) {
                val zig = type.name == "VARINT_ZIGZAG"
                val pair = Patcher.readVarintAt(tmp, f.offset)
                if (pair == null) 0L
                else {
                    val raw = pair.first
                    if (zig) (raw ushr 1) xor -(raw and 1L) else raw
                }
            } else {
                val cur = Patcher.read(tmp, f.offset, type)
                when (cur) {
                    is Long -> cur
                    is Float -> cur.toLong()
                    is Double -> cur.toLong()
                    else -> 0L
                }
            }
            val max = when (key) {
                "coin" -> 999_999_999L
                "gem" -> 999_999_999L
                "mint" -> 999_999L
                "gauntlet" -> 9_999L
                "pennyfuel" -> 9_999L
                "zensun" -> 999_999L
                "trophy" -> 99_999L
                else -> 999_999L
            }
            rows.add(SliderAdapter.Row(key, f.offset, type, curLong, curLong, max))
        }

        if (rows.isEmpty()) {
            info.text = "Chưa có field nào được lưu. Vào Scanner, quét giá trị và lưu offset cho Coin/Gems trước."
        } else {
            info.text = "Đã tải ${rows.size} field. Kéo slider hoặc nhập số, rồi nhấn Áp dụng."
        }
        adapter.update(rows)
    }

    private fun applyAllFields() {
        val rows = adapter.rows()
        if (rows.isEmpty()) { toast("Không có field nào"); return }

        AlertDialog.Builder(this)
            .setTitle("Áp dụng ${rows.size} field?")
            .setMessage("Backup tự động trước khi ghi. Tiếp tục?")
            .setPositiveButton("Áp dụng") { _, _ -> runApply(rows) }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun runApply(rows: List<SliderAdapter.Row>) {
        // Backup tự động
        val bk = BackupManager.backup(Const.PP_DAT, "pre_apply")
        if (!bk.startsWith("OK")) {
            toast("Backup thất bại: $bk")
            return
        }

        val tmp = copyPpDatLocal() ?: run { toast("Copy pp.dat thất bại"); return }

        // Log để chẩn đoán
        val log = StringBuilder()
        log.append("File local: ${tmp.absolutePath}\n")
        log.append("Size local: ${tmp.length()} bytes\n\n")

        var okCount = 0
        var errCount = 0
        val errMsgs = mutableListOf<String>()
        for (r in rows) {
            if (r.newValue == r.current) {
                log.append("${r.key}: bỏ qua (giá trị không đổi)\n")
                continue
            }
            val isFloat = r.type.name.contains("FLOAT") || r.type.name.contains("DOUBLE")
            val isVarint = r.type.name.startsWith("VARINT")
            val zig = r.type.name == "VARINT_ZIGZAG"
            val err = when {
                isVarint -> Patcher.writeVarintSafe(tmp, r.offset, r.newValue, zig)
                isFloat -> Patcher.writeFloat(tmp, r.offset, r.newValue.toFloat(), r.type)
                else -> Patcher.writeInt(tmp, r.offset, r.newValue, r.type)
            }
            log.append("${r.key}: write → $err\n")
            if (err == "OK") okCount++
            else { errCount++; if (errMsgs.size < 3) errMsgs.add("${r.key}: ${err}") }
        }

        if (okCount == 0) {
            AlertDialog.Builder(this)
                .setTitle("Không có thay đổi")
                .setMessage(log.toString())
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Verify: đọc lại file local để xác nhận giá trị mới
        log.append("\n--- Verify local ---\n")
        for (r in rows) {
            if (r.newValue == r.current) continue
            val isVarint = r.type.name.startsWith("VARINT")
            val newVal = if (isVarint) {
                val p = Patcher.readVarintAt(tmp, r.offset)
                p?.first ?: -1L
            } else {
                val c = Patcher.read(tmp, r.offset, r.type)
                when (c) { is Long -> c; is Float -> c.toLong(); is Double -> c.toLong(); else -> -1L }
            }
            log.append("${r.key} local sau patch = $newVal (kỳ vọng ${r.newValue})\n")
        }

        // Copy ngược về remote
        val remote = ShizukuShell.translate(Const.PP_DAT)
        log.append("\n--- Ghi ngược về remote ---\n")
        log.append("Remote: $remote\n")

        // Xóa remote trước để chắc chắn ghi đè
        val rmRes = ShizukuShell.exec("rm -f ${ShizukuShell.quote(remote)} 2>&1")
        log.append("rm: ${rmRes.trim().ifBlank { "(ok)" }}\n")

        val cpRes = ShizukuShell.exec("cp ${ShizukuShell.quote(tmp.absolutePath)} ${ShizukuShell.quote(remote)} 2>&1")
        log.append("cp: ${cpRes.trim().ifBlank { "(ok)" }}\n")

        // Verify remote sau khi ghi
        log.append("\n--- Verify remote ---\n")
        val remoteCheck = ShizukuShell.exec("ls -la ${ShizukuShell.quote(remote)} 2>&1")
        log.append(remoteCheck.trim() + "\n")

        // Copy remote về local 1 lần nữa để verify
        val verifyLocal = File(getExternalFilesDir("work"), "verify.dat")
        ShizukuShell.exec("cp ${ShizukuShell.quote(remote)} ${ShizukuShell.quote(verifyLocal.absolutePath)} 2>&1")
        if (verifyLocal.exists()) {
            for (r in rows) {
                if (r.newValue == r.current) continue
                val isVarint = r.type.name.startsWith("VARINT")
                val v = if (isVarint) {
                    val p = Patcher.readVarintAt(verifyLocal, r.offset)
                    p?.first ?: -1L
                } else {
                    val c = Patcher.read(verifyLocal, r.offset, r.type)
                    when (c) { is Long -> c; is Float -> c.toLong(); is Double -> c.toLong(); else -> -1L }
                }
                val mark = if (v == r.newValue) "✓" else "✗"
                log.append("$mark ${r.key} remote = $v (kỳ vọng ${r.newValue})\n")
            }
        } else {
            log.append("⚠ Không copy được remote về để verify\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Kết quả")
            .setMessage(log.toString())
            .setPositiveButton("OK") { _, _ -> loadFields() }
            .setNeutralButton("Copy log") { _, _ ->
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", log.toString()))
                toast("Đã copy log")
            }
            .show()
    }

    private fun copyPpDatLocal(): File? {
        val ext = getExternalFilesDir("work") ?: cacheDir
        if (!ext.exists()) ext.mkdirs()
        val local = File(ext, "pp.dat")
        // Xóa file tạm cũ để không nhầm với lần trước
        if (local.exists()) local.delete()
        val remote = ShizukuShell.translate(Const.PP_DAT)
        val res = ShizukuShell.exec("cp ${ShizukuShell.quote(remote)} ${ShizukuShell.quote(local.absolutePath)} 2>&1")
        if (res.contains("Permission denied", true) || res.startsWith("ERR")) return null
        if (!local.exists() || local.length() == 0L) return null
        return local
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
