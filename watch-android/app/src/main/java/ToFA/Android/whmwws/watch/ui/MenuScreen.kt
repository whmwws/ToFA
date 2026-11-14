package ToFA.Android.whmwws.watch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置页（长按表盘进入）：密钥管理、传递、唯一码、其他。
 * 唯一码为独立按钮，点击进入展示页。
 */
@Composable
fun MenuScreen(
    onManage: () -> Unit,
    onUniqueCode: () -> Unit,
    onTransfer: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(180),
        label = "menuAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.94f,
        animationSpec = WatchSpringSpec,
        label = "menuScale",
    )
    val scroll = rememberScrollState()
    // 版本号唯一来源为 app/build.gradle.kts 的 versionName
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题固定在屏幕最上方，不随内容滚动。
        WatchPageHeader(title = tr("设置", "Settings"), onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).watchRotaryBounce("menu")) {
        WatchRowColumn(
            scroll = scroll,
            alpha = alpha,
            scale = scale,
            versionName = versionName,
        ) {
        Spacer(Modifier.height(2.dp))
        WatchButton(
            tr("密钥管理", "Manage keys"),
            Modifier.fillMaxWidth().circularEdgePadding(),
        ) { onManage() }
        Spacer(Modifier.height(3.dp))
        WatchOutlinedButton(
            text = tr("传递", "Transfer"),
            modifier = Modifier.fillMaxWidth().circularEdgePadding(),
        ) { onTransfer() }
        Spacer(Modifier.height(3.dp))
        WatchOutlinedButton(
            text = tr("唯一码", "Unique code"),
            modifier = Modifier.fillMaxWidth().circularEdgePadding(),
        ) { onUniqueCode() }
        Spacer(Modifier.height(3.dp))
        WatchOutlinedButton(
            text = tr("其他", "More"),
            modifier = Modifier.fillMaxWidth().circularEdgePadding(),
        ) { onSettings() }
        Spacer(Modifier.height(3.dp))
    }
    }
    }
}

@Composable
private fun WatchRowColumn(
    scroll: ScrollState,
    alpha: Float,
    scale: Float,
    versionName: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .watchScrollProgress(scroll)
                .verticalScroll(scroll)
                .watchRotaryScroll(scroll, "menu")
                .alpha(alpha)
                .scale(scale)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
        Column(
            // 固定锚点计算弦长：逐帧测量会与提示文本换行形成反馈环，进场缩放时抽搐
            modifier = Modifier
                .fillMaxWidth()
                .bottomAnchorEdgePadding(anchorFromBottom = 18.dp)
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 官网 / 返回提示 / 版本号三行 0 间距、各自收紧行高；
            // 版本号单独压在最底一行，避免与上方内容挤在一起。
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                // 地址固定展示，窄圆屏下允许折行，绝不省略号截断
                text = "whmwws.icu/tools/ToFA",
                color = WatchColors.TextDim,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = tr("点击顶部区域返回", "Tap the top area to go back"),
                color = WatchColors.TextDim,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            // 版本号放到页面最底部：信息量最低、字数最少，贴底既不会被上方内容挤压，
            // 居中单行也比两行提示更窄，不会碰到圆屏底部的弧边。
            Text(
                text = versionName,
                color = WatchColors.TextDim,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
