package com.example.unzipfile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unzipfile.databinding.ItemFileSimpleBinding

class SelectedFilesAdapter(
    private val files: MutableList<String>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<SelectedFilesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFileSimpleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val path = files[position]
        holder.binding.tvFileName.text = path.substringAfterLast("/")
        holder.binding.btnRemove.setOnClickListener {
            onRemove(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = files.size
}
