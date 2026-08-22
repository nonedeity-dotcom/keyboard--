package com.offline.ruenkeyboard

import android.inputmethodservice.Keyboard
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyLabelsTest {

    @Test
    fun lettersFollowTheShiftState() {
        assertEquals("й", labelForShiftState("й", isLetterMode = true, shifted = false))
        assertEquals("Й", labelForShiftState("й", isLetterMode = true, shifted = true))
        assertEquals("a", labelForShiftState("a", isLetterMode = true, shifted = false))
        assertEquals("A", labelForShiftState("a", isLetterMode = true, shifted = true))
    }

    @Test
    fun symbolPageLabelsAreNeverRecased() {
        // Δ и π — подписи на странице символов, а не буквы под шифтом.
        assertEquals("Δ", labelForShiftState("Δ", isLetterMode = false, shifted = false))
        assertEquals("Δ", labelForShiftState("Δ", isLetterMode = false, shifted = true))
        assertEquals("π", labelForShiftState("π", isLetterMode = false, shifted = true))
    }

    @Test
    fun multiCharacterLabelsAreLeftAlone() {
        assertEquals("Русский", labelForShiftState("Русский", isLetterMode = true, shifted = true))
        assertEquals("?123", labelForShiftState("?123", isLetterMode = true, shifted = true))
    }

    @Test
    fun nullAndNonLetterLabelsSurviveUntouched() {
        assertEquals(null, labelForShiftState(null, isLetterMode = true, shifted = true))
        assertEquals("1", labelForShiftState("1", isLetterMode = true, shifted = true))
        assertEquals("@", labelForShiftState("@", isLetterMode = true, shifted = true))
    }

    /**
     * Общий инвариант: то, что клавиша показывает, и то, что она вводит, —
     * один и тот же символ. Именно его и нарушала прежняя логика шифта на
     * странице символов.
     */
    @Test
    fun everySymbolKeyTypesExactlyWhatItShows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mismatches = mutableListOf<String>()
        listOf(R.xml.keyboard_symbols, R.xml.keyboard_symbols2).forEach { xmlRes ->
            Keyboard(context, xmlRes).keys.forEach { key ->
                val code = key.codes.firstOrNull() ?: return@forEach
                val label = key.label
                // Служебные клавиши (отрицательные коды) и пробел, чью подпись
                // сервис проставляет во время работы, к инварианту не относятся.
                if (code <= 32) return@forEach
                if (label == null || label.length != 1) return@forEach
                val shown = labelForShiftState(label, isLetterMode = false, shifted = false)
                if (shown?.get(0)?.code != code) {
                    mismatches.add("key shows '$shown' but types code $code ('${code.toChar()}')")
                }
            }
        }
        assertEquals(mismatches.joinToString("; "), 0, mismatches.size)
    }
}
