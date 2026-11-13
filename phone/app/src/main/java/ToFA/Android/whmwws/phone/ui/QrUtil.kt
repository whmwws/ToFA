package ToFA.Android.whmwws.phone.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import ToFA.Android.whmwws.phone.data.TotpAccount
import java.net.URLEncoder

/** 把账号编码成标准 otpauth:// 链接，供其他验证器扫码迁移。 */
fun TotpAccount.toOtpauthUri(): String {
    val label = if (issuer.isBlank()) account else "$issuer:$account".trim(':')
    val path = URLEncoder.encode(label, "UTF-8").replace("+", "%20")
    val params = buildString {
        append("secret=").append(secret)
        if (issuer.isNotBlank()) append("&issuer=").append(URLEncoder.encode(issuer, "UTF-8").replace("+", "%20"))
        append("&algorithm=").append(algorithm)
        append("&digits=").append(digits)
        append("&period=").append(period)
        if (type == "HOTP") append("&counter=").append(counter)
    }
    return "otpauth://${type.lowercase()}/$path?$params"
}

fun makeQrImage(content: String, sizePx: Int = 512): ImageBitmap {
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
        val row = y * sizePx
        for (x in 0 until sizePx) {
            pixels[row + x] = if (matrix.get(x, y)) black else white
        }
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}

@Composable
fun AccountQrDialog(account: TotpAccount, onDismiss: () -> Unit) {
    val image = remember(account.id) { makeQrImage(account.toOtpauthUri()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = account.label,
                color = Color(0xFF1A1C20),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Image(
                bitmap = image,
                contentDescription = "账号迁移二维码",
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(240.dp)
                    .background(Color.White)
                    .padding(4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "用其他验证器扫码迁移，密钥仅在本机生成",
                color = Color(0xFF5F6672),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
