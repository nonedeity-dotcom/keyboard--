package com.offline.ruenkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.content.ClipboardManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Клавиатура RU/EN с буферами обмена. Работает полностью офлайн:
 * все данные (история буфера обмена) хранятся только локально на устройстве.
 */
class RuEnKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private enum class Lang { EN, RU }
    private enum class Mode { LETTERS, SYMBOLS, SYMBOLS2 }
    private enum class ShiftState { NONE, ONCE, CAPS }

    companion object {
        const val CODE_SHIFT = -10
        const val CODE_DELETE = -11
        const val CODE_TO_SYMBOLS = -12
        const val CODE_TO_LETTERS = -13
        const val CODE_ENTER = -15
        const val CODE_EMOJI = -16
        const val CODE_TO_SYMBOLS2 = -17
        const val CODE_CURSOR_LEFT = -18
        const val CODE_CURSOR_RIGHT = -19

        private const val SHIFT_DOUBLE_TAP_MS = 300L
        // Реальное значение из референс-клавиатуры (config_longpress_shift_lock_timeout).
        private const val SHIFT_LONG_PRESS_MS = 800L

        private val DIGIT_HINTS_EN = mapOf(
            113 to "1", 119 to "2", 101 to "3", 114 to "4", 116 to "5",
            121 to "6", 117 to "7", 105 to "8", 111 to "9", 112 to "0",
            97 to "@", 115 to "#", 100 to "$", 102 to "_", 103 to "&",
            104 to "-", 106 to "+", 107 to "(", 108 to ")",
            122 to "*", 120 to "\"", 99 to "'", 118 to ":", 98 to ";",
            110 to "!", 109 to "?"
        )
        private val RU_HINTS = mapOf(
            1081 to "1", 1094 to "2", 1091 to "3", 1082 to "4", 1077 to "5",
            1085 to "6", 1075 to "7", 1096 to "8", 1097 to "9", 1079 to "0",
            1092 to "@", 1099 to "#", 1074 to "₽", 1072 to "_", 1087 to "&",
            1088 to "-", 1086 to "+", 1083 to "(", 1076 to ")", 1078 to "№", 1101 to "~",
            1103 to "*", 1095 to "\"", 1089 to "'", 1084 to ":", 1080 to ";",
            1090 to "!", 1100 to "ъ", 1073 to "?", 1102 to "%"
        )
    }

    private lateinit var keyboardView: HintKeyboardView
    private lateinit var containerView: View
    private lateinit var clipboardPanelView: View
    private lateinit var clipListContainer: LinearLayout
    private lateinit var emojiPanelView: View

    private lateinit var enKeyboard: Keyboard
    private lateinit var ruKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var symbols2Keyboard: Keyboard

    private var lang = Lang.EN
    private var mode = Mode.LETTERS
    private var shiftState = ShiftState.NONE
    private var lastShiftTapTime = 0L
    private var shiftPressTime = 0L

    private lateinit var clipboardStore: ClipboardHistoryStore
    private lateinit var systemClipboard: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onSystemClipboardChanged() }

    override fun onCreate() {
        super.onCreate()
        clipboardStore = ClipboardHistoryStore(this)
        systemClipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        systemClipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        systemClipboard.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val inflater: LayoutInflater = layoutInflater
        containerView = inflater.inflate(R.layout.keyboard_container, null)

        keyboardView = containerView.findViewById(R.id.keyboard_view)
        clipboardPanelView = containerView.findViewById(R.id.clipboard_panel)
        clipListContainer = clipboardPanelView.findViewById(R.id.clip_list_container)
        emojiPanelView = containerView.findViewById(R.id.emoji_panel)

        enKeyboard = Keyboard(this, R.xml.keyboard_en)
        ruKeyboard = Keyboard(this, R.xml.keyboard_ru)
        symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)
        symbols2Keyboard = Keyboard(this, R.xml.keyboard_symbols2)

        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.accentCode = CODE_ENTER
        keyboardView.onSwipeLanguage = { switchLanguage() }
        // Стоковый попап-превью клавиши (увеличенная буква над пальцем) у
        // KeyboardView позиционируется нестабильно на части прошивок и
        // рисуется поверх системного UI — современные клавиатуры его не
        // используют, ограничиваясь подсветкой клавиши.
        keyboardView.isPreviewEnabled = false

        containerView.findViewById<TextView>(R.id.btn_open_clipboard).setOnClickListener {
            openClipboardPanel()
        }
        clipboardPanelView.findViewById<TextView>(R.id.btn_clip_back).setOnClickListener {
            closePanels()
        }
        clipboardPanelView.findViewById<TextView>(R.id.btn_clip_clear).setOnClickListener {
            clipboardStore.clear()
            renderClipboardList()
        }
        emojiPanelView.findViewById<TextView>(R.id.btn_emoji_back).setOnClickListener {
            closePanels()
        }

        applyKeyboard()
        return containerView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        mode = Mode.LETTERS
        shiftState = ShiftState.NONE
        closePanels()
        applyKeyboard()
    }

    // --- Клавиатура: переключение режимов ---

    private fun applyKeyboard() {
        val keyboard = when (mode) {
            Mode.SYMBOLS -> symbolsKeyboard
            Mode.SYMBOLS2 -> symbols2Keyboard
            Mode.LETTERS -> if (lang == Lang.RU) ruKeyboard else enKeyboard
        }
        keyboardView.keyboard = keyboard
        keyboardView.hints = when {
            mode == Mode.LETTERS && lang == Lang.EN -> DIGIT_HINTS_EN
            mode == Mode.LETTERS && lang == Lang.RU -> RU_HINTS
            else -> emptyMap()
        }
        applyLangLabel()
        applyShiftVisuals()
    }

    private fun applyLangLabel() {
        val keyboard = keyboardView.keyboard ?: return
        val label = if (lang == Lang.RU) "Русский" else "English"
        for (key in keyboard.keys) {
            if (key.codes.isNotEmpty() && key.codes[0] == 32) {
                key.label = label
            }
        }
    }

    private fun switchLanguage() {
        if (mode != Mode.LETTERS) return
        lang = if (lang == Lang.RU) Lang.EN else Lang.RU
        applyKeyboard()
    }

    private fun applyShiftVisuals() {
        val keyboard = keyboardView.keyboard ?: return
        for (key in keyboard.keys) {
            if (key.codes.isNotEmpty() && key.codes[0] == CODE_SHIFT) {
                key.label = if (shiftState == ShiftState.CAPS) "⇪" else "⇧"
            } else {
                val label = key.label
                if (label != null && label.length == 1 && Character.isLetter(label[0])) {
                    key.label = if (shiftState != ShiftState.NONE) {
                        label.toString().uppercase()
                    } else {
                        label.toString().lowercase()
                    }
                }
            }
        }
        keyboardView.invalidateAllKeys()
    }

    // --- Обработка ввода ---

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection

        when (primaryCode) {
            CODE_SHIFT -> {
                val now = System.currentTimeMillis()
                val heldMs = now - shiftPressTime
                shiftState = when {
                    heldMs >= SHIFT_LONG_PRESS_MS -> ShiftState.CAPS
                    now - lastShiftTapTime < SHIFT_DOUBLE_TAP_MS -> ShiftState.CAPS
                    shiftState == ShiftState.NONE -> ShiftState.ONCE
                    else -> ShiftState.NONE
                }
                lastShiftTapTime = now
                applyShiftVisuals()
            }
            CODE_DELETE -> {
                val selected = ic?.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic?.deleteSurroundingText(1, 0)
                }
            }
            CODE_TO_SYMBOLS -> {
                mode = Mode.SYMBOLS
                applyKeyboard()
            }
            CODE_TO_SYMBOLS2 -> {
                mode = Mode.SYMBOLS2
                applyKeyboard()
            }
            CODE_TO_LETTERS -> {
                mode = Mode.LETTERS
                shiftState = ShiftState.NONE
                applyKeyboard()
            }
            CODE_ENTER -> performEnter(ic)
            CODE_EMOJI -> openEmojiPanel()
            CODE_CURSOR_LEFT -> {
                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
            }
            CODE_CURSOR_RIGHT -> {
                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            else -> {
                if (primaryCode > 0) {
                    val codeToCommit = if (shiftState != ShiftState.NONE && Character.isLetter(primaryCode)) {
                        Character.toUpperCase(primaryCode)
                    } else {
                        primaryCode
                    }
                    ic?.commitText(String(Character.toChars(codeToCommit)), 1)

                    if (shiftState == ShiftState.ONCE) {
                        shiftState = ShiftState.NONE
                        applyShiftVisuals()
                    }
                }
            }
        }
    }

    private fun performEnter(ic: android.view.inputmethod.InputConnection?) {
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterFlag = (editorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && noEnterFlag == 0) {
            ic?.performEditorAction(action)
        } else {
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    // --- Панели (буфер обмена / эмодзи) ---

    private fun openClipboardPanel() {
        renderClipboardList()
        keyboardView.visibility = View.GONE
        emojiPanelView.visibility = View.GONE
        clipboardPanelView.visibility = View.VISIBLE
    }

    private fun openEmojiPanel() {
        keyboardView.visibility = View.GONE
        clipboardPanelView.visibility = View.GONE
        emojiPanelView.visibility = View.VISIBLE
    }

    private fun closePanels() {
        clipboardPanelView.visibility = View.GONE
        emojiPanelView.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
    }

    // --- Буфер обмена ---

    private fun onSystemClipboardChanged() {
        val clip = systemClipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        clipboardStore.addEntry(text)
    }

    private fun renderClipboardList() {
        clipListContainer.removeAllViews()
        val history = clipboardStore.getHistory()
        val inflater = layoutInflater

        if (history.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.clipboard_empty)
            empty.setPadding(32, 32, 32, 32)
            clipListContainer.addView(empty)
            return
        }

        for (text in history) {
            val itemView = inflater.inflate(R.layout.clipboard_item, clipListContainer, false)
            val textView = itemView.findViewById<TextView>(R.id.clip_item_text)
            val deleteView = itemView.findViewById<TextView>(R.id.clip_item_delete)
            textView.text = text

            itemView.setOnClickListener {
                currentInputConnection?.commitText(text, 1)
                closePanels()
            }
            deleteView.setOnClickListener {
                clipboardStore.removeEntry(text)
                renderClipboardList()
            }

            clipListContainer.addView(itemView)
        }
    }

    // --- Обязательные методы интерфейса (не используются) ---

    override fun onPress(primaryCode: Int) {
        if (primaryCode == CODE_SHIFT) shiftPressTime = System.currentTimeMillis()
    }
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
