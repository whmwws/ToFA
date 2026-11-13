package ToFA.Android.whmwws.watch.imports

import android.content.Context
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.data.WatchSettings
import ToFA.Android.whmwws.watch.ui.WatchI18n
import java.io.File

/**
 * 从手表导入目录读取账号，配合 adb 推送：
 *
 *   adb push accounts.json .../files/import/accounts.json
 *
 * 支持加密信封、JSON 数组和每行一条 otpauth:// 链接的文本。
 */
object AccountImporter {

    sealed interface Result {
        data class Success(val accounts: List<TotpAccount>) : Result
        data class Failure(val message: String) : Result
    }

    private val fileNames = listOf(
        "tofa-export.json",
        "accounts.json",
        "tfa_accounts.json",
        "export.json",
        "accounts.txt",
    )

    /** 手机 adb 推送落点：仅读取导入目录 */
    fun latestFile(context: Context): File? {
        val dir = File(WatchSettings.importDir(context))
        if (!dir.exists()) return null
        return fileNames
            .map { File(dir, it) }
            .firstOrNull { it.exists() && it.length() > 0 }
    }

    fun import(context: Context): Result {
        val file = latestFile(context)
            ?: return Result.Failure(WatchI18n.pick("未找到文件，先执行 adb push", "No file found, run adb push first"))

        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            ?: return Result.Failure(WatchI18n.pick("文件读取失败", "Failed to read file"))

        val accounts = parse(text)
        return if (accounts.isEmpty()) Result.Failure(WatchI18n.pick("文件里没有可用账号", "No usable accounts in file")) else Result.Success(accounts)
    }

    fun parse(text: String): List<TotpAccount> {
        val raw = text.trim().removePrefix("\uFEFF")
        if (raw.startsWith("[")) {
            return TotpAccount.listFromJson(raw)
        }
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                runCatching {
                    if (OtpauthUri.looksLikeOtpauth(line)) OtpauthUri.parse(line) else null
                }.getOrNull()
            }
    }
}
