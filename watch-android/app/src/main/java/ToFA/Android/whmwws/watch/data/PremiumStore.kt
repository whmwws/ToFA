package ToFA.Android.whmwws.watch.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 手表端高级功能激活状态。
 *
 * 手表没有支付/联网能力，按需求由手机端激活：手机通过 adb 推送 activation 文件，
 * 手表读取后置为已激活。未激活时仅做提示，不阻断基础验证码功能。
 */
class PremiumStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tfa_watch_premium", Context.MODE_PRIVATE)

    var activated by mutableStateOf(prefs.getBoolean(KEY_ACTIVATED, false))
        private set

    var multiPromptShown by mutableStateOf(prefs.getBoolean(KEY_MULTI_PROMPT, false))
        private set

    fun markActivated(value: Boolean) {
        activated = value
        prefs.edit().putBoolean(KEY_ACTIVATED, value).apply()
    }

    fun markMultiPromptShown() {
        multiPromptShown = true
        prefs.edit().putBoolean(KEY_MULTI_PROMPT, true).apply()
    }

    private companion object {
        const val KEY_ACTIVATED = "activated"
        const val KEY_MULTI_PROMPT = "multi_prompt_shown"
    }
}
