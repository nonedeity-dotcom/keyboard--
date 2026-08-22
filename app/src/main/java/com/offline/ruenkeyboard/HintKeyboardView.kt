package com.offline.ruenkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil

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

    // Собственный попап долгого нажатия вместо стокового KeyboardView-механизма:
    // у стокового попапа выбор символа обрывается, если палец уходит за пределы
    // строго той же строки, и он не подтверждает/закрывается сам при отпускании
    // (нужен отдельный крестик) — у современных клавиатур (например, Emoji
    // Keyboard) выбор свободный по всей ширине попапа и подтверждается прямо
    // при отпускании пальца.
    private var actionListener: OnKeyboardActionListener? = null
    private var popupWindow: PopupWindow? = null
    private var popupCells: List<TextView> = emptyList()
    private var popupChars: String = ""
    private var popupSelectedIndex: Int = -1
    private var popupLeftPx = 0f
    private var popupCellWidthPx = 1f
    private val popupMaxColumns = 8
    private val popupCellSizePx = (44f * density).toInt()

    override fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        actionListener = listener
        super.setOnKeyboardActionListener(listener)
    }

    override fun onLongPress(popupKey: Keyboard.Key): Boolean {
        val chars = popupKey.popupCharacters
        if (chars.isNullOrEmpty()) return super.onLongPress(popupKey)
        showCharacterPopup(popupKey, chars.toString())
        return true
    }

    private fun showCharacterPopup(key: Keyboard.Key, chars: String) {
        dismissPopup()
        popupChars = chars

        val columns = minOf(chars.length, popupMaxColumns)
        val rows = ceil(chars.length / columns.toDouble()).toInt()
        val popupWidth = popupCellSizePx * columns
        val popupHeight = popupCellSizePx * rows

        val grid = GridLayout(context).apply {
            columnCount = columns
            rowCount = rows
            setBackgroundColor(context.getColor(R.color.key_bg_special))
        }
        val cells = mutableListOf<TextView>()
        for (ch in chars) {
            val cell = TextView(context).apply {
                text = ch.toString()
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.key_text))
                setBackgroundColor(context.getColor(R.color.key_bg_special))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = popupCellSizePx
                    height = popupCellSizePx
                }
            }
            cells.add(cell)
            grid.addView(cell)
        }
        popupCells = cells

        popupLeftPx = (key.x + key.width / 2f - popupWidth / 2f)
            .coerceIn(0f, (width - popupWidth).coerceAtLeast(0).toFloat())
        popupCellWidthPx = popupWidth / columns.toFloat()

        popupSelectedIndex = -1
        updatePopupSelection(key.x + key.width / 2f)

        val location = IntArray(2)
        getLocationInWindow(location)
        val popupY = location[1] + key.y - popupHeight - (4f * density).toInt()

        val pw = PopupWindow(grid, popupWidth, popupHeight, false)
        pw.isTouchable = false
        pw.showAtLocation(this, Gravity.NO_GRAVITY, (location[0] + popupLeftPx).toInt(), popupY)
        popupWindow = pw
    }

    private fun updatePopupSelection(touchX: Float) {
        if (popupChars.isEmpty()) return
        val index = (((touchX - popupLeftPx) / popupCellWidthPx).toInt())
            .coerceIn(0, popupChars.length - 1)
        if (index != popupSelectedIndex) {
            popupCells.getOrNull(popupSelectedIndex)?.setBackgroundColor(context.getColor(R.color.key_bg_special))
            popupCells.getOrNull(index)?.setBackgroundColor(context.getColor(R.color.key_bg_accent))
            popupSelectedIndex = index
        }
    }

    private fun commitPopupSelection() {
        val index = popupSelectedIndex
        if (index in popupChars.indices) {
            actionListener?.onKey(popupChars[index].code, null)
        }
        dismissPopup()
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
        popupCells = emptyList()
        popupChars = ""
        popupSelectedIndex = -1
    }

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
        if (popupWindow != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> updatePopupSelection(event.x)
                MotionEvent.ACTION_UP -> {
                    commitPopupSelection()
                    val cancelEvent = MotionEvent.obtain(event)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                }
                MotionEvent.ACTION_CANCEL -> {
                    dismissPopup()
                    super.onTouchEvent(event)
                }
            }
            return true
        }

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
