package ToFA.Android.whmwws.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 单条更新内容，中文与英文成对维护，跟随应用语言切换。 */
private data class ChangelogItem(val zh: String, val en: String)

/** 单个版本：版本号、发布日期、更新条目。 */
private data class ChangelogRelease(
    val version: String,
    val date: String,
    val items: List<ChangelogItem>,
)

/**
 * 版本更新日志。新增版本时在列表顶部追加，并同步仓库根目录 open-source/CHANGELOG.md，
 * 以及 GitHub / Gitee 对应 Release 说明。
 */
private val ToFAChangelog = listOf(
    ChangelogRelease(
        version = "0.2.3",
        date = "2026-11-14",
        items = listOf(
            ChangelogItem(
                "导出方式中的「本机唯一码」更名为「唯一码」，四个选项固定排在同一行。",
                "The export option \"This device's code\" is now \"Device code\"; all four options stay on one row.",
            ),
            ChangelogItem(
                "设置 → 关于新增版本更新日志入口。",
                "Settings → About now has a changelog entry point.",
            ),
        ),
    ),
    ChangelogRelease(
        version = "0.2.2",
        date = "2026-11-14",
        items = listOf(
            ChangelogItem(
                "编辑模式点按条目可查看密钥，密钥只读，点按即可复制。",
                "In edit mode, tap an account to view its secret; the secret is read-only and tapping copies it.",
            ),
        ),
    ),
    ChangelogRelease(
        version = "0.2.1",
        date = "2026-11-14",
        items = listOf(
            ChangelogItem(
                "“传递”更名为“导入导出”，导出可选唯一码、自定义、明文与 TXT。",
                "\"Transfer\" became \"Import / Export\", with device-code, custom, plaintext and TXT export options.",
            ),
            ChangelogItem(
                "TXT 导出每行包含一条标准 otpauth:// 链接。",
                "TXT export writes one standard otpauth:// link per line.",
            ),
            ChangelogItem(
                "主页新增编辑模式：改名、删除确认与长按拖动排序。",
                "New home edit mode: rename, delete with confirmation, long-press reordering.",
            ),
            ChangelogItem(
                "修复导入导出的返回动画方向；系统返回手势返回上一页。",
                "Fixed the reverse animation when leaving Import / Export; the system back gesture returns to the previous page.",
            ),
            ChangelogItem(
                "手表端系统返回手势优先关闭弹层或返回上一页。",
                "On the watch, the system back gesture closes overlays or returns to the previous page first.",
            ),
        ),
    ),
    ChangelogRelease(
        version = "0.2.0",
        date = "2026-11-13",
        items = listOf(
            ChangelogItem(
                "首个开源版本：离线 TOTP 账号管理、加密备份与恢复。",
                "First open-source release: offline TOTP accounts with encrypted backup and restore.",
            ),
            ChangelogItem(
                "手机端支持应用锁、剪贴板保护、手表同步与本地激活。",
                "The phone app adds app lock, clipboard protection, watch sync and local activation.",
            ),
            ChangelogItem(
                "手表端支持圆屏 / 方屏、离线验证码与账号管理。",
                "The watch app supports round and square screens, offline codes and account management.",
            ),
            ChangelogItem(
                "采用 Apache License 2.0 开源发布。",
                "Released under the Apache License 2.0.",
            ),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("版本更新日志", "Changelog")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            ToFAChangelog.forEachIndexed { index, release ->
                Spacer(Modifier.height(if (index == 0) 4.dp else 20.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = release.version,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = release.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        release.items.forEach { item ->
                            Row {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = tr(item.zh, item.en),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
