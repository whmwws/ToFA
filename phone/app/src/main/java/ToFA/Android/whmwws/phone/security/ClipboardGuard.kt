package ToFA.Android.whmwws.phone.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 剪贴板守护：验证码复制后，在验证码更新（进入下一个周期）时自动清空，
 * 避免旧验证码长期停留在系统剪贴板 / 输入法历史里。
 * 只有当剪贴板内容仍是本次复制的内容时才清空，避免误删用户后来复制的内容。
 */
object ClipboardGuard {

    private const val DEFAULT_CLEAR_DELAY_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun copy(
        context: Context,
        label: String,
        text: String,
        clearAfterMs: Long = DEFAULT_CLEAR_DELAY_MS,
    ): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            delay(clearAfterMs.coerceAtLeast(1_000L))
            val current = manager.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString()
            if (current == text) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    manager.clearPrimaryClip()
                } else {
                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
        return true
    }
}
