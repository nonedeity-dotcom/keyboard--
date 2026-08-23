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
    // стоковый попап требует попадать пальцем ровно в свою строку и не
    // подтверждает выбор при отпускании (нужен отдельный крестик), тогда как
    // референсная клавиатура ведёт выбор по ближайшей ячейке и вставляет
    // подсвеченный символ ровно в момент, когда палец отрывают от экрана.
    private var actionListener: OnKeyboardActionListener? = null
    private var popupWindow: PopupWindow? = null
    private var popupCells: List<TextView> = emptyList()
    private var popupChars: String = ""
    private var popupGrid: PopupGrid? = null
    private var popupSelectedIndex: Int = -1

    private val popupCellSizePx = 44f * density
    private val popupGapPx = 6f * density

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

    /**
     * Попап живёт в отдельном окне, поэтому его обязательно нужно закрыть,
     * когда сама клавиатура уходит с экрана: иначе окно остаётся висеть без
     * валидного токена (IME пересоздаёт свой View при каждом показе).
     */
    override fun onDetachedFromWindow() {
        dismissPopup()
        super.onDetachedFromWindow()
    }

    private fun showCharacterPopup(key: Keyboard.Key, chars: String) {
        dismissPopup()

        // Координаты клавиши идут от области контента, поэтому добавляем
        // паддинги View — ровно так же, как это делает сам KeyboardView.
        val grid = PopupGrid.forCharacters(
            chars = chars,
            keyCenterX = key.x + paddingLeft + key.width / 2f,
            keyTop = (key.y + paddingTop).toFloat(),
            cellSize = popupCellSizePx,
            gap = popupGapPx,
            viewWidth = width
        )
        popupGrid = grid
        popupChars = chars

        val cellPx = popupCellSizePx.toInt()
        val gridView = GridLayout(context).apply {
            columnCount = grid.columns
            rowCount = grid.rows
            setBackgroundResource(R.drawable.popup_bg)
        }
        val cells = chars.map { ch ->
            TextView(context).apply {
                text = ch.toString()
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.key_text))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellPx
                    height = cellPx
                }
            }
        }
        cells.forEach { gridView.addView(it) }
        popupCells = cells

        popupSelectedIndex = -1
        // Палец ещё не двигали — подсвечиваем то, что под ним прямо сейчас.
        updatePopupSelection(
            key.x + paddingLeft + key.width / 2f,
            key.y + paddingTop + key.height / 2f
        )

        // ВАЖНО: именно getLocationInWindow, а не getLocationOnScreen.
        // PopupWindow добавляется как дочернее окно (TYPE_APPLICATION_PANEL) с
        // токеном родителя, и его x/y отсчитываются от РОДИТЕЛЬСКОГО окна.
        // Экранные координаты сдвигают попап вниз на высоту всего, что выше
        // окна клавиатуры, — то есть просто за пределы экрана, и долгое
        // нажатие выглядит как «ничего не происходит». Сам KeyboardView в
        // AOSP по этой же причине берёт getLocationInWindow.
        val location = IntArray(2)
        getLocationInWindow(location)
        val offsetX = (location[0] + grid.left).toInt()
        val offsetY = (location[1] + grid.top).toInt()
        popupOffsetInWindow = offsetX to offsetY

        popupWindow = PopupWindow(gridView, grid.width.toInt(), grid.height.toInt(), false).apply {
            isTouchable = false
            // Попап рисуется над клавишей, то есть выше окна IME — без этого
            // система прижала бы его обратно внутрь клавиатуры.
            isClippingEnabled = false
            showAtLocation(this@HintKeyboardView, Gravity.NO_GRAVITY, offsetX, offsetY)
        }
    }

    // --- Точки наблюдения для тестов ---

    internal val isAlternatesPopupShowing: Boolean get() = popupWindow != null
    internal val alternatesGrid: PopupGrid? get() = popupGrid
    internal val highlightedAlternate: Char?
        get() = popupChars.getOrNull(popupSelectedIndex)
    internal var popupOffsetInWindow: Pair<Int, Int>? = null
        private set

    private fun updatePopupSelection(touchX: Float, touchY: Float) {
        val grid = popupGrid ?: return
        val index = grid.indexAt(touchX, touchY)
        if (index == popupSelectedIndex) return
        popupCells.getOrNull(popupSelectedIndex)?.setBackgroundResource(0)
        popupCells.getOrNull(index)?.setBackgroundResource(R.drawable.popup_cell_selected)
        popupSelectedIndex = index
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
        popupGrid = null
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

                // Акцентный прямоугольник закрашивает то, что уже нарисовал
                // KeyboardView, поэтому содержимое клавиши рисуем поверх сами.
                val icon = key.icon
                if (icon != null) {
                    val left = (rect.centerX() - icon.intrinsicWidth / 2f).toInt()
                    val top = (rect.centerY() - icon.intrinsicHeight / 2f).toInt()
                    icon.setBounds(left, top, left + icon.intrinsicWidth, top + icon.intrinsicHeight)
                    icon.draw(canvas)
                    continue
                }
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
                MotionEvent.ACTION_MOVE -> updatePopupSelection(event.x, event.y)
                MotionEvent.ACTION_UP -> {
                    updatePopupSelection(event.x, event.y)
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
                MotionEvent.ACTION_DOWN -> {
                    // Новое касание при висящем попапе означает, что прошлый
                    // жест оборвался мимо нас: закрываем попап и отдаём
                    // событие обычному разбору нажатий.
                    dismissPopup()
                    return super.onTouchEvent(event)
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
