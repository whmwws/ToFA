package ToFA.Android.whmwws.phone.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ToFA.Android.whmwws.phone.AppViewModel
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.imports.PayloadCodec
import ToFA.Android.whmwws.phone.security.DeviceIdentity

private data class ImportedFile(val uri: Uri, val count: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportExportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uniqueCode = remember { DeviceIdentity.current(context) }
    var exportMode by remember { mutableStateOf(ExportMode.UniqueCode) }
    var requestedExportMode by remember { mutableStateOf(ExportMode.UniqueCode) }
    var pendingPlainExport by remember { mutableStateOf<ExportMode?>(null) }
    var password by remember { mutableStateOf("") }
    var importKey by remember { mutableStateOf("") }
    var pendingEncrypted by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<ImportedFile?>(null) }
    var busy by remember { mutableStateOf(false) }

    // 文件写入期间消费系统返回，配合模态进度层避免导出被中断。
    BackHandler(enabled = busy) { }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun finishImport(uri: Uri, accounts: List<TotpAccount>) {
        if (accounts.isEmpty()) {
            toast(I18n.pick("文件里没有可用密钥", "No usable secrets in this file"))
            return
        }
        vm.upsertAll(accounts)
        imported = ImportedFile(uri, accounts.size)
    }

    fun tryDecrypt(uri: Uri, text: String, keyText: String) {
        val key = DeviceIdentity.normalize(keyText)
        val passphrase = if (key != null) DeviceIdentity.keyOf(key) else TofaCryptoKey(keyText)
        if (passphrase.isEmpty()) {
            passwordError = I18n.pick("密钥不能为空", "Key cannot be empty")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PayloadCodec.decryptToAccounts(text, passphrase) }
            }
            passphrase.fill('\u0000')
            result.onSuccess {
                pendingEncrypted = null
                passwordError = null
                finishImport(uri, it)
            }.onFailure {
                passwordError = I18n.pick("密钥不正确，请重新输入或扫描设备唯一码", "Wrong key. Re-enter it or scan the device's unique code.")
            }
        }
    }

    fun handlePickedFile(uri: Uri) {
        busy = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size <= MAX_IMPORT_BYTES) {
                            I18n.pick("文件过大，疑似不是导出文件", "File too large; this may not be an export file")
                        }
                        String(bytes, Charsets.UTF_8)
                    } ?: error(I18n.pick("无法读取该文件", "Cannot read this file"))
                }
            }
            busy = false
            text.onSuccess { raw ->
                if (PayloadCodec.isEncrypted(raw)) {
                    // 先尝试本机唯一码；失败再让用户输入/扫描唯一码
                    val local = DeviceIdentity.keyOf(DeviceIdentity.current(context))
                    val auto = runCatching { PayloadCodec.decryptToAccounts(raw, local) }.getOrNull()
                    if (auto != null) {
                        finishImport(uri, auto)
                    } else {
                        pendingEncrypted = uri to raw
                        passwordError = null
                    }
                } else {
                    runCatching { PayloadCodec.decode(raw) }
                        .onSuccess { finishImport(uri, it) }
                        .onFailure { e -> toast(e.message ?: I18n.pick("导入失败", "Import failed")) }
                }
            }.onFailure { toast(it.message ?: I18n.pick("读取文件失败", "Failed to read file")) }
        }
    }

    fun handleExportUri(uri: Uri?) {
        if (uri == null) {
            toast(I18n.pick("已取消导出", "Export cancelled"))
            return
        }
        val accounts = vm.repo.accounts.value
        if (accounts.isEmpty()) {
            toast(I18n.pick("还没有密钥可导出", "No secrets to export"))
            return
        }
        busy = true
        val mode = requestedExportMode
        scope.launch {
            val payload = when (mode) {
                ExportMode.UniqueCode ->
                    PayloadCodec.encodeEncrypted(accounts, DeviceIdentity.keyOf(DeviceIdentity.current(context)))
                ExportMode.CustomKey -> {
                    val key = TofaCryptoKey(password)
                    if (key.isEmpty()) {
                        busy = false
                        toast(I18n.pick("请先输入自定义密钥", "Enter a custom key first"))
                        return@launch
                    }
                    PayloadCodec.encodeEncrypted(accounts, key)
                }
                ExportMode.Plain -> PayloadCodec.encodePlain(accounts)
                ExportMode.Txt -> PayloadCodec.encodeOtpauthLines(accounts)
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error(I18n.pick("无法写入文件", "Cannot write file"))
                }
            }
            busy = false
            result.onSuccess {
                toast(I18n.pick("已导出 ${accounts.size} 个密钥", "Exported ${accounts.size} secrets"))
            }
                .onFailure { toast(it.message ?: I18n.pick("导出失败", "Export failed")) }
        }
    }

    val createJsonDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> handleExportUri(uri) }
    val createTextDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> handleExportUri(uri) }

    fun launchExport(mode: ExportMode) {
        requestedExportMode = mode
        if (mode == ExportMode.Txt) {
            createTextDocument.launch("tofa-export.txt")
        } else {
            createJsonDocument.launch("tofa-export.json")
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        handlePickedFile(uri)
    }

    val scanKey = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            passwordError = I18n.pick("已取消扫码", "Scan cancelled")
            return@rememberLauncherForActivityResult
        }
        val pending = pendingEncrypted ?: return@rememberLauncherForActivityResult
        tryDecrypt(pending.first, pending.second, contents)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("导入导出", "Import / Export")) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
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
                .padding(horizontal = 16.dp)
                .scrollProgressBar(scroll, MaterialTheme.colorScheme.primary)
                .verticalScroll(scroll),
        ) {
            Spacer(Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("导出", "Export"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = exportMode == ExportMode.UniqueCode,
                            onClick = { exportMode = ExportMode.UniqueCode },
                            enabled = !busy,
                            label = { Text(tr("本机唯一码", "This device's code")) },
                        )
                        FilterChip(
                            selected = exportMode == ExportMode.CustomKey,
                            onClick = { exportMode = ExportMode.CustomKey },
                            enabled = !busy,
                            label = { Text(tr("自定义", "Custom")) },
                        )
                        FilterChip(
                            selected = exportMode == ExportMode.Plain,
                            onClick = { exportMode = ExportMode.Plain },
                            enabled = !busy,
                            label = { Text(tr("明文", "Plaintext")) },
                        )
                        FilterChip(
                            selected = exportMode == ExportMode.Txt,
                            onClick = { exportMode = ExportMode.Txt },
                            enabled = !busy,
                            label = { Text("TXT") },
                        )
                    }
                    when (exportMode) {
                        ExportMode.UniqueCode -> {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = uniqueCode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ExportMode.CustomKey -> {
                            Spacer(Modifier.height(10.dp))
                            PasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = tr("自定义密钥（至少 6 位）", "Custom key (min. 6 characters)"),
                            )
                        }
                        ExportMode.Plain -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("仅建议本机临时使用", "For temporary local use only"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        ExportMode.Txt -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("每行一条 otpauth:// 链接，包含明文密钥", "One otpauth:// link per line; contains plaintext secrets"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (exportMode == ExportMode.CustomKey &&
                                TofaCryptoKey(password).size < MIN_PASSWORD_LENGTH
                            ) {
                                toast(
                                    I18n.pick(
                                        "自定义密钥至少 $MIN_PASSWORD_LENGTH 位",
                                        "Custom key must be at least $MIN_PASSWORD_LENGTH characters",
                                    ),
                                )
                                return@Button
                            }
                            if (exportMode == ExportMode.Plain || exportMode == ExportMode.Txt) {
                                pendingPlainExport = exportMode
                            } else {
                                launchExport(exportMode)
                            }
                        },
                        enabled = !busy && vm.repo.accounts.value.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(tr("导出文件", "Export file")) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("导入", "Import"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("加密文件需密码或唯一码", "Encrypted files need a password or unique code"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(tr("选择文件导入", "Choose file to import")) }
                    if (busy) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(tr("处理中…", "Working…"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(30.dp))
        }
    }

    pendingPlainExport?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingPlainExport = null },
            title = { Text(tr("确认明文导出", "Confirm plaintext export")) },
            text = {
                Text(
                    tr(
                        "导出的文件包含完整明文密钥。任何获得文件的人都可能生成你的验证码，请确认仅保存在可信位置。",
                        "The exported file contains full plaintext secrets. Anyone with the file may generate your codes. Store it only in a trusted location.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPlainExport = null
                    launchExport(mode)
                }) { Text(tr("继续导出", "Continue")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlainExport = null }) { Text(tr("取消", "Cancel")) }
            },
        )
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(tr("处理中…", "Working…")) },
            text = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("请等待文件处理完成", "Please wait for the file operation to finish"))
                }
            },
            confirmButton = { },
        )
    }

    pendingEncrypted?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                pendingEncrypted = null
                passwordError = null
            },
            title = { Text(tr("文件已加密", "File is encrypted")) },
            text = {
                Column {
                    Text(
                        tr(
                            "输入密码，或扫描导出设备的唯一码（可不输入 -）。",
                            "Enter the password, or scan the exporting device's unique code (hyphens optional).",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = importKey,
                        onValueChange = {
                            importKey = it
                            passwordError = null
                        },
                        label = tr("密码 / 唯一码", "Password / unique code"),
                    )
                    passwordError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(
                            onClick = { tryDecrypt(pending.first, pending.second, uniqueCode) },
                        ) { Text(tr("本机唯一码", "This device's code")) }
                        TextButton(
                            onClick = {
                                scanKey.launch(
                                    ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        setPrompt(I18n.pick("扫描导出设备的唯一码二维码", "Scan the exporting device's unique code QR"))
                                        setBeepEnabled(false)
                                        setOrientationLocked(false)
                                    },
                                )
                            },
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("扫描唯一码", "Scan unique code"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tryDecrypt(pending.first, pending.second, importKey) }) {
                    Text(tr("解密导入", "Decrypt and import"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingEncrypted = null
                    passwordError = null
                }) { Text(tr("取消", "Cancel")) }
            },
        )
    }

    imported?.let { result ->
        AlertDialog(
            onDismissRequest = { imported = null },
            title = { Text(tr("导入完成", "Import complete")) },
            text = {
                Text(
                    tr(
                        "已导入 ${result.count} 个密钥，删除源文件？",
                        "Imported ${result.count} secrets. Delete the source file?",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = runCatching { context.contentResolver.delete(result.uri, null, null) }
                        .getOrDefault(0) > 0
                    imported = null
                    toast(
                        if (ok) {
                            I18n.pick("源文件已删除", "Source file deleted")
                        } else {
                            I18n.pick("无法自动删除，请手动删除源文件", "Could not delete automatically; please delete the source file manually")
                        },
                    )
                }) { Text(tr("删除源文件", "Delete source file")) }
            },
            dismissButton = {
                TextButton(onClick = { imported = null }) { Text(tr("保留", "Keep")) }
            },
        )
    }
}

private const val MIN_PASSWORD_LENGTH = 6
private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

/** 导出方式：本机唯一码 / 自定义密钥 / JSON 明文 / otpauth TXT 明文。 */
private enum class ExportMode { UniqueCode, CustomKey, Plain, Txt }

/** 复用 TofaCrypto 的统一规范化（去空格/连字符、忽略大小写）。 */
private fun TofaCryptoKey(raw: String): CharArray =
    ToFA.Android.whmwws.phone.security.TofaCrypto.normalizeKey(raw)
