package ToFA.Android.whmwws.phone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.imports.QrResult
import ToFA.Android.whmwws.phone.imports.QrRouter

enum class ScanMode { ANY, GOOGLE_EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    mode: ScanMode,
    onBack: () -> Unit,
    onImported: (List<TotpAccount>) -> Unit,
    onSwitchToGoogle: (() -> Unit)?,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var launching by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        launching = false
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            error = I18n.pick("已取消扫码，可以再试一次", "Scan cancelled — you can try again")
            return@rememberLauncherForActivityResult
        }
        when (val parsed = QrRouter.parse(contents, mode.toRouterMode())) {
            is QrResult.Ok -> onImported(parsed.accounts)
            is QrResult.Error -> error = parsed.message
        }
    }

    fun startScan() {
        error = null
        launching = true
        launcher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(
                    if (mode == ScanMode.GOOGLE_EXPORT) {
                        I18n.pick("扫描谷歌验证器的导出二维码", "Scan the Google Authenticator export QR code")
                    } else {
                        I18n.pick("将二维码放入取景框", "Fit the QR code in the frame")
                    },
                )
                setBeepEnabled(false)
                // 由 AndroidManifest 中 CaptureActivity 的 screenOrientation 控制，锁定竖屏
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            },
        )
    }

    LaunchedEffect(Unit) { startScan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (mode == ScanMode.GOOGLE_EXPORT) {
                            tr("谷歌验证器导入", "Google Authenticator import")
                        } else {
                            tr("扫码添加", "Scan")
                        },
                    )
                },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(64.dp).height(64.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                if (mode == ScanMode.GOOGLE_EXPORT) {
                    tr("Google Authenticator 导出步骤", "Google Authenticator export steps")
                } else {
                    tr("支持的二维码", "Supported QR codes")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (mode == ScanMode.GOOGLE_EXPORT) {
                    tr(
                        "1. 打开 Google Authenticator\n" +
                            "2. 右上角菜单 → 转移账号 → 导出账号\n" +
                            "3. 选择要导出的账号后，勾选「导出」生成二维码\n" +
                            "4. 回到本页点击「重新扫描」并扫描该二维码\n\n" +
                            "提示：若账号较多，谷歌会生成多张二维码，需要逐张扫描。",
                        "1. Open Google Authenticator\n" +
                            "2. Top-right menu → Transfer accounts → Export accounts\n" +
                            "3. Select the accounts to export, then tick Export to generate the QR code\n" +
                            "4. Return to this page, tap Rescan and scan that QR code\n\n" +
                            "Tip: with many accounts Google generates several QR codes — scan them one by one.",
                    )
                } else {
                    tr(
                        "· 网站 / 应用提供的 otpauth:// 二维码\n" +
                            "· 谷歌验证器导出二维码（otpauth-migration://）\n" +
                            "· 本应用「导出 JSON」保存的内容\n\n" +
                            "扫码后会自动加入账号列表，并尝试与手表同步。",
                        "· otpauth:// QR codes from websites / apps\n" +
                            "· Google Authenticator export QR codes (otpauth-migration://)\n" +
                            "· Content saved by this app's Export JSON\n\n" +
                            "Scanned accounts are added to the list automatically and synced to the watch when possible.",
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))
            Button(onClick = { startScan() }, enabled = !launching, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (launching) tr("正在打开相机…", "Opening camera…") else tr("重新扫描", "Rescan"))
            }
            if (onSwitchToGoogle != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSwitchToGoogle, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("改为从谷歌验证器导入", "Import from Google Authenticator instead"))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text(tr("返回列表", "Back to list")) }
        }
    }
}

private fun ScanMode.toRouterMode(): QrRouter.Mode = when (this) {
    ScanMode.ANY -> QrRouter.Mode.ANY
    ScanMode.GOOGLE_EXPORT -> QrRouter.Mode.GOOGLE_EXPORT
}
