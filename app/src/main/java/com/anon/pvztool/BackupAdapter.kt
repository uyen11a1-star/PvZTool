package com.anon.pvztool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class BackupAdapter(
    private val items: MutableList<File>,
    private val onRestore: (File) -> Unit,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<BackupAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.bkName)
        val meta: TextView = v.findViewById(R.id.bkMeta)
        val restore: View = v.findViewById(R.id.bkRestore)
        val delete: View = v.findViewById(R.id.bkDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_backup, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val f = items[p]
        h.name.text = f.name
        h.meta.text = "${BackupManager.fmtSize(f.length())}  ·  ${BackupManager.fmtDate(f.lastModified())}"
        h.restore.setOnClickListener { onRestore(f) }
        h.delete.setOnClickListener { onDelete(f) }
    }

    fun update(list: List<File>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
}
