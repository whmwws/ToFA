package ToFA.Android.whmwws.phone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.phone.R

/** 支付宝收款码原始内容，可用 appId=20000067 的收银台 scheme 直接打开付款页。 */
private const val ALIPAY_QR_URL = "https://qr.alipay.com/tsx17654b95qmfmuyslwp70"

/**
 * 微信 / 支付宝捐赠二维码，均已裁切为纯二维码区域，白底便于扫描。
 * 点击行为按平台能力区分：
 * - 支付宝：走官方收银台 scheme 直达付款页，失败回退到 https 收款页。
 * - 微信：个人收款码没有公开的直达 scheme，只能唤起微信并提示自行扫码。
 */
@Composable
fun DonationQrRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        DonationQrCard(R.drawable.donation_wechat, tr("微信", "WeChat")) { openWechatDonation(it) }
        DonationQrCard(R.drawable.donation_alipay, tr("支付宝", "Alipay")) { openAlipayDonation(it) }
    }
    Text(
        tr("点支付宝二维码可直达付款；微信请用扫一扫扫描", "Tap the Alipay QR code to pay directly; for WeChat, scan it in WeChat"),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@Composable
private fun DonationQrCard(resId: Int, label: String, onClick: (Context) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.clickable { onClick(context) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 对称留白：白色卡片内四周统一 4dp，保证二维码图形居中
        Image(
            painter = painterResource(resId),
            contentDescription = tr("$label 捐赠二维码", "$label donation QR code"),
            modifier = Modifier
                .size(120.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(4.dp),
        )
        Spacer(Modifier.height(6.dp))
        // 跳转按钮与名称同一行，位于文案右侧
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                    .clickable { onClick(context) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr("打开", "Open"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 支付宝：收银台 scheme 直达付款页；未安装 / 不支持时回退到 https 收款页。 */
private fun openAlipayDonation(context: Context) {
    val encoded = java.net.URLEncoder.encode(ALIPAY_QR_URL, "UTF-8")
    val deepLink = "alipays://platformapi/startapp?appId=20000067&url=$encoded"
    if (tryStart(context, deepLink)) return
    if (tryStart(context, ALIPAY_QR_URL)) return
    Toast.makeText(
        context,
        I18n.pick("未安装支付宝，请用微信/支付宝扫码", "Alipay is not installed — please scan with WeChat or Alipay"),
        Toast.LENGTH_SHORT,
    ).show()
}

/** 微信：个人收款码无直达 scheme，只能唤起微信并提示用户自行扫码。 */
private fun openWechatDonation(context: Context) {
    if (tryStart(context, "weixin://")) {
        Toast.makeText(
            context,
            I18n.pick(
                "已打开微信，请用「扫一扫」扫描二维码，或长按保存图片后从相册识别",
                "WeChat is open — scan the QR code with Scan, or long-press to save and pick it from your gallery",
            ),
            Toast.LENGTH_LONG,
        ).show()
    } else {
        Toast.makeText(
            context,
            I18n.pick("未安装微信，请用支付宝扫码", "WeChat is not installed — please scan with Alipay"),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun tryStart(context: Context, uri: String): Boolean =
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

/** 主页横幅「去捐赠」弹窗：扫码捐赠，再决定「我已捐赠」或「暂不考虑」。 */
@Composable
fun PremiumDonateDialog(onDonated: () -> Unit, onDeclined: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("捐赠激活高级功能", "Donate to activate premium"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    tr(
                        "扫码任意金额支持开发者，即可启用多密钥等高级功能，并同步激活手表端。",
                        "Scan to donate any amount and support the developer; this enables multiple keys and other premium features and activates the watch app too.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                DonationQrRow()
            }
        },
        confirmButton = {
            TextButton(onClick = onDonated) { Text(tr("我已捐赠", "I've donated"), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDeclined) {
                Text(tr("暂不考虑", "Not now"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
fun DonationThanksDialog(onGoTransfer: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("感谢你的支持", "Thank you for your support"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    tr(
                        "高级功能已启用。手机端与手表端的数据仍全部保存在本机，不会上传。",
                        "Premium is now enabled. Data on both the phone and the watch stays on the device and is never uploaded.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    tr(
                        "如需在手表上使用高级功能，请把手机连接到手表：传递 → 激活手表高级功能。",
                        "To use premium on the watch, connect the phone to the watch: Transfer → Activate watch premium.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                DonationQrRow()
            }
        },
        confirmButton = {
            TextButton(onClick = onGoTransfer) { Text(tr("去连接手表", "Connect watch"), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("稍后", "Later")) } },
    )
}

@Composable
fun PremiumDeclineConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("暂不考虑捐赠？", "Skip donating?"), fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                tr(
                    "ToFA 的高级功能仍会为你启用，捐赠入口会收纳到「设置 → 捐赠支持」。之后随时可以在那里支持 ToFA。",
                    "ToFA premium stays enabled for you, and the donation entry moves into Settings → Donate. You can support ToFA there any time.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("确认暂不考虑", "Yes, skip for now")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("返回", "Back")) } },
    )
}

@Composable
fun PremiumDeclineDoneDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("高级功能已启用", "Premium enabled"), fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                tr(
                    "捐赠入口已收纳到「设置 → 捐赠支持」，不会再来打扰。谢谢你的使用！",
                    "The donation entry now lives in Settings → Donate and will not bother you again. Thanks for using ToFA!",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("知道了", "Got it")) } },
    )
}

/** 设置里的捐赠入口：始终可打开；已激活后不再展示二维码。 */
@Composable
fun DonationDialog(activated: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("支持 ToFA", "Support ToFA"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    if (activated) {
                        tr(
                            "高级功能已启用，感谢你的支持！ToFA 完全离线运行，不采集任何密钥数据。",
                            "Premium is enabled — thank you for your support! ToFA runs fully offline and collects no key data.",
                        )
                    } else {
                        tr(
                            "ToFA 完全离线运行，不采集任何密钥数据。如果它帮到了你，可以扫码支持开发者。",
                            "ToFA runs fully offline and collects no key data. If it helps you, you can scan to support the developer.",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                DonationQrRow()
                Spacer(Modifier.height(10.dp))
                Text(
                    if (activated) {
                        tr(
                            "后续仍可自愿支持 ToFA，不影响任何功能。",
                            "You can still support ToFA voluntarily later; no feature is affected.",
                        )
                    } else {
                        tr(
                            "高级功能已对所有用户开放；捐赠完全自愿，不影响任何功能。",
                            "Premium is available to all users; donating is entirely voluntary and never affects any feature.",
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("关闭", "Close")) } },
    )
}
