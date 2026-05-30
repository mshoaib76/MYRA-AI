package com.myra.assistant.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.model.ChatMessage

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 0.1f }
    private val targetHeights = FloatArray(barCount) { 0.1f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animating = false
    private var amplitude = 0f

    private val animRunnable = object : Runnable {
        override fun run() {
            for (i in 0 until barCount) {
                val target = if (animating) {
                    (targetHeights[i] * 0.7f + amplitude * (0.3f + (i % 5) * 0.1f)).coerceIn(0.05f, 1f)
                } else 0.1f
                targetHeights[i] = target
                barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f
            }
            invalidate()
            if (animating) postDelayed(this, 32)
        }
    }

    fun setAmplitude(rms: Float) {
        amplitude = rms.coerceIn(0f, 1f)
    }

    fun startAnimation() {
        animating = true
        removeCallbacks(animRunnable)
        post(animRunnable)
    }

    fun stopAnimation() {
        animating = false
        removeCallbacks(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barW = width.toFloat() / (barCount * 2)
        val gap = barW
        for (i in 0 until barCount) {
            val h = barHeights[i] * height
            val x = i * (barW + gap) + gap
            val alpha = (150 + barHeights[i] * 105).toInt()
            paint.color = Color.argb(alpha, 255, 23, 68)
            canvas.drawRoundRect(x, height - h, x + barW, height.toFloat(), barW / 2, barW / 2, paint)
        }
    }
}

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(msg: ChatMessage) {
        if (!msg.isUser && messages.lastOrNull()?.let { !it.isUser && it.text == msg.text } == true) return
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun lastMyraText(): String? =
        messages.lastOrNull { !it.isUser }?.text

    override fun getItemViewType(position: Int): Int =
        if (messages[position].isUser) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_chat_user else R.layout.item_chat_myra
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = messages[position].text
    }

    override fun getItemCount(): Int = messages.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.chatText)
    }
}
