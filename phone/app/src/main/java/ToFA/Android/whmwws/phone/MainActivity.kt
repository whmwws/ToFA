package ToFA.Android.whmwws.phone

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ToFA.Android.whmwws.phone.ui.AppRoot
import ToFA.Android.whmwws.phone.ui.I18n
import ToFA.Android.whmwws.phone.ui.TfaTheme
import ToFA.Android.whmwws.phone.ui.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 验证码属于一次性敏感凭据；是否隐藏最近任务缩略图由「最近任务隐私模式」开关决定
        val prefs = getSharedPreferences("tfa_settings", Context.MODE_PRIVATE)
        applyRecentsPrivacy(prefs.getBoolean("recents_privacy", true))
        // 启动前先应用窗口底色与系统栏，避免浅色模式下首帧闪黑
        applySystemBars(prefs.getString("theme_mode", "system").orEmpty())
        // 界面语言在组合前就位，避免首帧显示错误语言；导入的语言包同步加载
        I18n.apply(prefs.getString("app_language", "zh").orEmpty())
        I18n.loadImported(prefs.getString("language_packs", "[]").orEmpty())
        setContent {
            val vm: AppViewModel = viewModel()
            LaunchedEffect(vm.recentsPrivacy) {
                applyRecentsPrivacy(vm.recentsPrivacy)
            }
            LaunchedEffect(vm.themeMode) {
                applySystemBars(vm.themeMode)
            }
            TfaTheme(accentId = vm.accentId, themeModeId = vm.themeMode) {
                AppRoot(vm)
            }
        }
    }

    /** 窗口底色、状态栏与导航栏跟随深浅色，图标亮度同步反相。 */
    private fun applySystemBars(themeModeId: String) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = resolveDarkTheme(themeModeId, systemDark)
        val background = if (dark) 0xFF0F1115.toInt() else 0xFFF7F8FA.toInt()
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun applyRecentsPrivacy(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
