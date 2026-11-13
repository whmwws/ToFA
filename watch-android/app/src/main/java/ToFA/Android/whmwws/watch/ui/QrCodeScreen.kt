package ToFA.Android.whmwws.watch.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.imports.OtpauthUri

/**
 * 当前账号密钥二维码：把 otpauth:// 链接本地编码成二维码，手机直接扫码即可迁移。
 * 轻点任意区域返回表盘；转动表冠也可返回。
 */
@Composable
fun QrCodeScreen(account: TotpAccount, onClose: () -> Unit) {
    val round = isRoundScreen()
    val uri = remember(account) { OtpauthUri.build(account) }
    val image = remember(account) { qrImage(uri, QR_BITMAP_PX) }

    Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("account-qr")) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 表冠只请求焦点、不切换/不触发关闭（与唯一码页一致），越界仍由外层回弹层处理。
            .watchRotaryFocus("account-qr")
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
        // 圆屏按圆内接正方形留出安全余量；方屏改为按真实宽高分别扣留白，非正方形也不溢出。
        val qrSize = if (round) {
            diameter * 0.68f
        } else {
            val horizontalInset = WatchPagePadding
            val verticalReserve = 64.dp
            minOf(
                maxWidth - horizontalInset * 2,
                maxHeight - verticalReserve,
            ).coerceAtLeast(80.dp)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = if (round) 18.dp else WatchPagePadding),
        ) {
            Text(
                text = account.title,
                color = WatchColors.TextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Image(
                bitmap = image,
                contentDescription = tr("密钥二维码", "Key QR code"),
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(qrSize)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(6.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr("手机扫码导入 · 点按返回", "Scan with phone · Tap to return"),
                color = WatchColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
    }
}

private const val QR_BITMAP_PX = 512

internal fun qrImage(content: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val rowOffset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[rowOffset + x] = if (matrix.get(x, y)) black else white
        }
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}
