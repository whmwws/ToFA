package ToFA.Android.whmwws.watch.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import org.json.JSONObject
import ToFA.Android.whmwws.watch.ui.WatchColors
import ToFA.Android.whmwws.watch.ui.WatchI18n
import ToFA.Android.whmwws.watch.ui.WatchThemeMode
import java.io.File

/**
 * 手机端经无线调试通道推送主题色：
 * 1. 把命令写入手表私有外部目录的 files/theme_in.json；
 * 2. 广播本组件，手表在前台时同一进程内直接改配色状态即时刷新；
 * 3. 不在前台也会先落盘（重启仍生效），下次启动读取。
 *
 * 反向「从手表导入」读取 files/theme_out.json，该文件在每次主题变化时刷新。
 */
object ThemeSync {

    const val ACTION_COMMAND = "ToFA.Android.whmwws.watch.THEME_COMMAND"
    private const val COMMAND_FILE = "theme_in.json"

    /** 读取命令文件并应用；处理完删除命令文件，避免重复应用旧指令。 */
    fun applyCommand(context: Context): Boolean {
        val file = File(WatchSettings.rootDir(context), COMMAND_FILE)
        if (!file.isFile) return false
        val json = runCatching { JSONObject(file.readText()) }.getOrNull()
        file.delete()
        if (json == null) return false
        var changed = false
        WatchSettings.parseAccent(json.optString("accent", ""))?.let { accent ->
            WatchSettings.setAccent(context, accent.id)
            WatchColors.setAccent(accent.color)
            changed = true
        }
        val modeId = json.optString("mode", "")
        if (modeId == "light" || modeId == "dark" || modeId == "system") {
            WatchSettings.setThemeModeId(context, modeId)
            val systemDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WatchColors.applyThemeMode(WatchThemeMode.fromId(modeId), systemDark)
            changed = true
        }
        val langId = json.optString("lang", "")
        if (langId.isNotBlank()) {
            val packsJson = json.optString("packs", "")
            if (packsJson.isNotBlank()) {
                WatchSettings.setLanguagePacksJson(context, packsJson)
            }
            WatchSettings.setLanguageId(context, langId)
            WatchI18n.apply(langId, packsJson.ifBlank { null })
            changed = true
        }
        WatchSettings.writeThemeExport(context)
        return changed
    }
}

/** 手机端 push 后的广播入口（manifest 中注册，exported 以允许 adb am broadcast）。 */
class ThemeCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ThemeSync.ACTION_COMMAND) return
        ThemeSync.applyCommand(context.applicationContext)
    }
}
