package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState { IDLE, LISTENING, SPEAKING, THINKING, ACTIVE }

class OrbAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state = OrbState.IDLE
    private var pulseScale = 1f
    private var glowAlpha = 180
    private var rotationAngle = 0f
    private var waveOffset = 0f
    private var amplitude = 0.3f
    private var thinkingAngle = 0f

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pulseAnimator = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseScale = it.animatedValue as Float
            invalidate()
        }
    }

    private val glowAnimator = ValueAnimator.ofInt(120, 220, 120).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            glowAlpha = it.animatedValue as Int
            invalidate()
        }
    }

    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            invalidate()
        }
    }

    private val waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            waveOffset = it.animatedValue as Float
            invalidate()
        }
    }

    private val thinkingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            thinkingAngle = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        pulseAnimator.start()
        glowAnimator.start()
        rotationAnimator.start()
        waveAnimator.start()
    }

    fun setState(newState: OrbState) {
        state = newState
        if (newState == OrbState.THINKING) thinkingAnimator.start() else thinkingAnimator.cancel()
        invalidate()
    }

    fun setAmplitude(amp: Float) {
        amplitude = amp.coerceIn(0.1f, 1f)
    }

    private fun colors(): Pair<Int, Int> = when (state) {
        OrbState.SPEAKING -> Color.parseColor("#E040FB") to Color.parseColor("#FF1744")
        OrbState.THINKING -> Color.parseColor("#40C4FF") to Color.parseColor("#00B0FF")
        OrbState.IDLE -> Color.parseColor("#B71C1C") to Color.parseColor("#880E4F")
        else -> Color.parseColor("#FF1744") to Color.parseColor("#D500F9")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = (minOf(width, height) / 2f) * 0.38f * pulseScale
        val (c1, c2) = colors()

        glowPaint.shader = RadialGradient(cx, cy, baseR * 1.6f, c1, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        glowPaint.alpha = glowAlpha
        canvas.drawCircle(cx, cy, baseR * 1.6f, glowPaint)

        corePaint.shader = RadialGradient(cx - baseR * 0.2f, cy - baseR * 0.2f, baseR * 1.2f, c2, c1, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, baseR, corePaint)

        if (state != OrbState.IDLE) {
            for (i in 0 until 3) {
                ringPaint.color = Color.argb(100 + i * 40, Color.red(c1), Color.green(c1), Color.blue(c1))
                val rect = RectF(cx - baseR * (1.1f + i * 0.12f), cy - baseR * (1.1f + i * 0.12f),
                    cx + baseR * (1.1f + i * 0.12f), cy + baseR * (1.1f + i * 0.12f))
                canvas.save()
                canvas.rotate(rotationAngle + i * 30f, cx, cy)
                canvas.drawArc(rect, 0f, 200f, false, ringPaint)
                canvas.restore()
            }
        }

        if (state == OrbState.LISTENING || state == OrbState.SPEAKING || state == OrbState.ACTIVE) {
            wavePaint.color = c1
            for (w in 0 until 2) {
                val waveR = baseR * (1.25f + w * 0.15f) + sin(Math.toRadians((waveOffset + w * 90).toDouble())).toFloat() * 8f * amplitude
                canvas.drawCircle(cx, cy, waveR, wavePaint)
            }
        }

        if (state == OrbState.THINKING) {
            val arcRect = RectF(cx - baseR * 0.7f, cy - baseR * 0.7f, cx + baseR * 0.7f, cy + baseR * 0.7f)
            ringPaint.color = c1
            ringPaint.pathEffect = null
            canvas.save()
            canvas.rotate(thinkingAngle, cx, cy)
            canvas.drawArc(arcRect, 0f, 120f, false, ringPaint)
            canvas.rotate(180f, cx, cy)
            canvas.drawArc(arcRect, 0f, 90f, false, ringPaint)
            canvas.restore()
            ringPaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

        if (state == OrbState.ACTIVE || state == OrbState.SPEAKING) {
            particlePaint.color = c2
            for (i in 0 until 12) {
                val angle = Math.toRadians((rotationAngle * 2 + i * 30).toDouble())
                val pr = baseR * (1.35f + amplitude * 0.2f)
                val px = cx + cos(angle).toFloat() * pr
                val py = cy + sin(angle).toFloat() * pr
                canvas.drawCircle(px, py, 4f + amplitude * 3f, particlePaint)
            }
        }

        highlightPaint.shader = RadialGradient(cx - baseR * 0.3f, cy - baseR * 0.3f, baseR * 0.5f,
            Color.argb(120, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx - baseR * 0.25f, cy - baseR * 0.25f, baseR * 0.35f, highlightPaint)
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        glowAnimator.cancel()
        rotationAnimator.cancel()
        waveAnimator.cancel()
        thinkingAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
