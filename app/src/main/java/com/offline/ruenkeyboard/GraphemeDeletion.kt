package com.offline.ruenkeyboard

import java.text.BreakIterator

/**
 * Сколько UTF-16 единиц нужно удалить, чтобы стереть ровно один видимый
 * символ в конце строки.
 *
 * InputConnection.deleteSurroundingText оперирует UTF-16 единицами, а не
 * символами. Эмодзи — это суррогатная пара (а часто ещё и селектор варианта
 * или keycap сверху), поэтому удаление одной единицы разрезало бы его
 * пополам и оставляло бы в поле ввода мусорный остаток.
 */
internal fun unitsToDeleteAtEndOf(text: String): Int {
    if (text.isEmpty()) return 0
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(text)
    val lastBoundary = boundaries.preceding(text.length)
    val units = if (lastBoundary == BreakIterator.DONE) text.length else text.length - lastBoundary
    return units.coerceAtLeast(1)
}
