package com.offline.ruenkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты выбора символа в попапе долгого нажатия.
 *
 * Главный случай здесь — клавиша запятой: у неё 15 символов, то есть сетка
 * 8×2, и в первой версии попапа индекс считался только по X. Из-за этого
 * весь второй ряд (';', '/', '(', ')', '#', '!', '?') был недостижим в
 * принципе, хотя рисовался на экране.
 */
class PopupGridTest {

    private val cell = 44f
    private val commaChars = "&%+\"-:'@;/()#!?"

    private fun commaGrid() = PopupGrid.forCharacters(
        chars = commaChars,
        keyCenterX = 300f,
        keyTop = 400f,
        cellSize = cell,
        gap = 4f,
        viewWidth = 1080
    )

    @Test
    fun commaPopupIsTwoRows() {
        val grid = commaGrid()
        assertEquals(15, grid.charCount)
        assertEquals(8, grid.columns)
        assertEquals(2, grid.rows)
    }

    @Test
    fun everyCharacterOfATwoRowPopupIsReachable() {
        val grid = commaGrid()
        val reached = mutableSetOf<Int>()
        // Проходим сеткой по всей площади попапа с шагом в четверть ячейки.
        var y = grid.top
        while (y < grid.top + grid.height) {
            var x = grid.left
            while (x < grid.left + grid.width) {
                reached.add(grid.indexAt(x, y))
                x += cell / 4f
            }
            y += cell / 4f
        }
        val unreachable = commaChars.indices.filterNot { it in reached }
        assertTrue(
            "characters unreachable by touch: " +
                unreachable.joinToString { "index $it ('${commaChars[it]}')" },
            unreachable.isEmpty()
        )
    }

    @Test
    fun fingerBelowThePopupSelectsTheBottomRow() {
        val grid = commaGrid()
        // Палец остаётся на самой клавише, то есть ниже попапа целиком.
        val onTheKeyY = grid.top + grid.height + 30f
        val index = grid.indexAt(grid.left + cell / 2f, onTheKeyY)
        assertEquals("expected first character of the bottom row", 8, index)
    }

    @Test
    fun movingUpFromTheKeyReachesTheTopRow() {
        val grid = commaGrid()
        val index = grid.indexAt(grid.left + cell / 2f, grid.top + cell / 2f)
        assertEquals(0, index)
    }

    @Test
    fun draggingOutsideThePopupKeepsTheNearestCellInsteadOfCancelling() {
        val grid = commaGrid()
        val farLeft = grid.indexAt(grid.left - 500f, grid.top + cell / 2f)
        val farRight = grid.indexAt(grid.left + grid.width + 500f, grid.top + cell / 2f)
        assertEquals("dragging off the left edge should hold the first cell", 0, farLeft)
        assertEquals("dragging off the right edge should hold the last cell of that row", 7, farRight)
    }

    @Test
    fun trailingEmptyCellOfAPartialLastRowClampsToTheLastCharacter() {
        val grid = commaGrid()
        // Нижний ряд заполнен только на 7 из 8 ячеек: восьмая пустая.
        val index = grid.indexAt(grid.left + grid.width - cell / 2f, grid.top + grid.height - cell / 2f)
        assertEquals(commaChars.length - 1, index)
    }

    @Test
    fun singleRowPopupSelectsItsFirstCharacterUnderTheKeyCentre() {
        val chars = "@àáâãäå"
        val keyCenterX = 300f
        val grid = PopupGrid.forCharacters(chars, keyCenterX, 400f, cell, 4f, 1080)
        assertEquals(1, grid.rows)
        // Палец не двигали: он всё ещё в центре клавиши, и должен быть выбран
        // ровно тот символ, который нарисован подсказкой в углу клавиши.
        val index = grid.indexAt(keyCenterX, grid.top + grid.height + 30f)
        assertEquals("expected the hint character '${chars[0]}'", 0, index)
    }

    @Test
    fun popupNeverExtendsPastTheRightEdgeOfTheKeyboard(  ) {
        val viewWidth = 1080
        val chars = "0123456789"
        // Клавиша у самого правого края экрана.
        val grid = PopupGrid.forCharacters(chars, viewWidth - 20f, 400f, cell, 4f, viewWidth)
        assertTrue("popup left edge must stay on screen", grid.left >= 0f)
        assertTrue(
            "popup right edge ${grid.left + grid.width} must stay within $viewWidth",
            grid.left + grid.width <= viewWidth
        )
    }
}
