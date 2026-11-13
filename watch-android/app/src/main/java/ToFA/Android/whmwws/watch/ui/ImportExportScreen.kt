package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.data.WatchSettings
import ToFA.Android.whmwws.watch.imports.AccountImporter
import ToFA.Android.whmwws.watch.imports.PayloadCodec
import ToFA.Android.whmwws.watch.security.DeviceIdentity
import ToFA.Android.whmwws.watch.security.TofaCrypto
import java.io.File

/**
 * 导入 / 导出：手动输入、导入、导出、删除导出文件。
 * 导出默认用唯一码加密，可选自定义密码或不加密。
 */
@Composable
fun ImportExportScreen(
    accounts: List<TotpAccount>,
    activated: Boolean,
    onManualInput: () -> Unit,
    onImported: (List<TotpAccount>) -> Unit,
    onNeedActivation: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportDir = remember { WatchSettings.exportDir(context) }
    val scroll = rememberScrollState()

    var status by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var askingPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var importedCount by remember { mutableIntStateOf(0) }
    var askDelete by remember { mutableStateOf(false) }
    var showExportChooser by remember { mutableStateOf(false) }
    var exportCustom by remember { mutableStateOf(false) }
    var confirmClearFiles by remember { mutableStateOf<Int?>(null) }
    var askRemoveImported by remember { mutableStateOf(false) }

    fun exportFileCount(): Int = File(exportDir).listFiles()?.size ?: 0

    fun startImport() {
        if (working) return
        working = true
        status = WatchI18n.pick("读取中…", "Reading…")
        passwordError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = AccountImporter.latestFile(context)
                        ?: throw IllegalStateException(WatchI18n.pick("未找到文件", "File not found"))
                    val text = file.readText(Charsets.UTF_8)
                    val codeKey = DeviceIdentity.keyOf(DeviceIdentity.current(context))
                    if (PayloadCodec.isEncrypted(text)) {
                        ImportCheck(file, runCatching { PayloadCodec.decryptToAccounts(text, codeKey) }.getOrNull())
                    } else {
                        ImportCheck(file, parsePlain(text))
                    }
                }
            }
            result.onSuccess { check ->
                val imported = check.accounts
                if (imported == null) {
                    pendingFile = check.file
                    askingPassword = true
                    status = WatchI18n.pick("需要密码", "Password required")
                    working = false
                } else if (imported.isEmpty()) {
                    status = WatchI18n.pick("没有可用密钥", "No usable keys")
                    working = false
                } else {
                    onImported(imported)
                    pendingFile = check.file
                    importedCount = imported.size
                    askRemoveImported = true
                    status = WatchI18n.pick("已导入 ${imported.size} 个密钥", "Imported ${imported.size} keys")
                    working = false
                }
            }.onFailure {
                status = it.message ?: WatchI18n.pick("导入失败", "Import failed")
                working = false
            }
        }
    }

    fun doExport(mode: ExportMode, secret: CharArray?) {
        if (working) return
        working = true
        exporting = true
        status = WatchI18n.pick("导出中…", "Exporting…")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    require(accounts.isNotEmpty()) { WatchI18n.pick("没有可导出的密钥", "No keys to export") }
                    val plain = PayloadCodec.encodePlain(accounts)
                    val text = when (mode) {
                        ExportMode.NoEncryption -> plain
                        ExportMode.UniqueCode ->
                            PayloadCodec.encodeEncrypted(accounts, DeviceIdentity.keyOf(DeviceIdentity.current(context)))
                        ExportMode.CustomPassword -> {
                            val key = secret ?: throw IllegalArgumentException(WatchI18n.pick("密码不能为空", "Password is required"))
                            PayloadCodec.encodeEncrypted(accounts, key)
                        }
                    }
                    val dir = File(exportDir).apply { mkdirs() }
                    val name = "tofa-export-${System.currentTimeMillis()}.json"
                    File(dir, name).apply { writeText(text, Charsets.UTF_8) }
                }
            }
            result.onSuccess { status = WatchI18n.pick("已导出 ${accounts.size} 个密钥", "Exported ${accounts.size} keys") }
                .onFailure { status = it.message ?: WatchI18n.pick("导出失败", "Export failed") }
            // 导出完成后才允许离开导出流程：期间顶栏/按钮/表冠全部被模态层屏蔽
            exporting = false
            working = false
            showExportChooser = false
        }
    }

    if (askingPassword) {
        PasswordPadScreen(
            title = tr("导入密码", "Import password"),
            subtitle = passwordError,
            confirmLabel = tr("解密并导入", "Decrypt & import"),
            onCancel = {
                askingPassword = false
                passwordError = null
            },
            onConfirm = { password ->
                val file = pendingFile
                if (file == null) {
                    askingPassword = false
                } else {
                    scope.launch {
                        working = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val text = file.readText(Charsets.UTF_8)
                                val list = PayloadCodec.decryptToAccounts(text, TofaCrypto.normalizeKey(password))
                                if (list.isEmpty()) throw IllegalArgumentException(WatchI18n.pick("没有可用密钥", "No usable keys"))
                                list
                            }
                        }
                        result.onSuccess { list ->
                            onImported(list)
                            pendingFile = file
                            importedCount = list.size
                            askingPassword = false
                            passwordError = null
                            askRemoveImported = true
                            status = WatchI18n.pick("已导入 ${list.size} 个密钥", "Imported ${list.size} keys")
                        }.onFailure {
                            passwordError = WatchI18n.pick("密码不正确", "Wrong password")
                        }
                        working = false
                    }
                }
            },
        )
        return
    }

    if (exportCustom) {
        PasswordPadScreen(
            title = tr("导出密码", "Export password"),
            subtitle = null,
            confirmLabel = tr("保存", "Save"),
            onCancel = { exportCustom = false },
            onConfirm = { password ->
                exportCustom = false
                doExport(ExportMode.CustomPassword, TofaCrypto.normalizeKey(password))
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("transfer")) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题栏固定在顶部，不随下方内容与状态文字位移；
            // 导出方式浮层打开时，点顶部退出区先关闭浮层、留在传递页，而不是退出到菜单。
            WatchPageHeader(
                title = tr("传递", "Transfer"),
                onBack = { if (exporting) Unit else if (showExportChooser) showExportChooser = false else onBack() },
            )
            Spacer(Modifier.height(2.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val minContentHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minContentHeight)
                        .watchScrollProgress(scroll)
                        .verticalScroll(scroll)
                        .watchRotaryScroll(scroll, "transfer"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    WatchButton(
                        text = tr("手动输入", "Manual input"),
                        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                        enabled = !working,
                    ) { onManualInput() }
                    Spacer(Modifier.height(3.dp))
                    WatchOutlinedButton(
                        text = if (working) tr("处理中…", "Working…") else tr("导入", "Import"),
                        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                        enabled = !working,
                    ) { startImport() }
                    Spacer(Modifier.height(3.dp))
                    WatchOutlinedButton(
                        text = tr("导出", "Export"),
                        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                        enabled = !working,
                    ) {
                        if (!activated) onNeedActivation() else showExportChooser = true
                    }
                    Spacer(Modifier.height(3.dp))
                    WatchOutlinedButton(
                        text = tr("删除导出文件", "Delete exports"),
                        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                        enabled = !working,
                    ) {
                        confirmClearFiles = exportFileCount()
                    }
                    Spacer(Modifier.height(3.dp))

                    // 固定高度状态区：出现提示时不挤压按钮、不推动标题栏（长文案折两行，区内自动增高）
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 17.dp).circularEdgePadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (status.isNotEmpty()) {
                            Text(
                                text = status,
                                color = if (
                                    status.contains("失败") || status.contains("未找到") ||
                                    status.contains("failed", true) || status.contains("not found", true)
                                ) WatchColors.Error else WatchColors.TextDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // 圆形表盘底部横向空间很窄，长句折成两行，避免被圆边裁掉
                    Column(
                        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = tr("请搭配手机端使用", "Use with the phone app"),
                            color = WatchColors.TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (showExportChooser) {
            ExportChooser(
                count = accounts.size,
                onUniqueCode = { doExport(ExportMode.UniqueCode, null) },
                onCustomPassword = { exportCustom = true },
                onNoEncryption = { doExport(ExportMode.NoEncryption, null) },
                onCancel = { showExportChooser = false },
            )
        }

        if (askRemoveImported) {
            ConfirmDialog(
                title = tr("已导入 $importedCount 个密钥", "Imported $importedCount keys"),
                message = tr(
                    "删除源文件？\n${pendingFile?.name ?: ""}",
                    "Delete source file?\n${pendingFile?.name ?: ""}",
                ),
                confirmLabel = tr("删除", "Delete"),
                cancelLabel = tr("保留", "Keep"),
                onConfirm = {
                    pendingFile?.let { f ->
                        scope.launch { withContext(Dispatchers.IO) { runCatching { f.delete() } } }
                    }
                    askRemoveImported = false
                    pendingFile = null
                },
                onCancel = {
                    askRemoveImported = false
                    pendingFile = null
                },
            )
        }

        confirmClearFiles?.let { count ->
            ConfirmDialog(
                title = tr("删除导出文件？", "Delete exports?"),
                message = if (count == 0) {
                    tr("导出目录里没有文件", "No files in the export folder")
                } else {
                    tr("将删除 export 目录下 $count 个文件", "Delete $count files in export folder")
                },
                confirmLabel = tr("删除", "Delete"),
                cancelLabel = tr("取消", "Cancel"),
                confirmEnabled = count > 0,
                onConfirm = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(exportDir).listFiles()?.forEach { runCatching { it.delete() } }
                        }
                        confirmClearFiles = null
                        status = WatchI18n.pick("已删除导出文件", "Exports deleted")
                    }
                },
                onCancel = { confirmClearFiles = null },
            )
        }

        // 导出期间模态层：屏蔽顶部退出区、底层按钮与表冠，导出结束才撤掉
        if (exporting) {
            ExportingOverlay(count = accounts.size)
        }
    }
    }
}

