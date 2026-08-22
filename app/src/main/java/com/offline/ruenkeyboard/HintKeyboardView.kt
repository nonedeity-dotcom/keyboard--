package com.offline.ruenkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs

/**
 * KeyboardView с цифровыми подсказками в углах клавиш (как у Gboard)
 * и акцентным цветом клавиши Enter — стандартный KeyboardView рисует
 * только один фон и одну подпись на клавишу, этого не хватает для
 * визуального соответствия макету.
 */
class HintKeyboardView(context: Context, attrs: AttributeSet?) : KeyboardView(context, attrs) {

    var hints: Map<Int, String> = emptyMap()
    var accentCode: Int? = null
    var onSwipeLanguage: (() -> Unit)? = null

    private val swipeThresholdPx = 45f * context.resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var downOnSpaceKey = false
    private var swipeFired = false

    private val density = context.resources.displayMetrics.density

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_hint)
        textSize = 12f * density
        textAlign = Paint.Align.RIGHT
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_bg_accent)
    }

    private val accentLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_text)
        textSize = 26f * density
        textAlign = Paint.Align.CENTER
    }

    private val gapPx = 1.5f * density
    private val cornerRadiusPx = 10f * density
    private val hintMarginPx = 8f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kb = keyboard ?: return

        val accent = accentCode
        if (accent != null) {
            for (key in kb.keys) {
                val code = key.codes.firstOrNull() ?: continue
                if (code != accent) continue
                val rect = RectF(
                    key.x + gapPx,
                    key.y + gapPx,
                    (key.x + key.width - gapPx),
                    (key.y + key.height - gapPx)
                )
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, accentPaint)
                val label = key.label ?: continue
                val textY = rect.centerY() - (accentLabelPaint.descent() + accentLabelPaint.ascent()) / 2f
                canvas.drawText(label.toString(), rect.centerX(), textY, accentLabelPaint)
            }
        }

        if (hints.isEmpty()) return
        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: continue
            val hint = hints[code] ?: continue
            val x = key.x + key.width - hintMarginPx
            val y = key.y + hintMarginPx + hintPaint.textSize
            canvas.drawText(hint, x, y, hintPaint)
        }
    }

    /**
     * Свайп по пробелу для смены языка отслеживаем сами: встроенный
     * KeyboardView.swipeLeft/swipeRight ненадёжен на многих устройствах.
     * При обнаружении свайпа шлём в super ACTION_CANCEL, чтобы не сработал
     * ещё и обычный тап по клавише пробела/соседним клавишам.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downOnSpaceKey = isOnSpaceKey(event.x, event.y)
                swipeFired = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (downOnSpaceKey && !swipeFired) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > swipeThresholdPx && abs(dx) > abs(dy) * 1.5f) {
                        swipeFired = true
                        onSwipeLanguage?.invoke()
                        val cancelEvent = MotionEvent.obtain(event)
                        cancelEvent.action = MotionEvent.ACTION_CANCEL
                        super.onTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeFired) {
                    swipeFired = false
                    downOnSpaceKey = false
                    return true
                }
            }
        }
        if (swipeFired) return true
        return super.onTouchEvent(event)
    }

    private fun isOnSpaceKey(x: Float, y: Float): Boolean {
        val kb = keyboard ?: return false
        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: continue
            if (code == 32 && key.isInside(x.toInt(), y.toInt())) {
                return true
            }
        }
        return false
    }
}
