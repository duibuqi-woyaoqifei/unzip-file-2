package com.example.unzipfile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.unzipfile.databinding.ItemAdBinding

data class AdItem(
    val imageUrl: String,
    val linkUrl: String
)

data class AdConfig(
    val ads: List<AdItem>
)

class AdAdapter(private val context: Context, private val items: List<AdItem>) :
    RecyclerView.Adapter<AdAdapter.AdViewHolder>() {

    inner class AdViewHolder(val binding: ItemAdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdViewHolder {
        val binding = ItemAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
        val item = items[position]
        holder.binding.ivAd.load(item.imageUrl) {
            crossfade(true)
            placeholder(android.R.drawable.progress_indeterminate_horizontal)
        }
        holder.binding.root.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.linkUrl))
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = items.size
}
