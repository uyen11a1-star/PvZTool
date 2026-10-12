package com.anon.pvztool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScannerAdapter(
    private val items: MutableList<BinaryScanner.Match>,
    private val onClick: (Int, BinaryScanner.Match) -> Unit
) : RecyclerView.Adapter<ScannerAdapter.VH>() {

    var selectedPos = -1
        private set

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val off: TextView = v.findViewById(R.id.mOffset)
        val hex: TextView = v.findViewById(R.id.mHex)
        val ascii: TextView = v.findViewById(R.id.mAscii)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_scan_match, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val m = items[p]
        h.off.text = "0x%08X  (%d)".format(m.offset, m.offset)
        h.hex.text = m.bytesHex
        h.ascii.text = m.preview
        h.itemView.setBackgroundColor(if (p == selectedPos) 0x20FF1493 else 0xFFFFFFFF.toInt())
        h.itemView.setOnClickListener {
            val old = selectedPos
            selectedPos = p
            notifyItemChanged(old)
            notifyItemChanged(p)
            onClick(p, m)
        }
    }

    fun update(list: List<BinaryScanner.Match>) {
        items.clear(); items.addAll(list)
        selectedPos = -1
        notifyDataSetChanged()
    }
}
