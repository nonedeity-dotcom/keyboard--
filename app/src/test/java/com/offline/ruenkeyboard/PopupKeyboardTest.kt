package com.offline.ruenkeyboard

import android.inputmethodservice.Keyboard
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверяет саму причину бага "долгое нажатие ничего не делает": у клавиш
 * с popupCharacters должен быть ненулевой popupResId (из android:popupKeyboard),
 * иначе KeyboardView.onLongPress() возвращает false, даже не пытаясь открыть попап.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PopupKeyboardTest {

    private fun keysWithPopupCharacters(xmlRes: Int): List<Keyboard.Key> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyboard = Keyboard(context, xmlRes)
        return keyboard.keys.filter { !it.popupCharacters.isNullOrEmpty() }
    }

    @Test
    fun everyRuKeyWithPopupCharactersHasAPopupKeyboardResource() {
        val keys = keysWithPopupCharacters(R.xml.keyboard_ru)
        assertNotEquals("expected keys with popupCharacters in keyboard_ru.xml", 0, keys.size)
        keys.forEach { key ->
            assertNotEquals(
                "key '${key.label}' has popupCharacters but no popupKeyboard resource, so long-press would silently do nothing",
                0, key.popupResId
            )
        }
    }

    @Test
    fun everyEnKeyWithPopupCharactersHasAPopupKeyboardResource() {
        val keys = keysWithPopupCharacters(R.xml.keyboard_en)
        assertNotEquals("expected keys with popupCharacters in keyboard_en.xml", 0, keys.size)
        keys.forEach { key ->
            assertNotEquals(
                "key '${key.label}' has popupCharacters but no popupKeyboard resource, so long-press would silently do nothing",
                0, key.popupResId
            )
        }
    }
}
