package ToFA.Android.whmwws.phone.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 单个第三方库：名称、随包版本、许可证名称、项目主页。 */
private data class PhoneLicenseLib(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
)

/** 按许可证分组；asset 为空表示全文不随包分发。 */
private data class PhoneLicenseGroup(
    val name: String,
    val asset: String?,
    val note: String,
    val libs: List<PhoneLicenseLib>,
    val enNote: String = "",
)

/** ToFA 自身许可证说明（全文见 assets/licenses/apache-2.0.txt 与仓库根目录 LICENSE）。 */
private const val ToFaLicenseNote =
    "ToFA 选择 Apache License 2.0 发布。\n\n" +
        "理由：\n" +
        "· 允许他人自由使用、修改、再分发，也能闭源分发衍生版本，便于手机端 / 手表端各自分发；\n" +
        "· 含显式专利授权条款，对安全类工具更稳妥；\n" +
        "· 保留商标条款，他人可在自己分支上修改代码，但不能冒用 ToFA 名称；\n" +
        "· 与全部第三方依赖（Apache-2.0 / MIT）兼容，可直接并入应用商店分发。\n\n" +
        "分发或修改时请保留本许可证与版权声明；若修改了文件，需在文件中注明改动。"

private const val ToFaLicenseNoteEn =
    "ToFA is released under the Apache License 2.0.\n\n" +
        "Rationale:\n" +
        "· Others may freely use, modify and redistribute it, including closed-source derivatives, so the phone and watch apps can be distributed separately;\n" +
        "· It includes an explicit patent grant, which is safer for a security tool;\n" +
        "· The trademark clause is kept: others may modify the code in their own fork but may not use the ToFA name;\n" +
        "· It is compatible with all third-party dependencies (Apache-2.0 / MIT) and can ship directly on app stores.\n\n" +
        "When redistributing or modifying, keep this license and the copyright notice; if you change a file, note the change in that file."

/**
 * 手机端实际依赖（app/build.gradle.kts 与 debugRuntimeClasspath 解析结果）。
 * JUnit / org.json 等仅测试期依赖不随 APK 分发，未列入。
 */
private val PhoneLicenseGroups = listOf(
    PhoneLicenseGroup(
        name = "Apache-2.0",
        asset = "apache-2.0.txt",
        note = "",
        libs = listOf(
            PhoneLicenseLib(
                "AndroidX Compose UI / Foundation", "1.7.3", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/compose-ui",
            ),
            PhoneLicenseLib(
                "AndroidX Compose Material3", "1.3.0", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/compose-material3",
            ),
            PhoneLicenseLib(
                "AndroidX Compose Material Icons Extended", "1.7.3", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/compose-material",
            ),
            PhoneLicenseLib(
                "AndroidX AppCompat", "1.7.0", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/appcompat",
            ),
            PhoneLicenseLib(
                "AndroidX Core KTX", "1.13.1", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/core",
            ),
            PhoneLicenseLib(
                "AndroidX Activity Compose", "1.9.2", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/activity",
            ),
            PhoneLicenseLib(
                "AndroidX Lifecycle ViewModel Compose", "2.8.6", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            ),
            PhoneLicenseLib(
                "AndroidX Lifecycle Runtime Compose", "2.8.6", "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            ),
            PhoneLicenseLib(
                "Kotlin Standard Library", "2.1.0-RC2", "Apache-2.0",
                "https://github.com/JetBrains/kotlin",
            ),
            PhoneLicenseLib(
                "Kotlinx Coroutines Android", "1.8.1", "Apache-2.0",
                "https://github.com/Kotlin/kotlinx.coroutines",
            ),
            PhoneLicenseLib(
                "ZXing Core", "3.5.3", "Apache-2.0",
                "https://github.com/zxing/zxing",
            ),
            PhoneLicenseLib(
                "JourneyApps ZXing Android Embedded", "4.3.0", "Apache-2.0",
                "https://github.com/journeyapps/zxing-android-embedded",
            ),
            PhoneLicenseLib(
                "Kadb", "1.2.1", "Apache-2.0",
                "https://github.com/flyfishxu/Kadb",
            ),
        ),
    ),
    PhoneLicenseGroup(
        name = "MIT（Bouncy Castle Licence）",
        asset = "bouncy-castle.txt",
        note = "",
        libs = listOf(
            PhoneLicenseLib(
                "Bouncy Castle bcprov-jdk18on", "1.79", "MIT",
                "https://www.bouncycastle.org",
            ),
            PhoneLicenseLib(
                "Bouncy Castle bcpkix-jdk18on", "1.79", "MIT",
                "https://www.bouncycastle.org",
            ),
            PhoneLicenseLib(
                "Bouncy Castle bcutil-jdk18on", "1.79", "MIT",
                "https://www.bouncycastle.org",
            ),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // -2 = 列表页；-1 = ToFA 自身许可证；>=0 = 第三方分组全文
    var page by remember { mutableIntStateOf(-2) }

    if (page != -2) {
        val title = if (page == -1) tr("ToFA 许可证", "ToFA License") else PhoneLicenseGroups[page].name
        val text = if (page == -1) {
            tr(ToFaLicenseNote, ToFaLicenseNoteEn) + "\n\n" + readAsset(context, "apache-2.0.txt").orEmpty()
        } else {
            val group = PhoneLicenseGroups[page]
            group.asset?.let { readAsset(context, it) } ?: tr(group.note, group.enNote)
        }
        LicenseTextPage(title = title, text = text, onBack = { page = -2 })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("开源许可", "Open source licenses")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .scrollProgressBar(scroll, MaterialTheme.colorScheme.primary)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 本项目许可证
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { page = -1 }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        tr("ToFA 项目许可 · Apache License 2.0", "ToFA project license · Apache License 2.0"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(
                            "允许修改与再分发，含专利授权与商标保护条款；点击查看说明与许可证全文",
                            "Allows modification and redistribution, with patent and trademark clauses; tap to view the notice and full license text",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PhoneLicenseGroups.forEachIndexed { index, group ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { page = index }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = tr(
                                    "${group.name} · ${group.libs.size} 个库",
                                    "${group.name} · ${group.libs.size} libraries",
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                tr("查看全文", "View full text"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (group.asset == null) {
                            Text(
                                text = tr(group.note, group.enNote),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        group.libs.forEach { lib ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "${lib.name}  ${lib.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${lib.license} · ${lib.url}",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(lib.url),
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun readAsset(context: android.content.Context, name: String): String? =
    runCatching {
        context.assets.open("licenses/$name").use { it.readBytes().decodeToString() }
    }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseTextPage(title: String, text: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .scrollProgressBar(scroll, MaterialTheme.colorScheme.primary)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
