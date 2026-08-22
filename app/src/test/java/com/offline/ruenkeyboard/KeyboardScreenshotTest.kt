package com.offline.ruenkeyboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.inputmethodservice.Keyboard
import android.os.Looper
import android.view.ViewGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

/**
 * Рисует каждую раскладку в PNG (артефакт CI) и ещё печатает уменьшенную
 * копию как base64 прямо в лог теста — так результат можно прочитать
 * через логи джоба, даже если сам artifact-zip недоступен для скачивания.
 *
 * Клавиатура рисуется в activity с реальным окном (не голый View), потому
 * что стоковый KeyboardView строит свой внутренний битмап-буфер клавиш
 * при attach/layout — без окна onDraw() ничего не блитит на канвас.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardScreenshotTest {

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun renderKeyboard(xmlRes: Int, fileName: String) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = HintKeyboardView(activity, null)
        view.keyboard = Keyboard(activity, xmlRes)
        view.setBackgroundColor(activity.getColor(R.color.keyboard_bg))

        activity.addContentView(
            view,
            ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        shadowOf(Looper.getMainLooper()).idle()
        view.invalidateAllKeys()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("$fileName measured to a zero size", view.width > 0 && view.height > 0)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        // Проверяем, что реально что-то нарисовано (не только фон одним цветом) —
        // иначе тест мог бы молча "проходить" на пустом кадре.
        val backgroundColor = bitmap.getPixel(0, 0)
        var distinctPixelFound = false
        var x = 0
        while (x < bitmap.width && !distinctPixelFound) {
            var y = 0
            while (y < bitmap.height && !distinctPixelFound) {
                if (bitmap.getPixel(x, y) != backgroundColor) distinctPixelFound = true
                y += 5
            }
            x += 5
        }
        assertTrue("$fileName appears blank: no pixel differs from the background color", distinctPixelFound)

        FileOutputStream(File(outDir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Уменьшенная копия для печати в лог (base64), чтобы её можно было
        // реально посмотреть через get_job_logs, не скачивая artifact-zip.
        val thumbWidth = 480
        val thumbHeight = (view.height.toFloat() * thumbWidth / view.width).toInt()
        val thumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
        val thumbBytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.PNG, 80, it) }.toByteArray()
        val b64 = Base64.getEncoder().encodeToString(thumbBytes)

        println("SCREENSHOT_BEGIN $fileName ${thumbWidth}x$thumbHeight ${b64.length}chars")
        b64.chunked(120).forEach { println("SCREENSHOT_DATA $it") }
        println("SCREENSHOT_END $fileName")
    }

    @Test
    fun renderAllKeyboardLayouts() {
        try {
            renderKeyboard(R.xml.keyboard_ru, "keyboard_ru.png")
            renderKeyboard(R.xml.keyboard_en, "keyboard_en.png")
            renderKeyboard(R.xml.keyboard_symbols, "keyboard_symbols.png")
            renderKeyboard(R.xml.keyboard_symbols2, "keyboard_symbols2.png")
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }
}
