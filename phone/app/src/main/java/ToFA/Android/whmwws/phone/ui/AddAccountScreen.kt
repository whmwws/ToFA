package ToFA.Android.whmwws.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.otp.Base32

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onGoogleImport: () -> Unit,
    onSave: (TotpAccount) -> Unit,
) {
    var issuer by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf("SHA1") }
    var digits by remember { mutableStateOf(6) }
    var period by remember { mutableStateOf(30) }
    var otpType by remember { mutableStateOf("TOTP") }
    var counter by remember { mutableStateOf("0") }
    // 计数器输入保留完整 TextFieldValue：过滤非数字时不清空 IME 组合区，删除不再残留。
    var counterField by remember { mutableStateOf(TextFieldValue("0", TextRange(1))) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("添加账号", "Add account")) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("扫码添加", "Scan"))
                }
                OutlinedButton(onClick = onGoogleImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("谷歌导入", "Google import"))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(tr("手动输入", "Manual entry"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(tr("发行方（如 GitHub / Google）", "Issuer (e.g. GitHub / Google)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text(tr("账号（邮箱 / 用户名）", "Account (email / username)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(tr("密钥（Base32，可含空格）", "Secret (Base32, spaces allowed)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            Text(tr("类型", "Type"), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TOTP", "HOTP").forEach { item ->
                    FilterChip(
                        selected = otpType == item,
                        onClick = { otpType = item },
                        label = { Text(item) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Text(tr("算法", "Algorithm"), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SHA1", "SHA256", "SHA512").forEach { item ->
                    FilterChip(selected = algorithm == item, onClick = { algorithm = item }, label = { Text(item) })
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(tr("位数", "Digits"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 8).forEach { item ->
                            FilterChip(selected = digits == item, onClick = { digits = item }, label = { Text("$item") })
                        }
                    }
                }
                Column {
                    Text(tr("周期（秒）", "Period (seconds)"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60).forEach { item ->
                            FilterChip(selected = period == item, onClick = { period = item }, label = { Text("$item") })
                        }
                    }
                }
            }

            if (otpType == "HOTP") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = counterField,
                    onValueChange = { new ->
                        val digits = new.text.filter { c -> c.isDigit() }
                        val next = if (digits == new.text) {
                            new
                        } else {
                            new.copy(
                                text = digits,
                                selection = TextRange(digits.length),
                                composition = null,
                            )
                        }
                        counterField = next
                        counter = digits
                    },
                    label = { Text(tr("HOTP 计数器", "HOTP counter")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 固定预留两行错误区：错误文本有无、是否换行都不改变滚动内容高度，
            // 避免滚到底时最大滚动值反复变化造成抖动/错位。
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val normalized = runCatching { Base32.encode(Base32.decode(secret)) }
                    if (normalized.isFailure) {
                        error = I18n.pick(
                            "密钥不是合法的 Base32：${normalized.exceptionOrNull()?.message}",
                            "Not a valid Base32 secret: ${normalized.exceptionOrNull()?.message}",
                        )
                        return@Button
                    }
                    if (normalized.getOrNull().isNullOrBlank()) {
                        error = I18n.pick("请填写密钥", "Please enter the secret")
                        return@Button
                    }
                    error = null
                    onSave(
                        TotpAccount(
                            issuer = issuer.trim(),
                            account = account.trim(),
                            secret = normalized.getOrThrow(),
                            algorithm = algorithm,
                            digits = digits,
                            period = period,
                            type = otpType,
                            counter = counter.toLongOrNull() ?: 0L,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(tr("保存", "Save"))
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** 与手表端密钥管理一致：只修改密钥名和账户名，其余 OTP 参数保持不变。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameAccountScreen(
    account: TotpAccount,
    onBack: () -> Unit,
    onSave: (TotpAccount) -> Unit,
) {
    var name by remember(account.id) { mutableStateOf(account.issuer) }
    var accountName by remember(account.id) { mutableStateOf(account.account) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("修改名称", "Rename")) },
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
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("密钥名（可选）", "Key name (optional)")) },
                placeholder = { Text(tr("未命名", "Unnamed")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text(tr("账户名（可选）", "Account name (optional)")) },
                placeholder = { Text(tr("邮箱 / 用户名", "Email / username")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(account.copy(issuer = name.trim(), account = accountName.trim()))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(tr("保存", "Save")) }
        }
    }
}
