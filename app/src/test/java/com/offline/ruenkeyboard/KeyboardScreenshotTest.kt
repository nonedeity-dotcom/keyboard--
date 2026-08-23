package com.offline.ruenkeyboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.inputmethodservice.Keyboard
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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
// Плотность зафиксирована: 1080px рендера = ровно 360dp, как на
// телефоне с референсного фото, поэтому рендеры можно сравнивать
// с ним напрямую, а не «на глаз».
@Config(sdk = [33], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardScreenshotTest {

    private val outDir = File("build/screenshots").apply { mkdirs() }

    // Те же таблицы подсказок и accent-код, что RuEnKeyboardService реально
    // применяет к keyboardView — чтобы рендер в тесте показывал то же самое,
    // что показала бы настоящая клавиатура, а не голый KeyboardView без хинтов.
    private val digitHintsEn = mapOf(
        113 to "1", 119 to "2", 101 to "3", 114 to "4", 116 to "5",
        121 to "6", 117 to "7", 105 to "8", 111 to "9", 112 to "0",
        97 to "@", 115 to "#", 100 to "$", 102 to "_", 103 to "&",
        104 to "-", 106 to "+", 107 to "(", 108 to ")",
        122 to "*", 120 to "\"", 99 to "'", 118 to ":", 98 to ";",
        110 to "!", 109 to "?"
    )
    private val ruHints = mapOf(
        1081 to "1", 1094 to "2", 1091 to "3", 1082 to "4", 1077 to "5",
        1085 to "6", 1075 to "7", 1096 to "8", 1097 to "9", 1079 to "0",
        1092 to "@", 1099 to "#", 1074 to "₽", 1072 to "_", 1087 to "&",
        1088 to "-", 1086 to "+", 1083 to "(", 1076 to ")", 1078 to "№", 1101 to "~",
        1103 to "*", 1095 to "\"", 1089 to "'", 1084 to ":", 1080 to ";",
        1090 to "!", 1100 to "ъ", 1073 to "?", 1102 to "%"
    )
    private val codeEnter = -15

    private fun renderKeyboard(xmlRes: Int, fileName: String, hints: Map<Int, String> = emptyMap()) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Инфлейтим настоящий keyboard_container.xml (как в RuEnKeyboardService),
        // а не голый HintKeyboardView(activity, null) — атрибуты вроде
        // android:keyBackground/keyTextColor/keyTextSize задаются ТОЛЬКО в XML
        // и применяются лишь при инфлейте с реальным AttributeSet, иначе рендер
        // тихо откатывается на дефолтный серый стиль KeyboardView.
        val root = LayoutInflater.from(activity).inflate(R.layout.keyboard_container, null)
        val view = root.findViewById<HintKeyboardView>(R.id.keyboard_view)
        view.keyboard = Keyboard(activity, xmlRes)
        view.hints = hints
        view.accentCode = codeEnter

        activity.addContentView(
            root,
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
            renderKeyboard(R.xml.keyboard_ru, "keyboard_ru.png", ruHints)
            renderKeyboard(R.xml.keyboard_en, "keyboard_en.png", digitHintsEn)
            renderKeyboard(R.xml.keyboard_symbols, "keyboard_symbols.png")
            renderKeyboard(R.xml.keyboard_symbols2, "keyboard_symbols2.png")
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }
}
