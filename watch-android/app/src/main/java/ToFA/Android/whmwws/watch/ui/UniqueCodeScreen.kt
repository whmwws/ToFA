package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.security.DeviceIdentity

/**
 * 手表唯一码展示页：二维码与大字文本可点按切换，供手机扫码导入 / 导出解密。
 */
@Composable
fun UniqueCodeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val code = remember { DeviceIdentity.current(context) }
    val image = remember(code) { qrImage(DeviceIdentity.qrPayload(code), UNIQUE_QR_PX) }
    var showText by remember { mutableStateOf(false) }
    val round = isRoundScreen()

    Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("unique-code")) {
    // 只取焦不消费：表冠越界仍由外层回弹层处理，但不再切换二维码/文字
    Column(modifier = Modifier.fillMaxSize().watchRotaryFocus("unique-code").padding(bottom = 4.dp)) {
    // 标题固定在屏幕最上方，不随内容位移。
    WatchPageHeader(title = tr("唯一码", "Unique code"), onBack = onBack)
    // 二维码/文字主体占满标题栏与底部提示之间的全部空间：不再乘固定系数，避免圆屏下缩在中间。
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (round) {
            // 正圆：可用宽高都按实际约束取，弦长本身已保证宽度不越圆；再留 2dp 视觉边距。
            val qrSize = minOf(maxWidth, maxHeight).let { it * 0.94f }
            UniqueCodeBody(
                qrSize = qrSize,
                showText = showText,
                onToggle = { showText = !showText },
                code = code,
                image = image,
                round = true,
            )
        } else {
            // 方屏：水平、垂直留白分开计算，按真实宽高取较小边，非正方形屏幕也不会溢出。
            val horizontalInset = 16.dp
            val verticalInset = 8.dp
            val qrSize = minOf(
                maxWidth - horizontalInset * 2,
                maxHeight - verticalInset * 2,
            ).coerceAtLeast(80.dp)
            UniqueCodeBody(
                qrSize = qrSize,
                showText = showText,
                onToggle = { showText = !showText },
                code = code,
                image = image,
                round = false,
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    Text(
        // 方屏/圆屏共用一行提示；英文与日语明显更长，允许折两行并保留圆屏左右内缩，避免贴边被裁。
        text = if (showText) {
            tr("点按显示二维码 · 输入可不带 -", "Tap for QR code · \"-\" optional")
        } else {
            tr("点按显示唯一码", "Tap to show unique code")
        },
        color = WatchColors.TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth().circularEdgePadding(),
    )
    Text(
        text = tr("会因特殊情况改变，请妥善备份密钥", "May change in special cases; back up your key"),
        color = WatchColors.TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 13.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .circularEdgePadding()
            .padding(bottom = 10.dp, start = 6.dp, end = 6.dp),
    )
    }
    }
}

/** 唯一码主体（二维码 / 大字文本）：尺寸由外层按真实可用宽高决定，这里只负责绘制与点按切换。 */
@Composable
private fun UniqueCodeBody(
    qrSize: Dp,
    showText: Boolean,
    onToggle: () -> Unit,
    code: String,
    image: ImageBitmap,
    round: Boolean,
) {
    Box(
        modifier = Modifier
            .size(qrSize)
            .then(
                if (showText) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(5.dp)
                }
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (showText) {
            Text(
                text = code.split("-")
                    .chunked(2)
                    .joinToString("\n") { it.joinToString("-") },
                color = WatchColors.Text,
                fontFamily = FontFamily.Monospace,
                fontSize = if (round) 20.sp else 16.sp,
                lineHeight = if (round) 24.sp else 20.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = tr("设备唯一码二维码", "Device unique code QR"),
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal const val UNIQUE_QR_PX = 512
