package com.example.unzipfile.membership

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unzipfile.R
import com.example.unzipfile.databinding.ActivityRewardStatsBinding
import com.example.unzipfile.databinding.ItemRewardRecordBinding
import java.text.SimpleDateFormat
import java.util.*

class RewardStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRewardStatsBinding
    private lateinit var statsManager: RewardStatsManager
    private var currentFilter = 0 // 0: Day, 1: Month, 2: Year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRewardStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statsManager = RewardStatsManager.getInstance(this)

        setupToolbar()
        setupListeners()
        refreshUI()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.toggleGroupTime.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilter = when (checkedId) {
                    R.id.btnDay -> 0
                    R.id.btnMonth -> 1
                    R.id.btnYear -> 2
                    else -> 0
                }
                updateChart()
            }
        }
    }

    private fun refreshUI() {
        val allRecords = statsManager.getAllRecords()
        
        // 1. Summary
        val totalDays = allRecords.sumOf { it.daysEarned.toDouble() }.toFloat()
        binding.tvTotalLifetime.text = "%.1f 天".format(totalDays)

        val statsByType = statsManager.getStatsByType()
        binding.tvInstallTotal.text = "%.1f 天".format(statsByType[RewardType.INSTALL] ?: 0f)
        binding.tvPurchaseTotal.text = "%.1f 天".format(statsByType[RewardType.PURCHASE] ?: 0f)
        binding.tvAdTotal.text = "%.2f 天".format(statsByType[RewardType.AD] ?: 0f)
        binding.tvGiftTotal.text = "%.1f 天".format(statsByType[RewardType.GIFT] ?: 0f)

        // 2. Chart
        updateChart()

        // 3. Records List
        setupRecordsList(allRecords.sortedByDescending { it.timestamp })
    }

    private fun updateChart() {
        val chartData = statsManager.getChartData(currentFilter)
        binding.barChart.setEntries(chartData)
    }

    private fun setupRecordsList(records: List<RewardRecord>) {
        binding.rvRewardRecords.layoutManager = LinearLayoutManager(this)
        binding.rvRewardRecords.adapter = RecordsAdapter(records)
    }

    inner class RecordsAdapter(private val records: List<RewardRecord>) :
        RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemRewardRecordBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRewardRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = records[position]
            holder.binding.tvRewardDesc.text = record.description
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.binding.tvRewardTime.text = sdf.format(Date(record.timestamp))
            
            val sign = if (record.daysEarned >= 0) "+" else ""
            val valueStr = if (record.daysEarned >= 1) "%.1f".format(record.daysEarned) else "%.2f".format(record.daysEarned)
            holder.binding.tvRewardDays.text = "$sign$valueStr 天"

            // Set color and icon based on type
            when (record.type) {
                RewardType.INSTALL -> {
                    holder.binding.ivRewardType.setImageResource(R.drawable.ic_membership)
                    holder.binding.ivRewardType.setColorFilter(Color.parseColor("#5856D6"))
                }
                RewardType.PURCHASE -> {
                    holder.binding.ivRewardType.setImageResource(R.drawable.ic_gift)
                    holder.binding.ivRewardType.setColorFilter(Color.parseColor("#FF9500"))
                }
                RewardType.AD -> {
                    holder.binding.ivRewardType.setImageResource(R.drawable.ic_watch)
                    holder.binding.ivRewardType.setColorFilter(Color.parseColor("#34C759"))
                }
                RewardType.GIFT -> {
                    holder.binding.ivRewardType.setImageResource(R.drawable.ic_gift)
                    holder.binding.ivRewardType.setColorFilter(Color.parseColor("#FF2D55"))
                }
            }
        }

        override fun getItemCount() = records.size
    }
}
