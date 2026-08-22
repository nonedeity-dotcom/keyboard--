package com.offline.ruenkeyboard

import android.inputmethodservice.Keyboard
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверяет саму логику свайпа на клавише пробела (без рендеринга):
 * что именно эту логику мы чинили после жалобы, что свайп не работал.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SwipeGestureTest {

    private fun buildLaidOutView(): HintKeyboardView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = HintKeyboardView(context, null)
        // The real KeyboardView base class touches the action listener from
        // its own onTouchEvent(); without one set it NPEs on the very first
        // dispatched event, which is what happened when this was missing.
        view.setOnKeyboardActionListener(object : android.inputmethodservice.KeyboardView.OnKeyboardActionListener {
            override fun onPress(primaryCode: Int) {}
            override fun onRelease(primaryCode: Int) {}
            override fun onKey(primaryCode: Int, keyCodes: IntArray?) {}
            override fun onText(text: CharSequence?) {}
            override fun swipeLeft() {}
            override fun swipeRight() {}
            override fun swipeDown() {}
            override fun swipeUp() {}
        })
        view.keyboard = Keyboard(context, R.xml.keyboard_ru)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun spaceKeyBounds(view: HintKeyboardView): Keyboard.Key {
        return view.keyboard!!.keys.first { it.codes.isNotEmpty() && it.codes[0] == 32 }
    }

    // Печатаем полный стектрейс перед тем, как дать исключению всплыть —
    // короткая сводка Gradle по умолчанию ("NPE at file:line") недостаточна,
    // чтобы понять причину падения.
    private fun dispatch(view: HintKeyboardView, event: MotionEvent) {
        try {
            view.dispatchTouchEvent(event)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun horizontalDragOnSpaceKeyTriggersLanguageSwitch() {
        val view = buildLaidOutView()
        var switchedCount = 0
        view.onSwipeLanguage = { switchedCount++ }

        val space = spaceKeyBounds(view)
        val y = (space.y + space.height / 2).toFloat()
        val downX = (space.x + space.width * 0.8f)
        val movedX = (space.x + space.width * 0.1f)

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, downX, y, 0)
        dispatch(view, down)
        down.recycle()

        val move = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_MOVE, movedX, y, 0)
        dispatch(view, move)
        move.recycle()

        assertTrue("swipe on the space key must trigger the language switch callback", switchedCount == 1)
    }

    @Test
    fun smallTapOnSpaceKeyDoesNotTriggerLanguageSwitch() {
        val view = buildLaidOutView()
        var switched = false
        view.onSwipeLanguage = { switched = true }

        val space = spaceKeyBounds(view)
        val x = (space.x + space.width / 2).toFloat()
        val y = (space.y + space.height / 2).toFloat()

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        dispatch(view, down)
        down.recycle()

        // Небольшое дрожание пальца, не должно расцениваться как свайп.
        val move = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, x + 5f, y, 0)
        dispatch(view, move)
        move.recycle()

        val up = MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, x + 5f, y, 0)
        dispatch(view, up)
        up.recycle()

        assertFalse("a small tap must not trigger the language switch", switched)
    }

    @Test
    fun horizontalDragOutsideSpaceKeyDoesNotTriggerLanguageSwitch() {
        val view = buildLaidOutView()
        var switched = false
        view.onSwipeLanguage = { switched = true }

        // Верхний ряд букв, не пробел — свайп здесь не должен переключать язык.
        val letterKey = view.keyboard!!.keys.first { it.codes.isNotEmpty() && it.codes[0] == 1081 }
        val y = (letterKey.y + letterKey.height / 2).toFloat()
        val downX = letterKey.x.toFloat()
        val movedX = downX + 300f

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, downX, y, 0)
        dispatch(view, down)
        down.recycle()

        val move = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_MOVE, movedX, y, 0)
        dispatch(view, move)
        move.recycle()

        assertFalse("a swipe that doesn't start on the space key must not switch language", switched)
    }
}
