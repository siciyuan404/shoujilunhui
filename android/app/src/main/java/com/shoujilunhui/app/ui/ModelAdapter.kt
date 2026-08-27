package com.shoujilunhui.app.ui

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shoujilunhui.app.R
import com.shoujilunhui.app.data.ModelRow

class ModelAdapter(
    private var baseUrl: String,
    private val onClick: (ModelRow) -> Unit,
    private val onLongClick: (ModelRow) -> Unit
) : RecyclerView.Adapter<ModelAdapter.VH>() {

    fun updateBaseUrl(url: String) {
        baseUrl = url
    }

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
        val row = items[position]
        holder.tvModel.text = row.model
        holder.tvBrand.text = "${row.brand} · ${row.category}"
        val note = row.note ?: ""
        if (note.isNotBlank()) {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = note
        } else {
            holder.tvNote.visibility = View.GONE
        }
        holder.tvPrice.text = if (row.price.isNotBlank()) "${row.price} 元" else "面议"
        val specs = buildList {
            if (!row.cpuModel.isNullOrBlank()) add(row.cpuModel)
            if (!row.releaseDate.isNullOrBlank()) add(row.releaseDate!!.take(4) + "年")
            if (!row.backCamera.isNullOrBlank()) add(row.backCamera)
        }
        if (specs.isNotEmpty()) {
            holder.tvSpec.visibility = View.VISIBLE
            holder.tvSpec.text = specs.joinToString(" · ")
        } else {
            holder.tvSpec.visibility = View.GONE
        }
        // 缩略图
        val imgs = row.images?.filter { it.isNotBlank() }.orEmpty()
        if (imgs.isNotEmpty()) {
            holder.ivThumb.visibility = View.VISIBLE
            val first = imgs.first()
            val full = if (first.startsWith("http")) first else baseUrl.trimEnd('/') + "/" + first.trimStart('/')
            holder.ivThumb.load(full) {
                crossfade(true)
                placeholder(ColorDrawable(0xFFEEEEEE.toInt()))
                error(ColorDrawable(0xFFDDDDDD.toInt()))
            }
        } else {
            holder.ivThumb.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { onClick(row) }
        holder.itemView.setOnLongClickListener { onLongClick(row); true }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvModel: TextView = v.findViewById(R.id.tvModel)
        val tvBrand: TextView = v.findViewById(R.id.tvBrand)
        val tvNote: TextView = v.findViewById(R.id.tvNote)
        val tvPrice: TextView = v.findViewById(R.id.tvPrice)
        val tvSpec: TextView = v.findViewById(R.id.tvSpec)
        val ivThumb: ImageView = v.findViewById(R.id.ivThumb)
    }
}
