package ToFA.Android.whmwws.watch

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ToFA.Android.whmwws.watch.data.AccountRepository
import ToFA.Android.whmwws.watch.data.AppContainer
import ToFA.Android.whmwws.watch.data.ThemeSync
import ToFA.Android.whmwws.watch.data.WatchSettings
import ToFA.Android.whmwws.watch.ui.WatchColors
import ToFA.Android.whmwws.watch.ui.WatchI18n
import ToFA.Android.whmwws.watch.ui.WatchRoot
import ToFA.Android.whmwws.watch.ui.WatchSplashView
import ToFA.Android.whmwws.watch.ui.WatchTheme
import ToFA.Android.whmwws.watch.ui.WatchThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** 冷启动计时起点（仅用于启动耗时诊断日志）。 */
    private val bootStartAt = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 展示验证码期间保持亮屏，避免每次抬腕都要重新等待
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 最近任务隐私模式（独立开关，只影响最近任务卡片，不影响页面内容）
        applyRecentsPrivacy(WatchSettings.isRecentsPrivacy(this))
        applyTheme()
        // 窗口底色跟随深浅色，避免浅色模式下启动时先闪一下黑底
        window.setBackgroundDrawable(ColorDrawable(WatchColors.Background.toArgb()))

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 纯 View 的加载环先铺满第一帧：Compose 首次初始化在老设备上要 1 秒以上，
        // 等加载环画完再异步初始化 Compose，首帧就不会被 Compose 拖慢。
        val splash = WatchSplashView(
            this,
            WatchColors.Background.toArgb(),
            WatchColors.Accent.toArgb(),
        )
        val rootLayout = FrameLayout(this)
        rootLayout.addView(
            splash,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(rootLayout)
        rootLayout.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    rootLayout.viewTreeObserver.removeOnPreDrawListener(this)
                    // 放到首帧绘制之后再挂 Compose，避免阻塞加载环出现
                    rootLayout.post { startCompose(rootLayout, splash) }
                    return true
                }
            },
        )
    }

    /** 首帧画出加载环之后才初始化 Compose，真实界面就绪后移除加载环。 */
    private fun startCompose(rootLayout: FrameLayout, splash: WatchSplashView) {
        val composeView = ComposeView(this)
        rootLayout.addView(
            composeView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        composeView.setContent {
            // 保险库解密（Keystore + AES）放在后台线程，不占用首帧与主线程。
            var repository by remember { mutableStateOf<AccountRepository?>(null) }
            LaunchedEffect(Unit) {
                repository = withContext(Dispatchers.IO) {
                    AppContainer.repository(applicationContext)
                }
                Log.d("ToFAStartup", "vault ready +${SystemClock.elapsedRealtime() - bootStartAt}ms")
            }
            WatchTheme {
                val repo = repository
                if (repo != null) {
                    LaunchedEffect(repo) {
                        // 等真实界面完成首帧绘制后再撤掉加载环，避免出现一帧空白
                        withFrameNanos { }
                        withFrameNanos { }
                        rootLayout.removeView(splash)
                        Log.d(
                            "ToFAStartup",
                            "ui ready +${SystemClock.elapsedRealtime() - bootStartAt}ms",
                        )
                    }
                    WatchRoot(repo)
                }
            }
        }
    }

    /** 读取持久化的深浅色模式并立即应用到全局配色。 */
    fun applyTheme() {
        // 手机端可能在本进程未启动时推送过主题：先把落盘命令应用掉，再按设置初始化。
        ThemeSync.applyCommand(this)
        // 语言与主题同批落盘，启动时一并生效
        WatchI18n.apply(this)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WatchColors.applyThemeMode(
            WatchThemeMode.fromId(WatchSettings.themeModeId(this)),
            systemDark,
        )
        WatchColors.setAccent(WatchSettings.accent(this).color)
        // 首次启动也生成 theme_out.json，手机端随时可「从手表导入」。
        WatchSettings.writeThemeExport(this)
    }

    /**
     * API 33+ 用 setRecentsScreenshotEnabled 只隐藏最近任务卡片，不影响 adb 截图调试；
     * 低版本没有该接口，用 FLAG_SECURE 兜底（会同时禁止截屏）。
     */
    fun applyRecentsPrivacy(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(!enabled)
        } else {
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
}
