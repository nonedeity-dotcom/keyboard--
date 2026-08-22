package com.offline.ruenkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * KeyboardView с цифровыми подсказками в углах клавиш (как у Gboard)
 * и акцентным цветом клавиши Enter — стандартный KeyboardView рисует
 * только один фон и одну подпись на клавишу, этого не хватает для
 * визуального соответствия макету.
 */
class HintKeyboardView(context: Context, attrs: AttributeSet?) : KeyboardView(context, attrs) {

    var hints: Map<Int, String> = emptyMap()
    var accentCode: Int? = null

    private val density = context.resources.displayMetrics.density

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_hint)
        textSize = 11f * density
        textAlign = Paint.Align.RIGHT
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_bg_accent)
    }

    private val accentLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_text)
        textSize = 20f * density
        textAlign = Paint.Align.CENTER
    }

    private val gapPx = 3f * density
    private val cornerRadiusPx = 8f * density
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
}
