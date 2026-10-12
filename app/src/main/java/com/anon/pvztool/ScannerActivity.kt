package com.anon.pvztool

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

class ScannerActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var valueInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var scanBtn: MaterialButton
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var savePanel: View
    private lateinit var saveKey: Spinner
    private lateinit var saveBtn: MaterialButton
    private lateinit var adapter: ScannerAdapter

    private var selectedMatch: BinaryScanner.Match? = null
    private var currentType: BinaryScanner.ValueType = BinaryScanner.ValueType.INT32LE

    private val types = listOf(
        BinaryScanner.ValueType.VARINT,
        BinaryScanner.ValueType.VARINT_ZIGZAG,
        BinaryScanner.ValueType.INT32LE,
        BinaryScanner.ValueType.INT32BE,
        BinaryScanner.ValueType.INT16LE,
        BinaryScanner.ValueType.INT16BE,
        BinaryScanner.ValueType.INT64LE,
        BinaryScanner.ValueType.INT64BE,
        BinaryScanner.ValueType.INT8,
        BinaryScanner.ValueType.FLOATLE,
        BinaryScanner.ValueType.FLOATBE,
        BinaryScanner.ValueType.DOUBLELE,
        BinaryScanner.ValueType.DOUBLEBE,
        BinaryScanner.ValueType.ASCII_STR,
        BinaryScanner.ValueType.UTF16LE_STR
    )

    private val keys = listOf("coin", "gem", "mint", "gauntlet", "pennyfuel", "zensun", "trophy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        toolbar = findViewById(R.id.scannerToolbar)
        valueInput = findViewById(R.id.scanValue)
        typeSpinner = findViewById(R.id.scanType)
        scanBtn = findViewById(R.id.scanBtn)
        status = findViewById(R.id.scanStatus)
        hint = findViewById(R.id.scanHint)
        recycler = findViewById(R.id.scanRecycler)
        savePanel = findViewById(R.id.savePanel)
        saveKey = findViewById(R.id.saveKey)
        saveBtn = findViewById(R.id.saveBtn)

        toolbar.setNavigationOnClickListener { finish() }

        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            types.map { it.label }).also {
            typeSpinner.setSelection(types.indexOf(BinaryScanner.ValueType.INT32LE))
        }
        saveKey.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, keys)

        adapter = ScannerAdapter(mutableListOf()) { _, m ->
            selectedMatch = m
            savePanel.visibility = View.VISIBLE
            hint.text = "Đã chọn offset 0x%08X — chọn field và nhấn Lưu".format(m.offset)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        scanBtn.setOnClickListener { doScan() }
        saveBtn.setOnClickListener { doSave() }
    }

    private fun doScan() {
        val txt = valueInput.text.toString().trim()
        if (txt.isEmpty()) { toast("Nhập giá trị"); return }
        currentType = types[typeSpinner.selectedItemPosition]
        status.text = "Đang quét…"
        scanBtn.isEnabled = false

        Thread {
            try {
                val tmp = copyPpDatLocal() ?: run {
                    runOnUiThread { status.text = "Lỗi: không đọc được pp.dat"; scanBtn.isEnabled = true }
                    return@Thread
                }
                val matches = when (currentType) {
                    BinaryScanner.ValueType.VARINT -> {
                        val v = txt.toLongOrNull() ?: run {
                            runOnUiThread { status.text = "Giá trị không phải số"; scanBtn.isEnabled = true }
                            return@Thread
                        }
                        BinaryScanner.scanVarint(tmp, v, zigzag = false)
                    }
                    BinaryScanner.ValueType.VARINT_ZIGZAG -> {
                        val v = txt.toLongOrNull() ?: run {
                            runOnUiThread { status.text = "Giá trị không phải số"; scanBtn.isEnabled = true }
                            return@Thread
                        }
                        BinaryScanner.scanVarint(tmp, v, zigzag = true)
                    }
                    BinaryScanner.ValueType.ASCII_STR -> BinaryScanner.scanAscii(tmp, txt)
                    BinaryScanner.ValueType.UTF16LE_STR -> BinaryScanner.scanUtf16Le(tmp, txt)
                    else -> {
                        if (currentType.name.contains("FLOAT") || currentType.name.contains("DOUBLE")) {
                            val f = txt.toFloatOrNull() ?: run {
                                runOnUiThread { status.text = "Giá trị không phải số thực"; scanBtn.isEnabled = true }
                                return@Thread
                            }
                            BinaryScanner.scanFloat(tmp, f, currentType)
                        } else {
                            val v = txt.toLongOrNull() ?: run {
                                runOnUiThread { status.text = "Giá trị không phải số nguyên"; scanBtn.isEnabled = true }
                                return@Thread
                            }
                            BinaryScanner.scanInt(tmp, v, currentType)
                        }
                    }
                }
                runOnUiThread {
                    adapter.update(matches)
                    status.text = "Tìm thấy ${matches.size} vị trí. ${if (matches.size > 30) "Thu hẹp bằng cách tiêu/nhận thêm rồi quét lại." else ""}"
                    scanBtn.isEnabled = true
                    hint.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Lỗi: ${e.message}"
                    scanBtn.isEnabled = true
                }
            }
        }.start()
    }

    private fun doSave() {
        val m = selectedMatch ?: run { toast("Chưa chọn offset"); return }
        val key = keys[saveKey.selectedItemPosition]
        OffsetStore.save(this, key, m.offset, currentType.name, currentType.size)
        toast("Đã lưu $key → 0x%08X (${currentType.label})".format(m.offset))
    }

    // Copy pp.dat ra external files dir để Scanner đọc trực tiếp
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
