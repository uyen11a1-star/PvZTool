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
        val label: TextView = v.findViewById(R.id.mLabel)
        val value: TextView = v.findViewById(R.id.mValue)
        val off: TextView = v.findViewById(R.id.mOffset)
        val hex: TextView = v.findViewById(R.id.mHex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_scan_match, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val m = items[p]
        h.label.text = m.label
        h.label.setTextColor(
            if (m.label == "Unknown") 0xFF8888A0.toInt() else 0xFFFF1493.toInt()
        )
        h.value.text = if (m.decodedValue >= 0) "= ${m.decodedValue}" else ""
        h.off.text = "0x%08X  (%d)".format(m.offset, m.offset)
        h.hex.text = m.bytesHex

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
