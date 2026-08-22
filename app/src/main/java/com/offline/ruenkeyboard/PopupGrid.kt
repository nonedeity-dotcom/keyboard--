package com.offline.ruenkeyboard

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Геометрия попапа долгого нажатия и выбор символа по позиции пальца.
 *
 * Вынесено из HintKeyboardView отдельным классом, потому что именно эта
 * арифметика — источник разницы с референсной клавиатурой, и её нужно
 * покрывать обычными unit-тестами, а не проверять руками на телефоне.
 */
internal class PopupGrid(
    val charCount: Int,
    val columns: Int,
    val left: Float,
    val top: Float,
    val cellSize: Float
) {
    val rows: Int = ceil(charCount / columns.toDouble()).toInt()
    val width: Float = columns * cellSize
    val height: Float = rows * cellSize

    /**
     * Индекс символа под пальцем.
     *
     * Координаты именно ЗАЖИМАЮТСЯ в границы попапа, а не отбрасываются:
     * во время долгого нажатия палец физически стоит на клавише, то есть
     * ниже попапа, и почти всегда вне его прямоугольника. Референсная
     * клавиатура в этом случае продолжает вести выбор по ближайшей ячейке,
     * а не сбрасывает его — из-за этого у нас и «требовалось попадать
     * ровно в строку».
     *
     * Как следствие палец под попапом выбирает НИЖНЮЮ строку — это же
     * поведение видно на записи экрана референса, где при зажатии запятой
     * подсвечен символ из второго ряда.
     */
    fun indexAt(x: Float, y: Float): Int {
        val col = floor((x - left) / cellSize).toInt().coerceIn(0, columns - 1)
        val row = floor((y - top) / cellSize).toInt().coerceIn(0, rows - 1)
        // Последний ряд может быть неполным (15 символов в сетке 8×2),
        // поэтому итог ещё раз зажимаем по реальному количеству символов.
        return (row * columns + col).coerceIn(0, charCount - 1)
    }

    companion object {
        const val MAX_COLUMNS = 8

        fun forCharacters(
            chars: String,
            keyCenterX: Float,
            keyTop: Float,
            cellSize: Float,
            gap: Float,
            viewWidth: Int
        ): PopupGrid {
            val columns = minOf(chars.length, MAX_COLUMNS)
            val rows = ceil(chars.length / columns.toDouble()).toInt()
            val popupWidth = columns * cellSize

            // Первая ячейка встаёт по центру клавиши: символ, нарисованный
            // подсказкой в углу клавиши, — это chars[0], и он же должен быть
            // выбран, если палец не двигали.
            val left = (keyCenterX - cellSize / 2f)
                .coerceIn(0f, (viewWidth - popupWidth).coerceAtLeast(0f))
            val top = keyTop - rows * cellSize - gap

            return PopupGrid(chars.length, columns, left, top, cellSize)
        }
    }
}
