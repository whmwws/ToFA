package ToFA.Android.whmwws.phone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ToFA.Android.whmwws.phone.AppViewModel
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.imports.PayloadCodec
import ToFA.Android.whmwws.phone.security.DeviceIdentity
import ToFA.Android.whmwws.phone.security.TofaCrypto
import ToFA.Android.whmwws.phone.sync.AdbWatchSync
import ToFA.Android.whmwws.phone.sync.PushKey

/** 安卓手表（Wear OS / Android）ADB 直连：配对、导入、导出、激活、安装。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbSyncScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(vm.adbHost) }
    var port by remember { mutableStateOf(vm.adbPort) }
    val logScroll = rememberScrollState()
    // 日志默认折叠为最后三条，点击标题或日志框展开/收起
    var logExpanded by remember { mutableStateOf(false) }

    var showPushChoice by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showPlainConfirm by remember { mutableStateOf(false) }
    var pushPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var showActivation by remember { mutableStateOf(false) }
    var activationCode by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }

    var pullBusy by remember { mutableStateOf(false) }
    var pullKey by remember { mutableStateOf("") }
    var pullError by remember { mutableStateOf<String?>(null) }
    var pendingPull by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(vm.adbLog, logExpanded) {
        if (logExpanded) logScroll.animateScrollTo(logScroll.maxValue)
    }

    fun save() = vm.saveAdbAddress(host, port, vm.adbPairPort)

    fun requirePort(): Int? {
        val value = vm.adbPort.toIntOrNull()?.takeIf { it in 1..65535 }
        if (value == null) {
            vm.pushKeyMessage(
                I18n.pick("连接端口不合法，请填写无线调试主页显示的端口", "Invalid port. Use the port shown on the wireless debugging screen."),
            )
        }
        return value
    }

    fun finishPull(name: String, accounts: List<TotpAccount>) {
        if (accounts.isEmpty()) {
            vm.pushKeyMessage(I18n.pick("$name 中没有可用密钥", "No usable secrets in $name"))
        } else {
            vm.upsertAll(accounts)
            vm.pushKeyMessage(
                I18n.pick(
                    "已从手表导入 ${accounts.size} 个密钥（$name）",
                    "Imported ${accounts.size} secrets from the watch ($name)",
                ),
            )
        }
    }

    fun importText(name: String, text: String) {
        if (PayloadCodec.isEncrypted(text)) {
            // 手表导出文件只可能用手表唯一码或自定义密码加密，直接询问用户
            pullKey = ""
            pullError = null
            pendingPull = name to text
        } else {
            runCatching { PayloadCodec.decode(text) }
                .onSuccess { finishPull(name, it) }
                .onFailure {
                    val reason = it.message ?: I18n.pick("文件格式不正确", "invalid file format")
                    vm.pushKeyMessage(I18n.pick("解析失败：$reason", "Failed to parse: $reason"))
                }
        }
    }

    fun decryptPull(name: String, text: String, keyText: String) {
        val uniqueCode = DeviceIdentity.normalize(keyText)
        val pass = if (uniqueCode != null) DeviceIdentity.keyOf(uniqueCode) else TofaCrypto.normalizeKey(keyText)
        if (pass.isEmpty()) {
            pullError = I18n.pick("密钥不能为空", "Key cannot be empty")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PayloadCodec.decryptToAccounts(text, pass) }
            }
            pass.fill('\u0000')
            result.onSuccess {
                pendingPull = null
                pullError = null
                finishPull(name, it)
            }.onFailure {
                pullError = I18n.pick(
                    "密钥不正确，请重新输入或扫描手表唯一码",
                    "Wrong key. Re-enter it or scan the watch's unique code.",
                )
            }
        }
    }

    fun pullLatest() {
        if (pullBusy) return
        val targetPort = requirePort() ?: return
        save()
        pullBusy = true
        vm.pushKeyMessage(I18n.pick("读取手表导出目录 …", "Reading the watch export folder …"))
        scope.launch {
            val result = runCatching {
                val files = AdbWatchSync.listExports(context, vm.adbHost, targetPort)
                if (files.isEmpty()) {
                    error(I18n.pick("手表 files/export 目录里没有导出文件", "No export files in the watch's files/export folder"))
                }
                AdbWatchSync.pullExport(context, vm.adbHost, targetPort, files.first())
            }
            pullBusy = false
            result.onSuccess { (name, text) -> importText(name, text) }
                .onFailure {
                    val reason = it.message ?: I18n.pick("未知错误", "unknown error")
                    vm.pushKeyMessage(I18n.pick("读取失败：$reason", "Read failed: $reason"))
                }
        }
    }

    val scanForPush = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            vm.pushKeyMessage(I18n.pick("已取消扫描唯一码", "Unique code scan cancelled"))
            return@rememberLauncherForActivityResult
        }
        val code = DeviceIdentity.normalize(contents)
        if (code == null) {
            vm.pushKeyMessage(I18n.pick("不是有效的 ToFA 唯一码二维码", "Not a valid ToFA unique code QR"))
        } else {
            save()
            vm.adbPushAccounts(PushKey.UniqueCode(code))
        }
    }

    val scanForPullKey = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        val pending = pendingPull
        if (contents.isNullOrBlank() || pending == null) {
            pullError = I18n.pick("已取消扫码", "Scan cancelled")
        } else {
            decryptPull(pending.first, pending.second, contents)
        }
    }

    val scanForActivation = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            activationError = I18n.pick("已取消扫描", "Scan cancelled")
            return@rememberLauncherForActivityResult
        }
        val code = DeviceIdentity.normalize(contents)
        if (code == null) {
            activationError = I18n.pick("不是有效的 ToFA 唯一码二维码", "Not a valid ToFA unique code QR")
        } else {
            activationCode = code
            activationError = null
        }
    }

    fun scanUniqueCode() {
        save()
        scanForPush.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(I18n.pick("扫描手表长按菜单「唯一码」二维码", "Scan the Unique code QR in the watch's long-press menu"))
                setBeepEnabled(false)
                setOrientationLocked(false)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("传递", "Transfer")) },
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
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .scrollProgressBar(scroll, MaterialTheme.colorScheme.primary)
                .verticalScroll(scroll),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("手表端准备", "Prepare the watch"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "手表开启无线调试，端口保持默认 5555，填写手表 IP 即可连接。",
                            "Enable wireless debugging on the watch, keep the default port 5555, and enter the watch's IP.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(tr("手表 IP / 主机", "Watch IP / host")) },
                placeholder = { Text("192.168.1.100") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(tr("连接端口", "Port")) },
                placeholder = { Text("5555") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        save()
                        vm.adbTest()
                    },
                    enabled = !vm.adbBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(tr("连接测试", "Test connection")) }
                OutlinedButton(
                    onClick = {
                        save()
                        vm.adbLaunchWatch()
                    },
                    enabled = !vm.adbBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(tr("启动手表 App", "Launch watch app")) }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { pullLatest() },
                enabled = !pullBusy && !vm.adbBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (pullBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(tr("从手表导入", "Import from watch"))
            }
            Text(
                tr("加密文件需扫唯一码或输入密码。", "Encrypted files need a unique code or password."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { showPushChoice = true },
                enabled = !vm.adbBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (vm.adbBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(tr("导出密钥到手表", "Export secrets to watch"))
            }
            Text(
                tr("写入后手表端自动导入。", "The watch imports them automatically after writing."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    save()
                    vm.adbPushLanguage()
                },
                enabled = !vm.adbBusy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text(tr("同步语言设置", "Sync language")) }
            Text(
                tr(
                    "把手机当前界面语言同步到手表端",
                    "Push the phone's interface language to the watch",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    save()
                    if (!vm.premium.activated) {
                        vm.pushKeyMessage(
                            I18n.pick(
                                "请先在主页完成激活，再激活手表高级功能",
                                "Activate on the home screen before enabling watch premium features.",
                            ),
                        )
                    } else {
                        activationError = null
                        showActivation = true
                    }
                },
                enabled = !vm.adbBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(tr("激活手表高级功能", "Activate watch premium")) }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    save()
                    vm.adbInstallWatch()
                },
                enabled = !vm.adbBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(tr("安装 / 更新手表端 App", "Install / update watch app")) }

            Text(
                tr(
                    "手机与手表需处于同一 Wi-Fi，且手表已开启无线调试。",
                    "Phone and watch must share the same Wi-Fi, and wireless debugging must be on.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (vm.adbLog.isNotBlank()) {
                val logLines = vm.adbLog.trim().lines().filter { it.isNotBlank() }
                // 折叠时只渲染最后三条；展开时渲染全部，日志框高度固定，不随内容撑开
                val logText = if (logExpanded) {
                    logLines.joinToString("\n")
                } else {
                    logLines.takeLast(3).joinToString("\n")
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { logExpanded = !logExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("运行日志", "Runtime log"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (logExpanded) tr("收起", "Collapse") else tr("展开全部", "Show all"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (logExpanded) 220.dp else 72.dp)
                        .clickable { logExpanded = !logExpanded },
                ) {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(logScroll, enabled = logExpanded),
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (showPushChoice) {
        PushKeyChoiceDialog(
            onScanUniqueCode = {
                showPushChoice = false
                scanUniqueCode()
            },
            onPassword = {
                showPushChoice = false
                passwordError = null
                showPassword = true
            },
            onPlain = {
                showPushChoice = false
                showPlainConfirm = true
            },
            onDismiss = { showPushChoice = false },
        )
    }

    if (showPassword) {
        AlertDialog(
            onDismissRequest = { showPassword = false },
            title = { Text(tr("自定义密码", "Custom password")) },
            text = {
                Column {
                    Text(
                        tr("手表导入时需输入相同密码。", "Enter the same password when importing on the watch."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = pushPassword,
                        onValueChange = {
                            pushPassword = it
                            passwordError = null
                        },
                        label = tr("密码（至少 6 位）", "Password (min. 6 characters)"),
                    )
                    passwordError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pushPassword.trim().length < 6) {
                        passwordError = I18n.pick("密码至少 6 位", "Password must be at least 6 characters")
                    } else {
                        save()
                        showPassword = false
                        vm.adbPushAccounts(PushKey.Password(pushPassword))
                    }
                }) { Text(tr("加密推送", "Push encrypted")) }
            },
            dismissButton = { TextButton(onClick = { showPassword = false }) { Text(tr("取消", "Cancel")) } },
        )
    }

    if (showPlainConfirm) {
        AlertDialog(
            onDismissRequest = { showPlainConfirm = false },
            title = { Text(tr("不加密推送？", "Push without encryption?")) },
            text = {
                Text(
                    tr(
                        "明文文件在手表私有目录中可被获得 adb 权限的进程读取。仅建议在开发调试时使用，" +
                            "正式迁移请选择唯一码或自定义密码。",
                        "Plaintext files in the watch's private directory can be read by any process with adb access. " +
                            "Use only for development; for real migrations choose a unique code or custom password.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    save()
                    showPlainConfirm = false
                    vm.adbPushAccounts(PushKey.None)
                }) { Text(tr("继续明文推送", "Push plaintext anyway")) }
            },
            dismissButton = { TextButton(onClick = { showPlainConfirm = false }) { Text(tr("取消", "Cancel")) } },
        )
    }

    if (showActivation) {
        AlertDialog(
            onDismissRequest = { showActivation = false },
            title = { Text(tr("激活手表高级功能", "Activate watch premium")) },
            text = {
                Column {
                    Text(
                        tr(
                            "扫描或输入手表唯一码（可不输入 -），激活文件仅这台手表可解开。",
                            "Scan or enter the watch's unique code (hyphens optional). Only this watch can unlock the activation file.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = activationCode,
                        onValueChange = {
                            activationCode = it
                            activationError = null
                        },
                        label = { Text(tr("手表唯一码", "Watch unique code")) },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    activationError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scanForActivation.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(I18n.pick("扫描手表唯一码二维码", "Scan the watch's unique code QR"))
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            },
                        )
                    }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("扫描手表唯一码", "Scan watch unique code"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val code = DeviceIdentity.normalize(activationCode)
                    if (code == null) {
                        activationError = I18n.pick("唯一码格式不正确", "Invalid unique code format")
                    } else {
                        save()
                        showActivation = false
                        vm.adbPushActivation(code)
                    }
                }) { Text(tr("推送到手表", "Push to watch")) }
            },
            dismissButton = { TextButton(onClick = { showActivation = false }) { Text(tr("取消", "Cancel")) } },
        )
    }

    pendingPull?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = {
                pendingPull = null
                pullError = null
            },
            title = { Text(tr("手表导出文件已加密", "Watch export file is encrypted")) },
            text = {
                Column {
                    Text(
                        tr(
                            "$name 已加密，请扫描唯一码或输入密码（唯一码可不输入 -）。",
                            "$name is encrypted. Scan the unique code or enter the password (hyphens optional).",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = pullKey,
                        onValueChange = {
                            pullKey = it
                            pullError = null
                        },
                        label = tr("密码 / 手表唯一码", "Password / watch unique code"),
                    )
                    pullError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scanForPullKey.launch(
                                ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt(I18n.pick("扫描手表唯一码二维码", "Scan the watch's unique code QR"))
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                },
                            )
                        },
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("扫描手表唯一码", "Scan watch unique code"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { decryptPull(name, text, pullKey) }) { Text(tr("解密导入", "Decrypt and import")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPull = null
                    pullError = null
                }) { Text(tr("取消", "Cancel")) }
            },
        )
    }
}

@Composable
private fun PushKeyChoiceDialog(
    onScanUniqueCode: () -> Unit,
    onPassword: () -> Unit,
    onPlain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("选择导出加密方式", "Choose export encryption")) },
        text = {
            Column {
                ChoiceRow(
                    title = tr("唯一码加密", "Unique code"),
                    desc = tr("手表用自身唯一码解开", "The watch unlocks it with its own unique code"),
                    onClick = onScanUniqueCode,
                )
                ChoiceRow(
                    title = tr("自定义密码", "Custom password"),
                    desc = tr("手表导入时输入相同密码", "Enter the same password when importing on the watch"),
                    onClick = onPassword,
                )
                ChoiceRow(
                    title = tr("无密码（明文）", "No password (plaintext)"),
                    desc = tr("不加密，仅建议开发调试使用", "Unencrypted; for development only"),
                    onClick = onPlain,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

@Composable
private fun ChoiceRow(title: String, desc: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
