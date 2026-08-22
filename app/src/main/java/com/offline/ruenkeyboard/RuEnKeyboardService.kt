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
    private enum class Mode { LETTERS, SYMBOLS }
    private enum class ShiftState { NONE, ONCE, CAPS }

    companion object {
        const val CODE_SHIFT = -10
        const val CODE_DELETE = -11
        const val CODE_TO_SYMBOLS = -12
        const val CODE_TO_LETTERS = -13
        const val CODE_LANG_SWITCH = -14
        const val CODE_ENTER = -15
        const val CODE_CLIPBOARD_OPEN = -16

        private const val SHIFT_DOUBLE_TAP_MS = 300L
    }

    private lateinit var keyboardView: KeyboardView
    private lateinit var containerView: View
    private lateinit var clipboardPanelView: View
    private lateinit var clipListContainer: LinearLayout

    private lateinit var enKeyboard: Keyboard
    private lateinit var ruKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard

    private var lang = Lang.EN
    private var mode = Mode.LETTERS
    private var shiftState = ShiftState.NONE
    private var lastShiftTapTime = 0L

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

        enKeyboard = Keyboard(this, R.xml.keyboard_en)
        ruKeyboard = Keyboard(this, R.xml.keyboard_ru)
        symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)

        keyboardView.setOnKeyboardActionListener(this)

        clipboardPanelView.findViewById<TextView>(R.id.btn_clip_back).setOnClickListener {
            closeClipboardPanel()
        }
        clipboardPanelView.findViewById<TextView>(R.id.btn_clip_clear).setOnClickListener {
            clipboardStore.clear()
            renderClipboardList()
        }

        applyKeyboard()
        return containerView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        mode = Mode.LETTERS
        shiftState = ShiftState.NONE
        closeClipboardPanel()
        applyKeyboard()
    }

    // --- Клавиатура: переключение режимов ---

    private fun applyKeyboard() {
        val keyboard = when (mode) {
            Mode.SYMBOLS -> symbolsKeyboard
            Mode.LETTERS -> if (lang == Lang.RU) ruKeyboard else enKeyboard
        }
        keyboardView.keyboard = keyboard
        applyShiftVisuals()
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
                shiftState = when {
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
            CODE_TO_LETTERS -> {
                mode = Mode.LETTERS
                shiftState = ShiftState.NONE
                applyKeyboard()
            }
            CODE_LANG_SWITCH -> {
                lang = if (lang == Lang.RU) Lang.EN else Lang.RU
                applyKeyboard()
            }
            CODE_ENTER -> performEnter(ic)
            CODE_CLIPBOARD_OPEN -> openClipboardPanel()
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

    // --- Буфер обмена ---

    private fun onSystemClipboardChanged() {
        val clip = systemClipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        clipboardStore.addEntry(text)
    }

    private fun openClipboardPanel() {
        renderClipboardList()
        keyboardView.visibility = View.GONE
        clipboardPanelView.visibility = View.VISIBLE
    }

    private fun closeClipboardPanel() {
        clipboardPanelView.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
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
                closeClipboardPanel()
            }
            deleteView.setOnClickListener {
                clipboardStore.removeEntry(text)
                renderClipboardList()
            }

            clipListContainer.addView(itemView)
        }
    }

    // --- Обязательные методы интерфейса (не используются) ---

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
