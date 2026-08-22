package com.offline.ruenkeyboard

import android.inputmethodservice.Keyboard
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepeatableDeleteTest {

    private fun deleteKey(xmlRes: Int): Keyboard.Key {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyboard = Keyboard(context, xmlRes)
        return keyboard.keys.first { it.codes.firstOrNull() == RuEnKeyboardService.CODE_DELETE }
    }

    @Test
    fun deleteKeyIsRepeatableOnAllLayouts() {
        listOf(
            R.xml.keyboard_ru,
            R.xml.keyboard_en,
            R.xml.keyboard_symbols,
            R.xml.keyboard_symbols2
        ).forEach { xmlRes ->
            assertTrue(
                "backspace key in layout $xmlRes must be repeatable so holding it deletes repeatedly",
                deleteKey(xmlRes).repeatable
            )
        }
    }
}