private enum class ExportMode { UniqueCode, CustomPassword, NoEncryption }

/** 导出模态层：吞掉点击、拖动与表冠事件，保证导出过程不可被中断。 */
@Composable
private fun ExportingOverlay(count: Int) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchColors.Scrim)
            .pointerInput(Unit) { detectTapGestures { } }
            .onRotaryScrollEvent { true }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                color = WatchColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = tr("正在导出 $count 个密钥…", "Exporting $count keys…"),
                color = WatchColors.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExportChooser(
    count: Int,
    onUniqueCode: () -> Unit,
    onCustomPassword: () -> Unit,
    onNoEncryption: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchColors.Scrim)
            // 浮层自身消费空白区域的点击，避免穿透到底下的传递页按钮。
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            // 标题保持「导出方式」；顶部退出区沿用页面标题栏的点击行为：关闭浮层、返回传递页。
            WatchPageHeader(title = tr("导出方式", "Export options"), onBack = onCancel)
            Spacer(Modifier.height(2.dp))

            // 与「设置」菜单、传递页同一套排布：标题栏下方 2dp 起始，行高 28dp、3dp 行间距完全一致。
            // 底部预留 48dp，对应菜单页版本号/返回提示占用的区域，
            // 使四颗胶囊在可视区内与菜单四行同起始、同节距（首行中心约 y=116px）。
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 48.dp),
            ) {
                val minContentHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minContentHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 与三个选项同高度的信息胶囊：显示当前待导出的密钥数量（唯一高亮项）
                    WatchCountCapsule(
                        text = tr("当前 $count 个密钥", "$count keys now"),
                        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                    )
                    Spacer(Modifier.height(3.dp))
                    // 三个选项全部为普通描边样式，与「自定义密码」「不加密」完全一致，
                    // 强调层级只保留第一颗信息胶囊。
                    WatchOutlinedButton(tr("唯一码加密", "Unique-code encrypted"), Modifier.fillMaxWidth().circularEdgePadding()) { onUniqueCode() }
                    Spacer(Modifier.height(3.dp))
                    WatchOutlinedButton(tr("自定义密码", "Custom password"), Modifier.fillMaxWidth().circularEdgePadding()) { onCustomPassword() }
                    Spacer(Modifier.height(3.dp))
                    WatchOutlinedButton(tr("不加密", "No encryption"), Modifier.fillMaxWidth().circularEdgePadding()) { onNoEncryption() }
                }
            }
        }

        // 选项下方固定高度提示：不参与上方内容排布，胶囊起始与节距保持不变，
        // 文字有无（这里始终显示）都不改变页面高度与按钮位置。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .heightIn(min = 18.dp)
                .circularEdgePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tr("推荐使用唯一码加密", "Unique code recommended"),
                color = WatchColors.TextDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 只读信息胶囊：最小高度与选项按钮一致（28dp），不参与点击与焦点；用主题色实底与「手动输入」主按钮同一强调层级。 */
@Composable
private fun WatchCountCapsule(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(WatchColors.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = WatchColors.OnAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchColors.Scrim)
            // 弹层期间吞掉所有触控，避免误触到底层列表或按钮
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                color = WatchColors.Text,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = WatchColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchButton(confirmLabel, enabled = confirmEnabled) { onConfirm() }
                WatchOutlinedButton(cancelLabel) { onCancel() }
            }
        }
    }
}

private fun parsePlain(text: String): List<TotpAccount> {
    val raw = text.trim().removePrefix("\uFEFF").trimStart()
    return if (raw.startsWith("[") || raw.startsWith("{")) {
        PayloadCodec.decode(raw)
    } else {
        AccountImporter.parse(raw)
    }
}

private data class ImportCheck(val file: File, val accounts: List<TotpAccount>?)
