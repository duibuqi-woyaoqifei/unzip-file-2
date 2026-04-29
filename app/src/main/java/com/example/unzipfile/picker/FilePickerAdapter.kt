package com.example.unzipfile.picker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unzipfile.R
import com.example.unzipfile.databinding.ItemFileBinding
import java.text.SimpleDateFormat
import java.util.*

class FilePickerAdapter(
    private val onFileClick: (FileItem) -> Unit,
    private val onSelectionChanged: (FileItem) -> Unit
) : ListAdapter<FileItem, FilePickerAdapter.ViewHolder>(FileDiffCallback()) {

    var isMultiSelect = true
    var selectType = 0 // same constants as FilePickerActivity

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FileItem) {
            binding.tvName.text = item.name
            
            if (item.isDirectory) {
                binding.ivIcon.setImageResource(R.drawable.ic_folder)
                binding.tvInfo.text = "文件夹"
            } else {
                binding.ivIcon.setImageResource(R.drawable.ic_file)
                binding.tvInfo.text = "${formatSize(item.size)} | ${formatDate(item.lastModified)}"
            }

            val canSelect = when (selectType) {
                0 -> !item.isDirectory // SELECT_TYPE_FILE
                1 -> item.isDirectory  // SELECT_TYPE_FOLDER
                else -> true            // SELECT_TYPE_BOTH
            }

            binding.cbSelect.visibility = if (canSelect) android.view.View.VISIBLE else android.view.View.GONE
            binding.cbSelect.isChecked = item.isSelected
            
            binding.root.setOnClickListener {
                if (item.isDirectory) {
                    onFileClick(item)
                } else {
                    if (canSelect) {
                        item.isSelected = !item.isSelected
                        binding.cbSelect.isChecked = item.isSelected
                        onSelectionChanged(item)
                    }
                }
            }
            
            binding.cbSelect.setOnClickListener {
                item.isSelected = binding.cbSelect.isChecked
                onSelectionChanged(item)
            }
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.isSelected == newItem.isSelected && oldItem.lastModified == newItem.lastModified
        }
    }
}
