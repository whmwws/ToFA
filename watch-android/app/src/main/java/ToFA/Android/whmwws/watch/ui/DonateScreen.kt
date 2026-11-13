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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.R

/**
 * 高级功能已激活后的捐赠页：展示微信 / 支付宝捐赠二维码，点按二维码切换。
 * 手表端不提供「打开应用」跳转，只保留二维码展示与必要提示；跳转能力在手机端。
 */
@Composable
fun DonateScreen(onBack: () -> Unit) {
    var wechat by remember { mutableStateOf(true) }
    val round = isRoundScreen()

    Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("donate")) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // 表冠只取焦并交给外层回弹，不再切换微信/支付宝二维码，避免误触。
                .watchRotaryFocus("donate"),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
            // 圆屏保持原有圆内比例；方屏按真实宽高分别扣留白与标题/说明区，非正方形屏幕不溢出。
            val qrSize = if (round) {
                diameter * 0.46f
            } else {
                val horizontalInset = WatchPagePadding
                val verticalReserve = 92.dp
                minOf(
                    maxWidth - horizontalInset * 2,
                    maxHeight - verticalReserve,
                ).coerceAtLeast(80.dp)
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WatchPageHeader(title = tr("捐赠", "Donate"), onBack = onBack)
                // 标题固定顶部，二维码与说明在剩余区域内整体居中（表冠可切换微信 / 支付宝）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = if (round) 8.dp else WatchPagePadding),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(qrSize)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .clickable { wechat = !wechat }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(
                                    if (wechat) R.drawable.donation_wechat else R.drawable.donation_alipay
                                ),
                                contentDescription = if (wechat) {
                                    tr("微信捐赠二维码", "WeChat donation QR code")
                                } else {
                                    tr("支付宝捐赠二维码", "Alipay donation QR code")
                                },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (wechat) {
                                tr("微信捐赠 · 点按切换支付宝", "WeChat · Tap for Alipay")
                            } else {
                                tr("支付宝捐赠 · 点按切换微信", "Alipay · Tap for WeChat")
                            },
                            color = WatchColors.TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("捐赠完全自愿，不影响任何功能", "Donation is voluntary and affects no features"),
                            color = WatchColors.TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
