package com.example.unzipfile.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.unzipfile.membership.ChartEntry

class SimpleBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var entries: List<ChartEntry> = emptyList()
    
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5E5CE6") // Noble Purple/Blue
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D1D1F")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    fun setEntries(newEntries: List<ChartEntry>) {
        this.entries = newEntries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 40f
        val bottomLabelHeight = 60f
        val chartHeight = height - padding - bottomLabelHeight
        
        val maxVal = if (entries.isEmpty()) 1.0f else (entries.maxOf { it.value.toDouble() }.toFloat()).coerceAtLeast(1.0f)
        val barWidth = (width - 2 * padding) / (entries.size * 2 - 1)
        
        entries.forEachIndexed { index, entry ->
            val x = padding + index * 2 * barWidth
            val barHeight = (entry.value / maxVal) * chartHeight
            
            // Draw Bar
            val rect = RectF(x, height - bottomLabelHeight - barHeight, x + barWidth, height - bottomLabelHeight)
            // Rounded top
            canvas.drawRoundRect(rect, 12f, 12f, barPaint)
            
            // Draw Label
            canvas.drawText(entry.label, x + barWidth / 2f, height - 10f, textPaint)
            
            // Draw Value (if not zero)
            if (entry.value > 0) {
                val valueStr = if (entry.value >= 1) "%.1f".format(entry.value) else "%.2f".format(entry.value)
                canvas.drawText(valueStr, x + barWidth / 2f, height - bottomLabelHeight - barHeight - 10f, valuePaint)
            }
        }
    }
}
