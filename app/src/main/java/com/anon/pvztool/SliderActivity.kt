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
        var okCount = 0
        var errCount = 0
        for (r in rows) {
            if (r.newValue == r.current) continue
            val isFloat = r.type.name.contains("FLOAT") || r.type.name.contains("DOUBLE")
            val err = if (isFloat) Patcher.writeFloat(tmp, r.offset, r.newValue.toFloat(), r.type)
                      else Patcher.writeInt(tmp, r.offset, r.newValue, r.type)
            if (err == "OK") okCount++ else errCount++
        }

        if (okCount == 0 && errCount == 0) {
            toast("Không có thay đổi")
            return
        }

        // Copy ngược về pp.dat
        val remote = ShizukuShell.translate(Const.PP_DAT)
        val res = ShizukuShell.exec("cp ${ShizukuShell.quote(tmp.absolutePath)} ${ShizukuShell.quote(remote)} 2>&1")
        if (res.contains("Permission denied", true) || res.startsWith("ERR")) {
            toast("Ghi pp.dat thất bại: $res")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Xong")
            .setMessage("Đã patch $okCount field${if (errCount > 0) ", $errCount lỗi" else ""}.\n\n" +
                        "Bước tiếp:\n" +
                        "1. Mở PvZ2\n" +
                        "2. Nếu giá trị chưa đổi → Settings → Apps → PvZ2 → Force Stop → mở lại\n" +
                        "3. Nếu vẫn không đổi → khởi động lại điện thoại\n\n" +
                        "Backup: ${bk.removePrefix("OK: ")}")
            .setPositiveButton("OK") { _, _ -> recreate() }
            .show()
    }

    private fun copyPpDatLocal(): File? {
        val ext = getExternalFilesDir("work") ?: cacheDir
        if (!ext.exists()) ext.mkdirs()
        val local = File(ext, "pp.dat")
        val remote = ShizukuShell.translate(Const.PP_DAT)
        val res = ShizukuShell.exec("cp ${ShizukuShell.quote(remote)} ${ShizukuShell.quote(local.absolutePath)} 2>&1")
        if (res.contains("Permission denied", true) || res.startsWith("ERR")) return null
        if (!local.exists() || local.length() == 0L) return null
        return local
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
