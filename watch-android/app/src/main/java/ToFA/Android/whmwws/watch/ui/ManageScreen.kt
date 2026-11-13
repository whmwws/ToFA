package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.data.TotpAccount

/** 密钥管理页固定名称：密钥名优先，缺失时回退账户名，两者都空则「未命名」。 */
private fun secretName(account: TotpAccount): String =
    account.issuer.ifBlank { account.account.ifBlank { WatchI18n.pick("未命名", "Unnamed") } }

/**
 * 密钥管理：长按任意一行上下拖动即可排序，点按名称进入改名页，轻点右侧「删除」移除。
 * 顺序会写入加密保险库，重启后保持。
 * 注意：本页名称固定显示密钥名，不跟随首页的「显示账户名」三档设置。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageScreen(
    accounts: List<TotpAccount>,
    onBack: () -> Unit,
    onEdit: (TotpAccount) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<TotpAccount?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 26.dp.toPx() }
    val currentAccounts by rememberUpdatedState(accounts)
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("manage")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WatchPageHeader(title = tr("${accounts.size} 个密钥", "${accounts.size} keys"), onBack = onBack)
            // 提示固定预留两行高度：文字在圆屏换行也不改变内容高度，避免到底滚动抖动。
            Box(
                modifier = Modifier.fillMaxWidth().circularEdgePadding().height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tr("点按修改 · 长按排序", "Tap to edit · hold to sort"),
                    color = WatchColors.TextDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(2.dp))

            if (accounts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(tr("暂无密钥", "No keys"), color = WatchColors.TextDim, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .watchScrollProgress(listState)
                        .watchRotaryScroll(listState, "manage"),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(accounts, key = { _, item -> item.id }) { index, account ->
                        val dragging = draggingId == account.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .circularEdgePadding()
                                .height(WatchRowHeight)
                                .background(
                                    color = if (dragging) WatchColors.SurfaceVariant else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .pointerInput(account.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = account.id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            dragOffset += drag.y
                                            val currentIndex = currentAccounts.indexOfFirst { it.id == account.id }
                                            if (currentIndex >= 0) {
                                                when {
                                                    dragOffset > thresholdPx && currentIndex < currentAccounts.lastIndex -> {
                                                        onMove(currentIndex, currentIndex + 1)
                                                        dragOffset -= thresholdPx
                                                    }
                                                    dragOffset < -thresholdPx && currentIndex > 0 -> {
                                                        onMove(currentIndex, currentIndex - 1)
                                                        dragOffset += thresholdPx
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "≡",
                                color = if (dragging) WatchColors.Accent else WatchColors.TextFaint,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Text(
                                text = secretName(account),
                                color = WatchColors.Text,
                                fontSize = WatchRowFontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .basicMarquee()
                                    .clickable { onEdit(account) },
                            )
                            WatchOutlinedButton(
                                text = tr("删除", "Delete"),
                                modifier = Modifier.heightIn(min = 24.dp),
                                enabled = pendingDelete == null,
                            ) { pendingDelete = account }
                        }
                    }
                }
            }
        }

        pendingDelete?.let { account ->
            ConfirmDelete(
                account = account,
                onCancel = { pendingDelete = null },
                onConfirm = {
                    onDelete(account.id)
                    pendingDelete = null
                },
            )
        }
    }
}

@Composable
private fun ConfirmDelete(
    account: TotpAccount,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchColors.Scrim)
            // 弹层期间吞掉所有触控，避免点到下层列表其它行的「删除」按钮
            .pointerInput(Unit) {
                detectTapGestures(onTap = { }, onLongPress = { })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                tr("删除密钥？", "Delete key?"),
                color = WatchColors.Text,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = secretName(account),
                color = WatchColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(),
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchButton(tr("删除", "Delete")) { onConfirm() }
                WatchOutlinedButton(tr("取消", "Cancel")) { onCancel() }
            }
        }
    }
}
