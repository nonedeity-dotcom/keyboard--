package com.offline.ruenkeyboard

import android.app.Activity
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Сквозная проверка долгого нажатия: касание → попап → выбор → вставка.
 *
 * Пользователь дважды сообщал, что «при зажатии ничего не происходит», и оба
 * раза причина была в разных местах этого пути. Поэтому проверяем весь путь
 * целиком, а не отдельные куски.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LongPressPopupTest {

    private val committed = mutableListOf<Int>()

    private fun buildView(): HintKeyboardView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        // Инфлейтим настоящий layout: часть атрибутов KeyboardView (в том числе
        // popupLayout, без которого фреймворк даже не вызовет onLongPress)
        // приходит из XML и темы, а не из кода.
        val root = LayoutInflater.from(activity).inflate(R.layout.keyboard_container, null)
        val view = root.findViewById<HintKeyboardView>(R.id.keyboard_view)
        view.keyboard = Keyboard(activity, R.xml.keyboard_ru)
        view.setOnKeyboardActionListener(object : KeyboardView.OnKeyboardActionListener {
            override fun onPress(primaryCode: Int) {}
            override fun onRelease(primaryCode: Int) {}
            override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
                committed.add(primaryCode)
            }
            override fun onText(text: CharSequence?) {}
            override fun swipeLeft() {}
            override fun swipeRight() {}
            override fun swipeDown() {}
            override fun swipeUp() {}
        })
        activity.addContentView(
            root,
            ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        shadowOf(Looper.getMainLooper()).idle()
        return view
    }

    private fun keyWithCode(view: HintKeyboardView, code: Int): Keyboard.Key =
        view.keyboard.keys.first { it.codes.firstOrNull() == code }

    private fun touch(view: HintKeyboardView, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.dispatchTouchEvent(event)
        // Событие ACTION_DOWN фреймворк кладёт в отложенное сообщение
        // долгого нажатия, поэтому recycle() здесь делать нельзя.
    }

    private fun holdPastLongPress() {
        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 100L))
    }

    /** Зажимаем клавишу и держим — попап обязан открыться. */
    private fun openPopupOn(view: HintKeyboardView, key: Keyboard.Key) {
        touch(view, MotionEvent.ACTION_DOWN, (key.x + key.width / 2).toFloat(), (key.y + key.height / 2).toFloat())
        holdPastLongPress()
        assertTrue(
            "long-pressing '${key.label}' must open the alternates popup",
            view.isAlternatesPopupShowing
        )
    }

    @Test
    fun longPressOpensThePopupAtAll() {
        val view = buildView()
        openPopupOn(view, keyWithCode(view, 44)) // запятая
        assertNotNull(view.alternatesGrid)
        assertNotNull("something must be highlighted straight away", view.highlightedAlternate)
    }

    @Test
    fun releasingCommitsTheHighlightedCharacterAndClosesThePopup() {
        val view = buildView()
        val key = keyWithCode(view, 44)
        openPopupOn(view, key)
        val grid = view.alternatesGrid!!

        // Ведём палец в первую ячейку верхнего ряда и отпускаем.
        val x = grid.left + grid.cellSize / 2f
        val y = grid.top + grid.cellSize / 2f
        touch(view, MotionEvent.ACTION_MOVE, x, y)
        assertEquals('&', view.highlightedAlternate)
        touch(view, MotionEvent.ACTION_UP, x, y)

        assertEquals("released finger must insert the highlighted character", listOf('&'.code), committed)
        assertTrue("popup must close by itself on release", !view.isAlternatesPopupShowing)
    }

    /**
     * Регрессия: второй ряд попапа был недостижим, потому что символ
     * выбирался только по координате X.
     */
    @Test
    fun theSecondRowOfTheCommaPopupCanActuallyBeSelected() {
        val view = buildView()
        val key = keyWithCode(view, 44)
        val chars = key.popupCharacters.toString()
        openPopupOn(view, key)
        val grid = view.alternatesGrid!!
        assertTrue("the comma popup is expected to wrap onto more than one row", grid.rows >= 2)

        // Ожидаемый символ выводим из самой сетки, а не зашиваем: количество
        // колонок зависит от ширины экрана.
        val expected = chars[grid.columns]
        val x = grid.left + grid.cellSize / 2f
        val y = grid.top + grid.cellSize * 1.5f // середина второго ряда
        touch(view, MotionEvent.ACTION_MOVE, x, y)
        assertEquals(expected, view.highlightedAlternate)
        touch(view, MotionEvent.ACTION_UP, x, y)

        assertEquals(listOf(expected.code), committed)
    }

    /** Палец остаётся на клавише, ниже попапа — выбор не должен теряться. */
    @Test
    fun keepingTheFingerOnTheKeyStillTracksASelection() {
        val view = buildView()
        val key = keyWithCode(view, 44)
        val chars = key.popupCharacters.toString()
        openPopupOn(view, key)
        val grid = view.alternatesGrid!!

        val onTheKeyY = grid.top + grid.height + 40f
        touch(view, MotionEvent.ACTION_MOVE, grid.left + grid.cellSize / 2f, onTheKeyY)
        val expected = chars[(grid.rows - 1) * grid.columns]
        assertEquals("finger below the popup selects the bottom row", expected, view.highlightedAlternate)
    }

    @Test
    fun aShortTapDoesNotOpenThePopupAndTypesTheKeyItself() {
        val view = buildView()
        val key = keyWithCode(view, 1081) // й
        val x = (key.x + key.width / 2).toFloat()
        val y = (key.y + key.height / 2).toFloat()
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("a short tap must not open a popup", !view.isAlternatesPopupShowing)
        assertEquals(listOf(1081), committed)
    }

    @Test
    fun popupIsPositionedNextToItsKeyRatherThanFarOutsideTheKeyboard() {
        val view = buildView()
        val key = keyWithCode(view, 44)
        openPopupOn(view, key)
        val (offsetX, offsetY) = view.popupOffsetInWindow!!
        val location = IntArray(2)
        view.getLocationInWindow(location)

        // Попап встаёт над своей клавишей: чуть выше её верхней грани.
        val keyTopInWindow = location[1] + key.y + view.paddingTop
        assertTrue(
            "popup y=$offsetY must sit above the key (top=$keyTopInWindow), not below it",
            offsetY < keyTopInWindow
        )
        assertTrue("popup x=$offsetX must stay on screen", offsetX >= location[0])
        assertTrue(
            "popup must not run off the right edge",
            offsetX + view.alternatesGrid!!.width <= location[0] + view.width
        )
    }
}
