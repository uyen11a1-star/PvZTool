package com.anon.pvztool

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SliderAdapter(
    private val items: MutableList<Row>
) : RecyclerView.Adapter<SliderAdapter.VH>() {

    data class Row(
        val key: String,
        val offset: Int,
        val type: BinaryScanner.ValueType,
        var current: Long,
        var newValue: Long,
        val max: Long = 999_999_999L,
        val isFloat: Boolean = false
    )

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val key: TextView = v.findViewById(R.id.slKey)
        val offset: TextView = v.findViewById(R.id.slOffset)
        val current: TextView = v.findViewById(R.id.slCurrent)
        val input: EditText = v.findViewById(R.id.slValue)
        val seek: SeekBar = v.findViewById(R.id.slSeek)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_slider, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val r = items[p]
        h.key.text = r.key.uppercase()
        h.offset.text = "0x%08X".format(r.offset)
        h.current.text = "Hiện: ${r.current}"

        // Xóa watcher cũ
        (h.input.getTag() as? TextWatcher)?.let { h.input.removeTextChangedListener(it) }
        (h.seek.getTag() as? SeekBar.OnSeekBarChangeListener)?.let { h.seek.setOnSeekBarChangeListener(null) }

        h.input.setText(r.newValue.toString())
        h.seek.max = 1000
        h.seek.progress = ((r.newValue.toDouble() / r.max) * 1000).toInt().coerceIn(0, 1000)

        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                var v = s?.toString()?.toLongOrNull() ?: return
                if (v > r.max) {
                    v = r.max
                    h.input.setText(v.toString())
                    h.input.setSelection(h.input.text.length)
                }
                r.newValue = v
                h.seek.progress = ((v.toDouble() / r.max) * 1000).toInt().coerceIn(0, 1000)
            }
        }
        h.input.addTextChangedListener(w)
        h.input.setTag(w)

        val sb = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = (prog.toLong() * r.max) / 1000
                r.newValue = v
                h.input.setText(v.toString())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        h.seek.setOnSeekBarChangeListener(sb)
        h.seek.setTag(sb)
    }

    fun update(list: List<Row>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun rows(): List<Row> = items
}
