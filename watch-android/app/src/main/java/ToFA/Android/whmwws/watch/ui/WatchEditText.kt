package ToFA.Android.whmwws.watch.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 手表端系统输入法输入框：内部用平台 [EditText]（AndroidView）承载编辑状态。
 *
 * 为什么不用 Compose 的 BasicTextField：
 * Compose 文本输入在 IME 侧维护 composition / selection 的另一份编辑状态，
 * 系统输入法退格时出现过「光标已前移、文本绘制未更新」的状态/绘制脱节；
 * 换到平台 EditText 后，编辑、删除、渲染都由同一个 Editable 驱动，
 * IME 的显示/消失也回到标准窗口流程，便于用 InputMethodManager 明确收起。
 *
 * 外观仍由外层 Compose 容器负责（底色、描边、占位文案），这里只画文字与光标。
 */
@Composable
fun WatchEditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 13f,
    textColor: Color = WatchColors.Text,
    centered: Boolean = false,
    monospace: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Done,
    enabled: Boolean = true,
    requestFocusSignal: Int = 0,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFocusChanged by rememberUpdatedState(onFocusChanged)
    val currentOnDone by rememberUpdatedState(onDone)
    var editText by remember { mutableStateOf<EditText?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            EditText(ctx).apply {
                background = null
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                isSingleLine = true
                isEnabled = enabled
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setTextColor(textColor.toArgb())
                setHintTextColor(WatchColors.TextFaint.toArgb())
                hint?.let { setHint(it) }
                gravity = if (centered) Gravity.CENTER else (Gravity.CENTER_VERTICAL or Gravity.START)
                typeface = if (monospace) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
                inputType = inputTypeOf(keyboardType, capitalization)
                imeOptions = when (imeAction) {
                    ImeAction.Done -> EditorInfo.IME_ACTION_DONE
                    ImeAction.Go -> EditorInfo.IME_ACTION_GO
                    ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
                    ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
                    ImeAction.Send -> EditorInfo.IME_ACTION_SEND
                    else -> EditorInfo.IME_ACTION_NONE
                }
                if (maxLength != Int.MAX_VALUE) {
                    filters = arrayOf(InputFilter.LengthFilter(maxLength))
                }
                setText(value)
                setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        // 平台输入法已经完成删除/组合/光标更新，这里只把最终文本同步给页面状态。
                        currentOnValueChange(s?.toString().orEmpty())
                    }
                })
                setOnFocusChangeListener { _, hasFocus ->
                    currentOnFocusChanged?.invoke(hasFocus)
                    if (!hasFocus) hideWatchKeyboard(ctx)
                }
                setOnEditorActionListener { view, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        hideWatchKeyboard(ctx)
                        view.clearFocus()
                        currentOnDone?.invoke()
                        true
                    } else {
                        false
                    }
                }
                editText = this
            }
        },
        update = { et ->
            // 只有外部状态真正变化时才 setText；键入过程中文本一致，不再重建、不移动光标。
            if (et.text.toString() != value) {
                val selection = et.selectionStart.coerceIn(0, value.length)
                et.setText(value)
                et.setSelection(selection.coerceIn(0, value.length))
            }
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            et.setTextColor(textColor.toArgb())
            et.setHintTextColor(WatchColors.TextFaint.toArgb())
            if (hint != null) et.hint = hint
            et.isEnabled = enabled
            et.gravity = if (centered) Gravity.CENTER else (Gravity.CENTER_VERTICAL or Gravity.START)
            et.typeface = if (monospace) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
        },
    )

    // 需要程序化唤起键盘时使用（密码页切换系统输入法），只响应信号变化，不会每次重组都抢焦点。
    LaunchedEffect(requestFocusSignal) {
        if (requestFocusSignal > 0) {
            editText?.let { et ->
                et.requestFocus()
                // 刚切到系统输入法时窗口还没完成焦点切换，立即 showSoftInput 会被吞掉；
                // 先等一帧让窗口稳定，再用 WindowInsetsController 显式 show，最后补一次 IMM 调用。
                kotlinx.coroutines.delay(120)
                et.requestFocus()
                et.context.findActivity()?.let { activity ->
                    WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                        .show(WindowInsetsCompat.Type.ime())
                }
                val imm = et.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // 页面退出时兜底收起键盘：平台 IME 窗口不会因为 Compose 节点消失而可靠消失。
    DisposableEffect(editText) {
        val et = editText
        onDispose {
            et?.clearFocus()
            hideWatchKeyboard(context)
        }
    }
}

/** 把 Compose 的键盘类型映射到平台 inputType，保证大小写/密码等行为由系统 IME 自己维护。 */
private fun inputTypeOf(type: KeyboardType, capitalization: KeyboardCapitalization): Int {
    val capFlag = if (capitalization == KeyboardCapitalization.Characters) {
        InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    } else {
        0
    }
    return when (type) {
        KeyboardType.Number -> InputType.TYPE_CLASS_NUMBER
        KeyboardType.Ascii -> InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or capFlag
        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or capFlag
    }
}

/**
 * 明确收起当前窗口的系统输入法。
 *
 * 只调 Compose 的 keyboardController 不够：系统 IME（本机为百度输入法）在手表上以全屏窗口运行，
 * 页面销毁时它的窗口可能 still visible，留下悬浮图标并继续拦截触摸。
 * 这里同时用 InputMethodManager 与 WindowInsetsController 两种平台路径收起，并清掉写焦点。
 */
fun hideWatchKeyboard(context: Context) {
    val activity = context.findActivity() ?: return
    val decor = activity.window.decorView
    val token = decor.windowToken ?: return
    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(token, 0)
    WindowInsetsControllerCompat(activity.window, decor).hide(WindowInsetsCompat.Type.ime())
    decor.clearFocus()
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
