package ToFA.Android.whmwws.phone.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 捐赠 / 高级功能状态。
 *
 * 设计为「荣誉制」：无论用户选择「我已捐赠」还是「暂不考虑并确认」，
 * 都视为启用高级功能；区别只在提示文案与是否引导连接手表激活。
 * 不依赖应用商店支付，避免在离线手表场景引入额外权限。
 */
class PremiumStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tfa_premium", Context.MODE_PRIVATE)

    var activated by mutableStateOf(prefs.getBoolean(KEY_ACTIVATED, false))
        private set

    /** 主页激活横幅是否已经处理过（两种选择后都会置为 true）。 */
    var bannerResolved by mutableStateOf(prefs.getBoolean(KEY_BANNER_RESOLVED, false))
        private set

    val shouldShowBanner: Boolean get() = !activated && !bannerResolved

    fun markDonated() {
        persist(activated = true, bannerResolved = true)
    }

    fun markDeclined() {
        persist(activated = true, bannerResolved = true)
    }

    fun activate() {
        persist(activated = true, bannerResolved = true)
    }

    fun resetBanner() {
        persist(activated = false, bannerResolved = false)
    }

    private fun persist(activated: Boolean, bannerResolved: Boolean) {
        this.activated = activated
        this.bannerResolved = bannerResolved
        prefs.edit()
            .putBoolean(KEY_ACTIVATED, activated)
            .putBoolean(KEY_BANNER_RESOLVED, bannerResolved)
            .apply()
    }

    private companion object {
        const val KEY_ACTIVATED = "activated"
        const val KEY_BANNER_RESOLVED = "banner_resolved"
    }
}
