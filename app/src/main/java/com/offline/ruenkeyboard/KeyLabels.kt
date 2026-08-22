package com.offline.ruenkeyboard

/**
 * Подпись клавиши с учётом шифта.
 *
 * Регистр меняется только на буквенных раскладках: на странице символов
 * есть одиночные буквенные подписи (π, Δ), которые к шифту отношения не
 * имеют, и приведение их к нижнему регистру переписывало «Δ» в «δ» —
 * клавиша при этом продолжала вводить Δ, то есть подпись врала.
 */
internal fun labelForShiftState(
    label: CharSequence?,
    isLetterMode: Boolean,
    shifted: Boolean
): CharSequence? {
    if (!isLetterMode) return label
    if (label == null || label.length != 1 || !Character.isLetter(label[0])) return label
    return if (shifted) label.toString().uppercase() else label.toString().lowercase()
}
