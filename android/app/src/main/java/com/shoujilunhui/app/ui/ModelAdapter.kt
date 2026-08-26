package com.shoujilunhui.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shoujilunhui.app.R
import com.shoujilunhui.app.data.ModelRow

class ModelAdapter(
    private val onClick: (ModelRow) -> Unit,
    private val onLongClick: (ModelRow) -> Unit
) : RecyclerView.Adapter<ModelAdapter.VH>() {

    private val items = mutableListOf<ModelRow>()

    fun submit(list: List<ModelRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun remove(id: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun update(row: ModelRow) {
        val idx = items.indexOfFirst { it.id == row.id }
        if (idx >= 0) {
            items[idx] = row
            notifyItemChanged(idx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.tvModel.text = it.model
        holder.tvBrand.text = "${it.brand} · ${it.category}"
        val note = it.note ?: ""
        if (note.isNotBlank()) {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = note
        } else {
            holder.tvNote.visibility = View.GONE
        }
        holder.tvPrice.text = if (it.price.isNotBlank()) "${it.price} 元" else "面议"
        holder.itemView.setOnClickListener { onClick(it) }
        holder.itemView.setOnLongClickListener { onLongClick(it); true }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvModel: TextView = v.findViewById(R.id.tvModel)
        val tvBrand: TextView = v.findViewById(R.id.tvBrand)
        val tvNote: TextView = v.findViewById(R.id.tvNote)
        val tvPrice: TextView = v.findViewById(R.id.tvPrice)
    }
}
