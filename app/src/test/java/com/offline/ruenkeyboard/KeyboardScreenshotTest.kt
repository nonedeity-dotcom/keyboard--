package com.offline.ruenkeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.inputmethodservice.Keyboard
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Рисует каждую раскладку в PNG-файл, чтобы можно было реально
 * посмотреть на результат (а не просто поверить, что код скомпилировался).
 * Файлы попадают в app/build/screenshots и заливаются CI как артефакт.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardScreenshotTest {

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun renderKeyboard(xmlRes: Int, hints: Map<Int, String>, fileName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = HintKeyboardView(context, null)
        view.hints = hints
        view.keyboard = Keyboard(context, xmlRes)
        view.setBackgroundColor(context.getColor(R.color.keyboard_bg))

        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        FileOutputStream(File(outDir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderAllKeyboardLayouts() {
        renderKeyboard(R.xml.keyboard_ru, emptyMap(), "keyboard_ru.png")
        renderKeyboard(R.xml.keyboard_en, emptyMap(), "keyboard_en.png")
        renderKeyboard(R.xml.keyboard_symbols, emptyMap(), "keyboard_symbols.png")
        renderKeyboard(R.xml.keyboard_symbols2, emptyMap(), "keyboard_symbols2.png")
    }
}
