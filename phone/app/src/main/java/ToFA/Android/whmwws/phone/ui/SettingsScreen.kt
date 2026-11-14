package ToFA.Android.whmwws.phone.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ToFA.Android.whmwws.phone.AppViewModel
import ToFA.Android.whmwws.phone.data.DisplayNameMode
import ToFA.Android.whmwws.phone.security.ClipboardGuard
import ToFA.Android.whmwws.phone.security.DeviceIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onImportExport: () -> Unit,
    onLicenses: () -> Unit,
    onChangelog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 版本号唯一来源为 app/build.gradle.kts 的 versionName
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val uniqueCode = remember { DeviceIdentity.current(context) }
    var showUniqueCode by remember { mutableStateOf(false) }
    var showDonation by remember { mutableStateOf(false) }

    // ---------- 应用锁 ----------
    var passwordEnabled by remember { mutableStateOf(vm.passwords.isEnabled()) }
    var wipeAfterErrors by remember { mutableStateOf(vm.passwords.isWipeAfterErrors()) }
    var emergencyEnabled by remember { mutableStateOf(vm.passwords.isEmergencyEnabled()) }
    var pendingPad by remember { mutableStateOf<PadKind?>(null) }
    var emergencyDraft by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("设置", "Settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("返回", "Back"),
                        )
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
            SectionTitle(tr("数据安全", "Data & Security"))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingsActionRow(
                        title = tr("导入导出", "Import / Export"),
                        desc = tr(
                            "导出可选加密，导入自动识别",
                            "Optional encryption on export; auto-detect on import",
                        ),
                        onClick = onImportExport,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showUniqueCode = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("本机唯一码", "Device Code"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                uniqueCode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                tr("重装不变，输入时可不带 -", "Unchanged after reinstall; the - can be omitted"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.QrCode2,
                            contentDescription = tr("显示唯一码二维码", "Show device code QR"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle(tr("显示与交互", "Display & Interaction"))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // 深浅色模式：三档分段选择，切换立即生效并持久化
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(tr("深浅色模式", "Theme"), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tr("切换后立即生效，并记住选择", "Applied immediately and remembered"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            themeModeOptions.forEach { option ->
                                val selected = vm.themeMode == option.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                        )
                                        .clickable { vm.updateThemeMode(option.id) }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = tr(option.label, option.enLabel),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    // 界面语言：选择菜单形式，切换立即生效并持久化；支持导入离线语言包
                    var showLanguageMenu by remember { mutableStateOf(false) }
                    val importLanguage = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch(Dispatchers.IO) {
                            val text = runCatching {
                                context.contentResolver.openInputStream(uri)
                                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            }.getOrNull()
                            val name = text?.let { vm.importLanguagePack(it) }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (name != null) {
                                        I18n.pick("已导入语言包：", "Language pack imported: ") + name
                                    } else {
                                        I18n.pick("语言包格式不正确", "Invalid language pack")
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageMenu = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr("语言", "Language"),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tr(
                                        "支持导入语言包",
                                        "Language pack import supported",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                I18n.labelFor(vm.languageId),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                        ) {
                            I18n.options().forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.label) },
                                    onClick = {
                                        vm.setLanguage(lang.id)
                                        showLanguageMenu = false
                                    },
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            DropdownMenuItem(
                                text = { Text(tr("导入语言包（JSON）", "Import language pack (JSON)")) },
                                onClick = {
                                    showLanguageMenu = false
                                    importLanguage.launch(arrayOf("application/json", "text/*"))
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsSwitchRow(
                        title = tr("复制验证码", "Copy codes"),
                        desc = tr("轻点复制，更新后清空剪贴板", "Tap to copy; clipboard clears on refresh"),
                        checked = vm.copyEnabled,
                        onCheckedChange = vm::updateCopyEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsSwitchRow(
                        title = tr("显示二维码按钮", "Show QR button"),
                        desc = tr("卡片右侧显示二维码入口", "QR entry on the right of each card"),
                        checked = vm.qrEnabled,
                        onCheckedChange = vm::updateQrEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsSwitchRow(
                        title = tr("最近任务隐私模式", "Recents privacy"),
                        desc = tr("隐藏缩略图并禁止截屏", "Hide thumbnail and block screenshots"),
                        checked = vm.recentsPrivacy,
                        onCheckedChange = vm::updateRecentsPrivacy,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsSwitchRow(
                        title = tr("启动时显示验证码", "Show codes on launch"),
                        desc = tr("关闭后进入应用先显示遮挡页", "When off, the app opens with codes hidden"),
                        checked = vm.showCodesOnLaunch,
                        onCheckedChange = vm::updateShowCodesOnLaunch,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    // 显示账户名：三档分段选择，切换立即生效并持久化（与「深浅色模式」同一套交互）
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(tr("显示账户名", "Account name"), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tr(
                                "只密钥名 / 只账户名 / 都显示，切换后立即生效",
                                "Secret name only / account only / both; applied immediately",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            DisplayNameMode.entries.forEach { option ->
                                val selected = vm.displayNameMode == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                        )
                                        .clickable { vm.updateDisplayNameMode(option) }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = tr(option.label, option.enLabel),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle(tr("应用锁", "App Lock"))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingsSwitchRow(
                        title = tr("启用密码", "Enable password"),
                        desc = tr("启动或回前台时解锁", "Unlock on launch or return to foreground"),
                        checked = passwordEnabled,
                        onCheckedChange = { value ->
                            if (value) {
                                pendingPad = PadKind.SetPassword
                            } else {
                                vm.passwords.disablePassword()
                                passwordEnabled = false
                            }
                        },
                    )
                    if (passwordEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        SettingsActionRow(
                            title = tr("更改密码", "Change password"),
                            desc = tr("至少 4 位", "At least 4 characters"),
                            onClick = { pendingPad = PadKind.ChangePassword },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        SettingsSwitchRow(
                            title = tr("多次错误后删除", "Wipe after failed attempts"),
                            desc = tr("第 5 次错误清空密钥", "Clears secrets on the 5th wrong entry"),
                            checked = wipeAfterErrors,
                            onCheckedChange = {
                                wipeAfterErrors = it
                                vm.passwords.setWipeAfterErrors(it)
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        SettingsSwitchRow(
                            title = tr("紧急密码", "Emergency password"),
                            desc = tr("输入即清空全部密钥", "Entering it wipes all secrets"),
                            checked = emergencyEnabled,
                            onCheckedChange = { value ->
                                if (value) {
                                    emergencyDraft = null
                                    pendingPad = PadKind.EmergencyFirst
                                } else {
                                    vm.passwords.disableEmergencyPassword()
                                    emergencyEnabled = false
                                }
                            },
                        )
                        if (emergencyEnabled) {
                            SettingsActionRow(
                                title = tr("更改紧急密码", "Change emergency password"),
                                desc = tr("需要连续输入两次确认", "Enter it twice to confirm"),
                                onClick = {
                                    emergencyDraft = null
                                    pendingPad = PadKind.EmergencyFirst
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            SectionTitle(tr("主题色", "Accent Color"))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        accentOptions.forEach { option ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(option.dark)
                                        .then(
                                            if (vm.accentId == option.id) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable { vm.setAccent(option.id) },
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    tr(option.label, option.enLabel),
                                    fontSize = 10.sp,
                                    color = if (vm.accentId == option.id) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // 色轮自由选色：HSV 圆盘，拖动选点，选中色持久化
                    AccentColorWheel(
                        color = accentById(vm.accentId).dark,
                        onColor = { color ->
                            vm.setAccent("custom:" + String.format("%08X", color.toArgb()))
                        },
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("拖动色轮自由选色", "Drag the wheel to pick any color"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(14.dp))
                    // 数值调色：HEX 输入与色轮/预设共存；非法值给提示并禁用应用。
                    val currentHex = String.format("#%06X", accentById(vm.accentId).dark.toArgb() and 0xFFFFFF)
                    var hexText by remember(vm.accentId) { mutableStateOf(currentHex) }
                    val normalizedHex = hexText.trim().removePrefix("#").trim()
                    val hexValid = normalizedHex.length == 6 &&
                        normalizedHex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { hexText = it.take(7) },
                        label = { Text(tr("HEX 颜色", "HEX color")) },
                        placeholder = { Text("#RRGGBB") },
                        singleLine = true,
                        isError = !hexValid,
                        supportingText = {
                            Text(
                                if (hexValid) {
                                    tr("输入 #RRGGBB 可直接应用", "Enter #RRGGBB to apply directly")
                                } else {
                                    tr(
                                        "请输入 6 位十六进制颜色，如 #8AB4F8",
                                        "Enter a 6-digit hex color, e.g. #8AB4F8",
                                    )
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { vm.setAccent("custom:FF" + normalizedHex.uppercase()) },
                            enabled = hexValid,
                        ) { Text(tr("应用 HEX 颜色", "Apply HEX color")) }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 主题色推送 / 导入：复用传递页的无线调试通道；失败在状态行明确提示。
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.adbPushTheme() },
                            enabled = !vm.adbBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text(tr("推送到手表", "Push to watch")) }
                        OutlinedButton(
                            onClick = { vm.adbImportTheme() },
                            enabled = !vm.adbBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text(tr("从手表导入", "Import from watch")) }
                    }
                    val lastAdb = vm.adbLog.trim().lines().lastOrNull { it.isNotBlank() }
                    if (lastAdb != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            lastAdb,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (vm.adbHost.isBlank() || vm.adbPort.isBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr(
                                "请先在「传递」页填写手表无线调试地址与端口",
                                "Set the watch's wireless debugging address and port on the Transfer page first",
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SectionTitle(tr("捐赠支持", "Support"))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 文案在左，按钮在同一行右侧
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.premium.activated) {
                                tr("高级功能已启用", "Premium enabled")
                            } else {
                                tr("高级功能尚未激活", "Premium not activated")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (vm.premium.activated) {
                                tr("感谢支持！数据仅保存在本机。", "Thanks for your support! Data stays on this device.")
                            } else {
                                tr("完全自愿，不影响任何功能。", "Entirely voluntary; no features are affected.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showDonation = true }) {
                        Text(tr("支持 ToFA", "Support ToFA"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SectionTitle(tr("安全说明", "Security Notes"))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SecurityNote(
                        title = tr("密钥存储", "Secret storage"),
                        body = tr(
                            "密钥只写入本机应用私有目录，并由 Android Keystore 加密保护。卸载应用或清除数据后无法恢复。",
                            "Secrets are written only to the app's private storage and protected by the Android Keystore. They cannot be recovered after uninstall or clearing app data.",
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    SecurityNote(
                        title = tr("加密导出", "Encrypted export"),
                        body = tr(
                            "导出可选 AES-256-GCM + PBKDF2 加密，也可用本机唯一码加密。明文导出不带任何保护，请勿通过聊天工具或邮件外发。",
                            "Exports can be encrypted with AES-256-GCM + PBKDF2 or with this device's code. Plaintext exports carry no protection — never send them over chat or email.",
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    SecurityNote(
                        title = tr("网络与统计", "Network & analytics"),
                        body = tr(
                            "应用不申请网络权限，也不含统计、广告或任何第三方联网 SDK；激活校验只在本机完成。",
                            "The app requests no network permission and bundles no analytics, ads, or networked SDKs. Activation is verified entirely on this device.",
                        ),
                    )
                }
            }

            SectionTitle(tr("关于", "About"))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChangelog)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("版本更新日志", "Changelog"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("按版本查看更新内容", "Release notes by version"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(">", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLicenses)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("开源许可", "Open source licenses"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("第三方库清单与许可证全文", "Third-party libraries and full license texts"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(">", color = MaterialTheme.colorScheme.primary)
                }
            }

            // 版本号（唯一来源：app/build.gradle.kts 的 versionName）
            Text(
                text = versionName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showUniqueCode) {
        UniqueCodeDialog(code = uniqueCode, onDismiss = { showUniqueCode = false })
    }
    if (showDonation) {
        DonationDialog(activated = vm.premium.activated, onDismiss = { showDonation = false })
    }

    when (pendingPad) {
        PadKind.SetPassword -> PasswordPadDialog(
            title = tr("设置解锁密码", "Set unlock password"),
            subtitle = tr("至少 4 位，可使用数字与符号", "At least 4 characters; digits and symbols allowed"),
            confirmLabel = tr("启用", "Enable"),
            onDismiss = { pendingPad = null },
            onConfirm = { input ->
                vm.passwords.setPassword(input.toCharArray())
                passwordEnabled = true
                pendingPad = null
            },
        )

        PadKind.ChangePassword -> PasswordPadDialog(
            title = tr("更改解锁密码", "Change unlock password"),
            subtitle = tr("输入新密码；至少 4 位", "Enter a new password; at least 4 characters"),
            confirmLabel = tr("保存", "Save"),
            onDismiss = { pendingPad = null },
            onConfirm = { input ->
                vm.passwords.setPassword(input.toCharArray())
                Toast.makeText(context, I18n.pick("密码已更新", "Password updated"), Toast.LENGTH_SHORT).show()
                pendingPad = null
            },
        )

        PadKind.EmergencyFirst -> PasswordPadDialog(
            title = tr("设置紧急密码", "Set emergency password"),
            subtitle = tr(
                "第一次输入；解锁时输入它会立即清空密钥",
                "First entry; entering it at unlock wipes all secrets",
            ),
            confirmLabel = tr("下一步", "Next"),
            onDismiss = { pendingPad = null },
            onConfirm = { input ->
                emergencyDraft = input
                pendingPad = PadKind.EmergencyConfirm
            },
        )

        PadKind.EmergencyConfirm -> PasswordPadDialog(
            title = tr("再次确认紧急密码", "Confirm emergency password"),
            subtitle = tr("请重复输入，两次必须一致", "Enter it again; both entries must match"),
            confirmLabel = tr("保存", "Save"),
            onDismiss = { pendingPad = null },
            onConfirm = { input ->
                if (input == emergencyDraft) {
                    vm.passwords.setEmergencyPassword(input.toCharArray())
                    emergencyEnabled = true
                    pendingPad = null
                    Toast.makeText(
                        context,
                        I18n.pick("紧急密码已保存", "Emergency password saved"),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        I18n.pick("两次输入不一致，请重新设置", "Entries don't match. Please try again."),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )

        null -> Unit
    }
}

private enum class PadKind { SetPassword, ChangePassword, EmergencyFirst, EmergencyConfirm }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsActionRow(title: String, desc: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(3.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun UniqueCodeDialog(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val image = remember(code) { makeQrImage(DeviceIdentity.qrPayload(code), 512) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("本机唯一码", "Device Code"),
                color = Color(0xFF1A1C20),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Image(
                bitmap = image,
                contentDescription = tr("本机唯一码二维码", "Device code QR"),
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(220.dp).background(Color.White),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                code,
                color = Color(0xFF1A1C20),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tr(
                    "该码由设备生成，重装应用不会改变；输入时可不带 -。",
                    "Generated by this device; unchanged after reinstall. The - can be omitted.",
                ),
                color = Color(0xFF5F6672),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("极端情况下会改变，请注意备份密钥", "It may change in extreme cases; back up your secrets"),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                ClipboardGuard.copy(context, I18n.pick("ToFA 唯一码", "ToFA device code"), code)
                Toast.makeText(context, I18n.pick("已复制唯一码", "Device code copied"), Toast.LENGTH_SHORT).show()
            }) { Text(tr("复制唯一码", "Copy code")) }
        }
    }
}

/** 安全说明小节：小标题 + 短段落，按语义分组，而不是靠空行堆叠。 */
@Composable
private fun SecurityNote(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(5.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 19.sp,
    )
}
