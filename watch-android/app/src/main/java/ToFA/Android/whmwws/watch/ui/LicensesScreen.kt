package ToFA.Android.whmwws.watch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 随手表 APK 分发的第三方库（版本取自 compose-bom 2024.09.03 与 build.gradle.kts）。 */
private data class WatchLib(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
)

private val WatchLibs = listOf(
    WatchLib("AndroidX Compose UI", "1.7.3", "Apache-2.0", "developer.android.com/jetpack/androidx/releases/compose-ui"),
    WatchLib("Compose Foundation", "1.7.3", "Apache-2.0", "developer.android.com/jetpack/androidx/releases/compose-foundation"),
    WatchLib("Compose Material3", "1.3.0", "Apache-2.0", "developer.android.com/jetpack/androidx/releases/compose-material3"),
    WatchLib("AndroidX Core KTX", "1.13.1", "Apache-2.0", "developer.android.com/jetpack/androidx/releases/core"),
    WatchLib("AndroidX Activity Compose", "1.9.2", "Apache-2.0", "developer.android.com/jetpack/androidx/releases/activity"),
    WatchLib("Lifecycle Runtime Compose", "2.8.6", "Apache-2.0", "developer.android.com/jetpack/androidx/releases/lifecycle"),
    WatchLib("Kotlin Standard Library", "2.1.0", "Apache-2.0", "github.com/JetBrains/kotlin"),
    WatchLib("Kotlinx Coroutines", "1.8.1", "Apache-2.0", "github.com/Kotlin/kotlinx.coroutines"),
    WatchLib("ZXing Core", "3.5.3", "Apache-2.0", "github.com/zxing/zxing"),
)

private const val ToFaWatchNote =
    "ToFA 以 Apache License 2.0 发布：允许使用、修改与再分发，含专利授权与商标保护条款；" +
        "修改文件需注明改动，再分发需保留许可证与版权声明。\n\n"

private const val ToFaWatchNoteEn =
    "ToFA is released under the Apache License 2.0: use, modification and redistribution are permitted, " +
        "with patent grant and trademark protection clauses; modified files must be marked as changed, " +
        "and redistributions must retain the license and copyright notices.\n\n"

/**
 * 开源许可：列表页（ToFA 自身许可 + 各第三方库的名称/版本/许可证/主页），
 * 点条目进入全文；圆屏沿用「窄弦长正文宽」的排版结论。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apache = remember {
        runCatching {
            context.assets.open("licenses/apache-2.0.txt").use { it.readBytes().decodeToString() }
        }.getOrNull().orEmpty()
    }
    // 0 列表 / 1 ToFA 许可 / 2 Apache 全文
    var page by remember { mutableIntStateOf(0) }
    when (page) {
        1 -> WatchLicenseText(
            tr("ToFA 许可", "ToFA License"),
            tr(ToFaWatchNote, ToFaWatchNoteEn) + apache,
            onBack = { page = 0 },
        )
        2 -> WatchLicenseText("Apache-2.0", apache, onBack = { page = 0 })
        else -> WatchLicenseList(
            onBack = onBack,
            openToFa = { page = 1 },
            openApache = { page = 2 },
            openUrl = { url ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://$url"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}

@Composable
private fun WatchLicenseList(
    onBack: () -> Unit,
    openToFa: () -> Unit,
    openApache: () -> Unit,
    openUrl: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val round = isRoundScreen()
    Column(modifier = Modifier.fillMaxSize()) {
        WatchPageHeader(title = tr("开源许可", "Open source licenses"), onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).watchRotaryBounce("license-list")) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .watchScrollProgress(scroll)
                    .verticalScroll(scroll)
                    .watchRotaryScroll(scroll, "license-list"),
            ) {
                Spacer(Modifier.height(if (round) 14.dp else 6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (round) 26.dp else 14.dp),
                ) {
                    Text(
                        text = "ToFA · Apache-2.0",
                        color = WatchColors.Accent,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = openToFa),
                    )
                    Text(
                        text = tr("本项目许可证说明与全文 ›", "This project's license & full text ›"),
                        color = WatchColors.TextFaint,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.clickable(onClick = openToFa),
                    )
                    Spacer(Modifier.height(8.dp))
                    WatchLibs.forEach { lib ->
                        Text(
                            text = "${lib.name} ${lib.version}",
                            color = WatchColors.Text,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${lib.license} · ${lib.url}",
                            color = WatchColors.TextFaint,
                            fontSize = 8.sp,
                            lineHeight = 11.sp,
                            modifier = Modifier.clickable { lib.url.let(openUrl) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = tr("Apache-2.0 全文 ›", "Full Apache-2.0 text ›"),
                        color = WatchColors.Accent,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.clickable(onClick = openApache),
                    )
                }
                Spacer(Modifier.height(if (round) 46.dp else 12.dp))
            }
        }
    }
}

@Composable
private fun WatchLicenseText(title: String, text: String, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    val round = isRoundScreen()
    // 原文用行首空格做居中排版，小屏放大字号后仅规范化缩进（最多 4 个），文字一字不改。
    val displayText = remember(text) {
        text.lineSequence().joinToString("\n") { line ->
            val indent = line.takeWhile { it == ' ' }.length
            " ".repeat(minOf(indent, 4)) + line.trimStart(' ')
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        WatchPageHeader(title = title, onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).watchRotaryBounce("license-text")) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .watchScrollProgress(scroll)
                    .verticalScroll(scroll)
                    .watchRotaryScroll(scroll, "license-text"),
            ) {
                Spacer(Modifier.height(if (round) 18.dp else 8.dp))
                Text(
                    text = displayText,
                    color = WatchColors.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (round) 26.dp else 16.dp),
                )
                Spacer(Modifier.height(if (round) 46.dp else 12.dp))
            }
        }
    }
}
