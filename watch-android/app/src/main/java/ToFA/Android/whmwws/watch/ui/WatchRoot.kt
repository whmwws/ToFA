package ToFA.Android.whmwws.watch.ui

import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ToFA.Android.whmwws.watch.MainActivity
import ToFA.Android.whmwws.watch.data.AccountRepository
import ToFA.Android.whmwws.watch.data.DisplayNameMode
import ToFA.Android.whmwws.watch.data.PremiumStore
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.data.WatchSettings
import ToFA.Android.whmwws.watch.imports.AccountImporter
import ToFA.Android.whmwws.watch.imports.PayloadCodec
import ToFA.Android.whmwws.watch.security.ClipboardGuard
import ToFA.Android.whmwws.watch.security.DeviceIdentity
import ToFA.Android.whmwws.watch.security.PasswordPolicy
import ToFA.Android.whmwws.watch.security.PasswordStore
import ToFA.Android.whmwws.watch.security.TofaCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class WatchScreen {
    Lock, Main, Menu, Manage, Transfer, Add, EditAccount, Settings, Theme, Password, Pad, Qr, UniqueCode, Donate, Licenses
}

/**
 * 页面层级深度：仅用于判定切换方向（进入 = 深度增加，返回 = 深度减少）。
 * 返回动画据此取进入动画的镜像版本，不再与进入共用同一套正向动效。
 */
private fun screenDepth(screen: WatchScreen): Int = when (screen) {
    WatchScreen.Lock -> 0
    WatchScreen.Main -> 1
    WatchScreen.Qr -> 2
    WatchScreen.Menu -> 2
    WatchScreen.Manage -> 3
    WatchScreen.Transfer -> 3
    WatchScreen.UniqueCode -> 3
    WatchScreen.Settings -> 3
    WatchScreen.Add -> 4
    WatchScreen.EditAccount -> 4
    WatchScreen.Theme -> 4
    WatchScreen.Donate -> 4
    WatchScreen.Password -> 4
    WatchScreen.Licenses -> 4
    WatchScreen.Pad -> 5
}

private enum class PadPurpose { Set, Change, Emergency }

private const val KEY_LAST_IMPORT_MTIME = "last_import_mtime"
private const val ACTIVATION_FILE_NAME = "tofa-activation.json"

