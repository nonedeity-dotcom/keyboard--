package com.offline.ruenkeyboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.inputmethodservice.Keyboard
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
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
 * Рисует полный keyboard_container.xml (верхняя панель с кнопкой буфера
 * обмена + сама клавиатура) — в отличие от KeyboardScreenshotTest, который
 * рисует только голый HintKeyboardView без окружающего layout'а. Нужен,
 * чтобы визуально подтвердить, что кнопка буфера обмена реально справа.
 */
@RunWith(RobolectricTestRunner::class)
// Плотность зафиксирована: 1080px рендера = ровно 360dp, как на
// телефоне с референсного фото, поэтому рендеры можно сравнивать
// с ним напрямую, а не «на глаз».
@Config(sdk = [33], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContainerScreenshotTest {

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private val ruHints = mapOf(
        1081 to "1", 1094 to "2", 1091 to "3", 1082 to "4", 1077 to "5",
        1085 to "6", 1075 to "7", 1096 to "8", 1097 to "9", 1079 to "0",
        1092 to "@", 1099 to "#", 1074 to "₽", 1072 to "_", 1087 to "&",
        1088 to "-", 1086 to "+", 1083 to "(", 1076 to ")", 1078 to "№", 1101 to "~",
        1103 to "*", 1095 to "\"", 1089 to "'", 1084 to ":", 1080 to ";",
        1090 to "!", 1100 to "ъ", 1073 to "?", 1102 to "%"
    )
    private val codeEnter = -15

    @Test
    fun renderFullContainerWithClipboardButton() {
        try {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val inflater = LayoutInflater.from(activity)
            val container = inflater.inflate(R.layout.keyboard_container, null)

            val keyboardView = container.findViewById<HintKeyboardView>(R.id.keyboard_view)
            keyboardView.keyboard = Keyboard(activity, R.xml.keyboard_ru)
            keyboardView.hints = ruHints
            keyboardView.accentCode = codeEnter

            activity.addContentView(
                container,
                ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            shadowOf(Looper.getMainLooper()).idle()
            keyboardView.invalidateAllKeys()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("container measured to a zero size", container.width > 0 && container.height > 0)

            val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            container.draw(canvas)

            // Проверяем, что кнопка буфера обмена реально находится в правой
            // половине верхней панели, а не по центру.
            val clipboardBtn = container.findViewById<View>(R.id.btn_open_clipboard)
            val btnCenterX = clipboardBtn.left + clipboardBtn.width / 2
            assertTrue(
                "clipboard button center ($btnCenterX) should be in the right half of the top bar (width=${container.width})",
                btnCenterX > container.width / 2
            )

            FileOutputStream(File(outDir, "keyboard_container.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val thumbWidth = 480
            val thumbHeight = (bitmap.height.toFloat() * thumbWidth / bitmap.width).toInt()
            val thumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
            val thumbBytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.PNG, 80, it) }.toByteArray()
            val b64 = Base64.getEncoder().encodeToString(thumbBytes)

            println("SCREENSHOT_BEGIN keyboard_container.png ${thumbWidth}x$thumbHeight ${b64.length}chars")
            b64.chunked(120).forEach { println("SCREENSHOT_DATA $it") }
            println("SCREENSHOT_END keyboard_container.png")
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }
}
