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

class BackupActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var path: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: BackupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        toolbar = findViewById(R.id.bkToolbar)
        path = findViewById(R.id.bkPath)
        recycler = findViewById(R.id.bkRecycler)
        val create: MaterialButton = findViewById(R.id.bkCreate)

        toolbar.setNavigationOnClickListener { finish() }
        path.text = "Thư mục: ${Const.BACKUP_DIR}"

        adapter = BackupAdapter(mutableListOf(), ::onRestore, ::onDelete)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        create.setOnClickListener {
            val r = BackupManager.backup(Const.PP_DAT, "manual")
            toast(if (r.startsWith("OK")) "Đã lưu backup" else r)
            refresh()
        }

        refresh()
    }

    private fun refresh() {
        adapter.update(BackupManager.listBackups())
    }

    private fun onRestore(f: File) {
        AlertDialog.Builder(this)
            .setTitle("Khôi phục?")
            .setMessage("Ghi đè pp.dat bằng:\n${f.name}\n\nTiến trình hiện tại sẽ bị thay thế.")
            .setPositiveButton("Khôi phục") { _, _ ->
                val r = BackupManager.restore(f.absolutePath, Const.PP_DAT)
                toast(if (r == "OK") "Đã khôi phục — mở PvZ2 để kiểm tra" else r)
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun onDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("Xóa backup?")
            .setMessage(f.name)
            .setPositiveButton("Xóa") { _, _ ->
                if (BackupManager.delete(f.absolutePath)) { refresh(); toast("Đã xóa") }
                else toast("Không xóa được")
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