/**
 * 手表端根界面。
 * 主流程：锁屏 → 表盘 → 长按菜单（密钥管理 / 传递 / 唯一码 / 其他）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchRoot(repository: AccountRepository) {
    val context = LocalContext.current
    val repo = repository
    val accounts by repo.accounts.collectAsState()
    // 主页分页状态上提到根界面：打开二维码页后关闭时，能回到原来的那个密钥页。
    val pagerState = rememberPagerState(pageCount = { accounts.size })
    val premium = remember { PremiumStore(context) }
    val passwordStore = remember { PasswordStore(context) }

    var screen by remember {
        mutableStateOf(if (passwordStore.isEnabled()) WatchScreen.Lock else WatchScreen.Main)
    }
    var secured by remember { mutableStateOf(passwordStore.isEnabled()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var qrEnabled by remember { mutableStateOf(WatchSettings.isQrEnabled(context)) }
    var squareUi by remember { mutableStateOf(WatchSettings.isSquareUi(context)) }
    var copyEnabled by remember { mutableStateOf(WatchSettings.isCopyEnabled(context)) }
    // 两个独立开关：最近任务隐私（只影响后台卡片）与开屏隐私（进入先遮挡页面内容）
    var recentsPrivacy by remember { mutableStateOf(WatchSettings.isRecentsPrivacy(context)) }
    var showCodesOnLaunch by remember { mutableStateOf(WatchSettings.isShowCodesOnLaunch(context)) }
    var themeMode by remember { mutableStateOf(WatchThemeMode.fromId(WatchSettings.themeModeId(context))) }
    var displayNameMode by remember { mutableStateOf(WatchSettings.displayNameMode(context)) }
    var codesHidden by remember { mutableStateOf(!showCodesOnLaunch) }
    var qrAccount by remember { mutableStateOf<TotpAccount?>(null) }
    var editAccount by remember { mutableStateOf<TotpAccount?>(null) }
    // 首启隐私协议：1 = 隐私提示，2 = 激活说明，0 = 不再显示
    var privacyNotice by remember {
        mutableIntStateOf(if (WatchSettings.isPrivacyAgreed(context)) 0 else 1)
    }

    var toast by remember { mutableStateOf<String?>(null) }
    var wipeNotice by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<WatchNotice?>(null) }
    var pendingPremiumAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var addReturn by remember { mutableStateOf(WatchScreen.Main) }
    var padPurpose by remember { mutableStateOf(PadPurpose.Set) }
    var padStage by remember { mutableIntStateOf(1) }
    var padFirst by remember { mutableStateOf("") }
    var padError by remember { mutableStateOf<String?>(null) }
    var padReset by remember { mutableIntStateOf(0) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var padBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rootView = LocalView.current

    // 300ms 刷新，保证倒计时与验证码即时切换
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(300)
        }
    }

    // 自动读取手机通过 adb 推送的激活文件（唯一码加密）
    LaunchedEffect(Unit) {
        while (true) {
            if (!premium.activated) {
                val activated = withContext(Dispatchers.IO) { readActivation(context, premium) }
                if (activated) toast = WatchI18n.pick("高级功能已激活", "Premium activated")
            }
            delay(5000)
        }
    }

    // 手机通过 adb 推送明文或唯一码加密的账号文件后自动合并
    LaunchedEffect(Unit) {
        val file = AccountImporter.latestFile(context) ?: return@LaunchedEffect
        val prefs = context.getSharedPreferences("tfa_watch_sync", Context.MODE_PRIVATE)
        val lastMtime = prefs.getLong(KEY_LAST_IMPORT_MTIME, 0L)
        if (accounts.isNotEmpty() && file.lastModified() <= lastMtime) return@LaunchedEffect

        val imported = withContext(Dispatchers.IO) {
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                if (PayloadCodec.isEncrypted(text)) {
                    val key = DeviceIdentity.keyOf(DeviceIdentity.current(context))
                    runCatching { PayloadCodec.decryptToAccounts(text, key) }.getOrNull()
                } else {
                    PayloadCodec.decodeFlexible(text)
                }
            }.getOrNull()
        }
        if (!imported.isNullOrEmpty()) {
            repo.upsertAll(imported)
            prefs.edit().putLong(KEY_LAST_IMPORT_MTIME, file.lastModified()).apply()
            toast = WatchI18n.pick("已自动导入 ${imported.size} 个密钥", "Auto-imported ${imported.size} keys")
        }
    }

    // 使用多个密钥时，提示高级功能需手机端激活（仅提示一次）
    LaunchedEffect(accounts.size, premium.activated) {
        if (accounts.size > 1 && !premium.activated && !premium.multiPromptShown) {
            notice = WatchNotice(
                title = WatchI18n.pick("已使用多个密钥", "Multiple keys in use"),
                message = WatchI18n.pick(
                    "可在 whmwws.icu/tools/ToFA/activate 生成激活码，或通过手机版激活。",
                    "Generate an activation code at whmwws.icu/tools/ToFA/activate, or activate from the phone app.",
                ),
                primaryLabel = WatchI18n.pick("知道了", "Got it"),
                secondaryLabel = null,
            )
            premium.markMultiPromptShown()
        }
    }

    // 退到后台立即重新上锁
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, secured) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && secured && passwordStore.isEnabled()) {
                screen = WatchScreen.Lock
                unlockError = null
                padReset++
            }
            // 离开应用后，若关闭了「启动时显示验证码」，回来时重新遮挡
            if (event == Lifecycle.Event.ON_STOP) {
                codesHidden = !showCodesOnLaunch
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 开屏隐私：应用从后台回到前台时重新遮挡（首次进入同样按设置决定）
    DisposableEffect(lifecycleOwner, showCodesOnLaunch) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                codesHidden = !showCodesOnLaunch
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }

    fun showPremiumPrompt(action: () -> Unit) {
        if (premium.activated) {
            action()
        } else {
            pendingPremiumAction = action
            notice = WatchNotice(
                title = WatchI18n.pick("高级功能未激活", "Premium not activated"),
                message = WatchI18n.pick(
                    "可在 whmwws.icu/tools/ToFA/activate 生成激活码，或通过手机版激活。",
                    "Generate an activation code at whmwws.icu/tools/ToFA/activate, or activate from the phone app.",
                ),
                primaryLabel = WatchI18n.pick("仍要继续", "Continue anyway"),
                secondaryLabel = WatchI18n.pick("知道了", "Got it"),
            )
        }
    }

    fun openPad(purpose: PadPurpose) {
        padPurpose = purpose
        padStage = 1
        padFirst = ""
        padError = null
        padReset++
        screen = WatchScreen.Pad
    }

    fun updateThemeMode(mode: WatchThemeMode) {
        themeMode = mode
        WatchSettings.setThemeModeId(context, mode.id)
        val systemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WatchColors.applyThemeMode(mode, systemDark)
    }

    fun wipeAll() {
        repo.wipe()
        passwordStore.completeWipe()
        secured = false
        screen = WatchScreen.Main
        wipeNotice = true
    }

    BackHandler(enabled = screen != WatchScreen.Main && screen != WatchScreen.Lock) {
        if (!(screen == WatchScreen.Pad && padBusy)) {
            screen = when (screen) {
                WatchScreen.Menu -> WatchScreen.Main
                WatchScreen.Manage, WatchScreen.Transfer, WatchScreen.UniqueCode, WatchScreen.Settings -> WatchScreen.Menu
                WatchScreen.Add -> addReturn
                WatchScreen.EditAccount -> WatchScreen.Manage
                WatchScreen.Theme, WatchScreen.Password, WatchScreen.Donate, WatchScreen.Licenses -> WatchScreen.Settings
                WatchScreen.Pad -> WatchScreen.Password
                WatchScreen.Qr -> WatchScreen.Main
                WatchScreen.Main, WatchScreen.Lock -> screen
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = WatchColors.Background) {
        // 仅 debug 包生效：合成表冠输入入口，供真机逐页取证
        DebugRotaryReceiver()
        // 方形 / 圆形布局切换：所有页面同步重排，并用弹簧曲线过渡
        AnimatedContent(
            targetState = squareUi,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = WatchSpringSpec))
                    .togetherWith(fadeOut(tween(130)))
            },
            label = "watchSquareUi",
        ) { squareMode ->
            // 关闭系统自带 overscroll（拉伸）效果：边界回弹统一由各页的 watchRotaryBounce 处理，
            // 让表冠与触控走同一套位移/弹簧，观感一致。
            CompositionLocalProvider(
                LocalWatchSquareUi provides squareMode,
                LocalOverscrollConfiguration provides null,
            ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                // 进入：新页 220ms 淡入 + 从 0.88 放大到 1（220ms）+ 轻微右入位移（屏宽 1/16），
                //       旧页 200ms 淡出；返回是同一套的完整镜像：
                //       新页 200ms 淡入，旧页 220ms 淡出 + 缩回 0.88 + 向右位移。
                // 缩放幅度从 0.94 加大到 0.88、并加入约 14dp 位移，让过渡明确可感；
                // 位移/缩放只作用于 AnimatedContent 的图层，不改动各页内部滚动与回弹。
                val forward = screenDepth(targetState) >= screenDepth(initialState)
                if (forward) {
                    (fadeIn(tween(200)) +
                        scaleIn(initialScale = 0.88f, animationSpec = tween(220)) +
                        slideInHorizontally(animationSpec = tween(220)) { it / 16 })
                        .togetherWith(fadeOut(tween(200)))
                } else {
                    fadeIn(tween(200))
                        .togetherWith(
                            fadeOut(tween(220)) +
                                scaleOut(targetScale = 0.88f, animationSpec = tween(220)) +
                                slideOutHorizontally(animationSpec = tween(220)) { it / 16 },
                        )
                }
            },
            label = "watchScreen",
        ) { current ->
            when (current) {
                WatchScreen.Lock -> {
                    val remaining = passwordStore.lockRemainingMs(now)
                    PasswordPadScreen(
                        title = tr("解锁 ToFA", "Unlock ToFA"),
                        subtitle = when {
                            padBusy -> tr("验证中…", "Verifying…")
                            remaining > 0L -> tr(
                                "冷静期 ${PasswordPolicy.formatRemaining(remaining)}",
                                "Cooldown ${PasswordPolicy.formatRemaining(remaining)}",
                            )
                            unlockError != null -> unlockError
                            else -> tr("解锁密码", "Unlock password")
                        },
                        confirmLabel = tr("解锁", "Unlock"),
                        minLength = 1,
                        enabled = remaining <= 0L && !padBusy,
                        showCancel = false,
                        resetToken = padReset,
                        onCancel = {},
                        onConfirm = { password ->
                            if (!padBusy) {
                                padBusy = true
                                scope.launch {
                                    // PBKDF2 在后台线程执行，避免手表主线程卡死
                                    val result = withContext(Dispatchers.Default) {
                                        passwordStore.verify(password.toCharArray(), now)
                                    }
                                    padBusy = false
                                    when (result) {
                                        is PasswordStore.UnlockResult.Ok -> {
                                            secured = true
                                            unlockError = null
                                            padReset++
                                            screen = WatchScreen.Main
                                        }
                                        is PasswordStore.UnlockResult.Wrong -> {
                                            unlockError = result.message
                                            padReset++
                                        }
                                        is PasswordStore.UnlockResult.Locked -> {
                                            unlockError = WatchI18n.pick(
                                                "冷静期 ${PasswordPolicy.formatRemaining(result.remainingMs)}",
                                                "Cooldown ${PasswordPolicy.formatRemaining(result.remainingMs)}",
                                            )
                                            padReset++
                                        }
                                        is PasswordStore.UnlockResult.Wipe -> wipeAll()
                                    }
                                }
                            }
                        },
                    )
                }

                WatchScreen.Main -> MainScreen(
                    accounts = accounts,
                    displayNameMode = displayNameMode,
                    pagerState = pagerState,
                    now = now,
                    codesHidden = codesHidden,
                    onRevealCodes = { codesHidden = false },
                    onOpenMenu = {
                        // 长按进入设置菜单的那一下给一次轻震动，风格与表冠逐格震动一致
                        rootView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        Log.d("RotaryCrown", "menu haptic CLOCK_TICK")
                        screen = WatchScreen.Menu
                    },
                    onAdd = {
                        addReturn = WatchScreen.Main
                        screen = WatchScreen.Add
                    },
                    qrEnabled = qrEnabled,
                    copyEnabled = copyEnabled,
                    onShowQr = { account ->
                        qrAccount = account
                        screen = WatchScreen.Qr
                    },
                    onCopyCode = { code, clearAfterMs ->
                        val digits = code.filter { it.isDigit() }
                        toast = if (ClipboardGuard.copy(context, WatchI18n.pick("ToFA 验证码", "ToFA code"), digits, clearAfterMs)) {
                            WatchI18n.pick("已复制验证码", "Code copied")
                        } else {
                            WatchI18n.pick("复制失败", "Copy failed")
                        }
                    },
                )

                WatchScreen.Menu -> MenuScreen(
                    onManage = { screen = WatchScreen.Manage },
                    onUniqueCode = { screen = WatchScreen.UniqueCode },
                    onTransfer = { screen = WatchScreen.Transfer },
                    onSettings = { screen = WatchScreen.Settings },
                    onBack = { screen = WatchScreen.Main },
                )

                WatchScreen.Manage -> ManageScreen(
                    accounts = accounts,
                    onBack = { screen = WatchScreen.Menu },
                    onEdit = { account ->
                        editAccount = account
                        screen = WatchScreen.EditAccount
                    },
                    onDelete = { repo.delete(it) },
                    onMove = { from, to -> repo.move(from, to) },
                )

                WatchScreen.Transfer -> ImportExportScreen(
                    accounts = accounts,
                    activated = premium.activated,
                    onManualInput = {
                        addReturn = WatchScreen.Transfer
                        screen = WatchScreen.Add
                    },
                    onImported = { repo.upsertAll(it) },
                    onNeedActivation = {
                        showPremiumPrompt { screen = WatchScreen.Transfer }
                    },
                    onBack = { screen = WatchScreen.Menu },
                )

                WatchScreen.Add -> AddScreen(
                    onBack = { screen = addReturn },
                    onSave = { account ->
                        repo.upsert(account)
                        screen = addReturn
                    },
                )

                WatchScreen.EditAccount -> editAccount?.let { account ->
                    RenameAccountScreen(
                        account = account,
                        onBack = { screen = WatchScreen.Manage },
                        onSave = { updated ->
                            repo.upsert(updated)
                            editAccount = null
                            screen = WatchScreen.Manage
                        },
                    )
                }

                WatchScreen.Settings -> SettingsScreen(
                    qrEnabled = qrEnabled,
                    recentsPrivacy = recentsPrivacy,
                    onToggleRecentsPrivacy = { enabled ->
                        recentsPrivacy = enabled
                        WatchSettings.setRecentsPrivacy(context, enabled)
                        (context as? MainActivity)?.applyRecentsPrivacy(enabled)
                    },
                    showCodesOnLaunch = showCodesOnLaunch,
                    onToggleShowCodesOnLaunch = { enabled ->
                        showCodesOnLaunch = enabled
                        WatchSettings.setShowCodesOnLaunch(context, enabled)
                        if (enabled) codesHidden = false
                    },
                    displayNameMode = displayNameMode,
                    onDisplayNameModeChange = { mode ->
                        displayNameMode = mode
                        WatchSettings.setDisplayNameMode(context, mode)
                    },
                    onToggleQr = { enabled ->
                        qrEnabled = enabled
                        WatchSettings.setQrEnabled(context, enabled)
                    },
                    copyEnabled = copyEnabled,
                    onToggleCopy = { enabled ->
                        if (enabled) {
                            showPremiumPrompt {
                                copyEnabled = true
                                WatchSettings.setCopyEnabled(context, true)
                            }
                        } else {
                            copyEnabled = false
                            WatchSettings.setCopyEnabled(context, false)
                        }
                    },
                    activated = premium.activated,
                    squareUi = squareUi,
                    onToggleSquareUi = { enabled ->
                        squareUi = enabled
                        WatchSettings.setSquareUi(context, enabled)
                    },
                    themeMode = themeMode,
                    onThemeModeChange = ::updateThemeMode,
                    onDonate = { if (premium.activated) screen = WatchScreen.Donate },
                    onTheme = { showPremiumPrompt { screen = WatchScreen.Theme } },
                    onPassword = { showPremiumPrompt { screen = WatchScreen.Password } },
                    onLicenses = { screen = WatchScreen.Licenses },
                    onBack = { screen = WatchScreen.Menu },
                )

                WatchScreen.Licenses -> LicensesScreen(
                    onBack = { screen = WatchScreen.Settings },
                )

                WatchScreen.Theme -> ThemeScreen(onBack = { screen = WatchScreen.Settings })

                WatchScreen.Donate -> DonateScreen(onBack = { screen = WatchScreen.Settings })

                WatchScreen.Password -> PasswordSettingsScreen(
                    store = passwordStore,
                    onBack = { screen = WatchScreen.Settings },
                    onSetPassword = { openPad(PadPurpose.Set) },
                    onChangePassword = { openPad(PadPurpose.Change) },
                    onSetEmergency = { openPad(PadPurpose.Emergency) },
                )

                WatchScreen.Pad -> {
                    val title = when (padPurpose) {
                        PadPurpose.Set -> tr("设置密码", "Set password")
                        PadPurpose.Change -> tr("更改密码", "Change password")
                        PadPurpose.Emergency -> tr("紧急密码", "Emergency password")
                    }
                    PasswordPadScreen(
                        title = if (padStage == 1) title else tr("再次输入", "Enter again"),
                        subtitle = if (padBusy) tr("保存中…", "Saving…") else padError ?: when {
                            padPurpose == PadPurpose.Emergency -> tr("输入即清空密钥", "Wipes all secrets")
                            padStage == 1 -> tr("至少 4 位", "At least 4 characters")
                            else -> tr("再次输入", "Enter again")
                        },
                        confirmLabel = if (padStage == 1) tr("下一步", "Next") else tr("保存", "Save"),
                        enabled = !padBusy,
                        resetToken = padReset,
                        onCancel = { if (!padBusy) screen = WatchScreen.Password },
                        onConfirm = { password ->
                            if (!padBusy) {
                                if (padStage == 1) {
                                    padFirst = password
                                    padStage = 2
                                    padError = null
                                    padReset++
                                } else if (password != padFirst) {
                                    padError = WatchI18n.pick("两次输入不一致", "Passwords do not match")
                                    padStage = 1
                                    padFirst = ""
                                    padReset++
                                } else {
                                    padBusy = true
                                    scope.launch {
                                        // 哈希在后台线程执行，避免手表主线程卡死
                                        withContext(Dispatchers.Default) {
                                            when (padPurpose) {
                                                PadPurpose.Set, PadPurpose.Change ->
                                                    passwordStore.setPassword(password.toCharArray())
                                                PadPurpose.Emergency ->
                                                    passwordStore.setEmergencyPassword(password.toCharArray())
                                            }
                                        }
                                        if (padPurpose != PadPurpose.Emergency) secured = true
                                        padBusy = false
                                        padError = null
                                        screen = WatchScreen.Password
                                    }
                                }
                            }
                        },
                    )
                }

                WatchScreen.Qr -> qrAccount?.let { account ->
                    QrCodeScreen(account = account, onClose = { screen = WatchScreen.Main })
                }

                WatchScreen.UniqueCode -> UniqueCodeScreen(onBack = { screen = WatchScreen.Menu })
            }
        }
            }
        }

        // 全局模态提示优先于页面返回，避免系统返回在遮挡层后面切换页面。
        BackHandler(enabled = wipeNotice || notice != null || privacyNotice != 0) {
            when {
                wipeNotice -> wipeNotice = false
                notice != null -> {
                    notice = null
                    pendingPremiumAction = null
                }
                privacyNotice == 2 -> privacyNotice = 0
                // 首启隐私协议必须明确同意，系统返回不跳过。
            }
        }

        // 复制成功 / 自动导入的小提示
        toast?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 30.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = WatchColors.SurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = message,
                        color = WatchColors.Text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // 紧急密码 / 错误上限清空后的提示
        if (wipeNotice) {
            WatchOverlay(
                title = tr("密钥已清空", "Keys wiped"),
                message = tr("已按安全策略删除全部密钥与密码设置。", "All keys and password settings were removed by policy."),
                buttons = listOf(
                    tr("知道了", "Got it") to {
                        wipeNotice = false
                    },
                ),
            )
        }

        notice?.let { current ->
            WatchOverlay(
                title = current.title,
                message = current.message,
                buttons = buildList {
                    add(
                        (current.primaryLabel) to {
                            notice = null
                            val action = pendingPremiumAction
                            pendingPremiumAction = null
                            action?.invoke()
                        },
                    )
                    current.secondaryLabel?.let { label ->
                        add(label to {
                            notice = null
                            pendingPremiumAction = null
                        })
                    }
                },
            )
        }

        // 首启隐私协议；确认后接着提示激活信息
        when (privacyNotice) {
            1 -> WatchOverlay(
                title = tr("隐私协议", "Privacy"),
                message = tr(
                    "ToFA 完全离线：密钥与验证码只保存在本机，不上传、不联网，也不含统计或广告。",
                    "Fully offline: secrets stay on this device. No upload, no network, no analytics.",
                ),
                buttons = listOf(
                    tr("同意并继续", "Agree") to {
                        WatchSettings.setPrivacyAgreed(context, true)
                        privacyNotice = 2
                    },
                ),
            )

            2 -> WatchOverlay(
                title = tr("激活说明", "Activation"),
                message = tr(
                    "可在 whmwws.icu/tools/ToFA/activate 生成激活码，或通过手机版激活。",
                    "Generate an activation code at whmwws.icu/tools/ToFA/activate, or activate from the phone app.",
                ),
                buttons = listOf(
                    tr("知道了", "Got it") to { privacyNotice = 0 },
                ),
            )
        }
    }
}

private data class WatchNotice(
    val title: String,
    val message: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
)

@Composable
private fun WatchOverlay(
    title: String,
    message: String,
    buttons: List<Pair<String, () -> Unit>>,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(WatchColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = WatchColors.Text,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(24.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = message,
                color = WatchColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                buttons.forEachIndexed { index, (label, action) ->
                    if (index > 0) Spacer(Modifier.padding(horizontal = 4.dp))
                    if (index == 0) {
                        Button(
                            onClick = action,
                            modifier = Modifier.height(30.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text(label, fontSize = 10.sp) }
                    } else {
                        OutlinedButton(
                            onClick = action,
                            modifier = Modifier.height(30.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text(label, fontSize = 10.sp) }
                    }
                }
            }
        }
    }
}

/** 读取手机推送的激活文件；必须用本机唯一码加密，避免被随意伪造。 */
private fun readActivation(context: Context, premium: PremiumStore): Boolean {
    val file = File(WatchSettings.rootDir(context), ACTIVATION_FILE_NAME)
    if (!file.exists() || file.length() <= 0) return false
    return runCatching {
        val plain = TofaCrypto.decrypt(file.readText(Charsets.UTF_8), DeviceIdentity.keyOf(DeviceIdentity.current(context)))
        val obj = JSONObject(plain)
        val ok = obj.optString("magic") == "TOFA-ACTIVATE" && obj.optBoolean("activated", false)
        if (ok) {
            premium.markActivated(true)
            file.delete()
        }
        ok
    }.getOrDefault(false)
}
