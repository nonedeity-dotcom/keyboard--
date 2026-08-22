package com.offline.ruenkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Backspace должен стирать один ВИДИМЫЙ символ, а не одну UTF-16 единицу.
 * Для эмодзи это принципиально: они занимают две единицы, и удаление одной
 * оставило бы в поле половину суррогатной пары.
 */
class GraphemeDeletionTest {

    @Test
    fun plainAsciiDeletesOneUnit() {
        assertEquals(1, unitsToDeleteAtEndOf("abc"))
    }

    @Test
    fun cyrillicDeletesOneUnit() {
        assertEquals(1, unitsToDeleteAtEndOf("привет"))
    }

    @Test
    fun emojiDeletesTheWholeSurrogatePair() {
        val text = "hi 😀"
        assertEquals("😀 is a surrogate pair and must go as a whole", 2, unitsToDeleteAtEndOf(text))
        // Проверяем и результат: остаток не должен содержать «обрубка» пары.
        val remaining = text.dropLast(unitsToDeleteAtEndOf(text))
        assertEquals("hi ", remaining)
        assertEquals(
            "no dangling surrogate may be left behind",
            0,
            remaining.count { it.isSurrogate() }
        )
    }

    @Test
    fun deletingEveryEmojiInTheBundledSetLeavesNoDanglingSurrogate() {
        // Прогоняем весь зашитый набор: любая ячейка панели эмодзи должна
        // удаляться без остатка.
        val broken = mutableListOf<String>()
        EmojiData.categories.flatMap { it.emoji }.forEach { emoji ->
            val text = "x$emoji"
            val remaining = text.dropLast(unitsToDeleteAtEndOf(text))
            if (remaining != "x") broken.add(emoji)
        }
        assertEquals("emoji left a remainder after one backspace: $broken", 0, broken.size)
    }

    @Test
    fun emptyTextDeletesNothing() {
        assertEquals(0, unitsToDeleteAtEndOf(""))
    }
}
