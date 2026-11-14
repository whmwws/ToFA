package ToFA.Android.whmwws.phone.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import ToFA.Android.whmwws.phone.AppViewModel
import ToFA.Android.whmwws.phone.data.TotpAccount

enum class Screen { LIST, ADD, SCAN_ADD, SCAN_GOOGLE, SETTINGS, IMPORT_EXPORT, TRANSFER, LICENSES, CHANGELOG, EDIT_ACCOUNT }

/** 页面层级深度：用于判断进入 / 退出的动画方向。 */
private val Screen.depth: Int
    get() = when (this) {
        Screen.LIST -> 0
        Screen.ADD, Screen.SETTINGS, Screen.TRANSFER, Screen.EDIT_ACCOUNT -> 1
        Screen.SCAN_ADD, Screen.SCAN_GOOGLE, Screen.IMPORT_EXPORT, Screen.LICENSES, Screen.CHANGELOG -> 2
    }

private sealed interface HomeDialog {
    data object Donate : HomeDialog
    data object DonatedThanks : HomeDialog
    data object DeclinedConfirm : HomeDialog
    data object DeclinedDone : HomeDialog
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val accounts by vm.repo.accounts.collectAsState()
    val context = LocalContext.current
    val store = vm.passwords
    var screen by remember { mutableStateOf(Screen.LIST) }
    var backStack by remember { mutableStateOf(listOf<Screen>()) }
    var reverseTransition by remember { mutableStateOf(false) }
    var homeEditing by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<TotpAccount?>(null) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var locked by remember { mutableStateOf(store.isEnabled()) }
    // 首启隐私协议 → 关闭后接着提示激活信息（只在本机记录，不联网）
    var showPrivacy by remember { mutableStateOf(!vm.privacyAgreed) }
    var showActivationInfo by remember { mutableStateOf(false) }
    // 开屏隐私：关闭「启动时显示验证码」后先显示遮挡页，回前台重新遮挡
    var codesHidden by remember { mutableStateOf(!vm.showCodesOnLaunch) }

    fun navigate(target: Screen) {
        if (target == screen) return
        backStack = backStack + screen
        reverseTransition = false
        screen = target
    }

    fun navigateBack() {
        reverseTransition = true
        if (backStack.isEmpty()) {
            screen = Screen.LIST
        } else {
            val target = backStack.last()
            backStack = backStack.dropLast(1)
            screen = target
        }
    }

    fun popToRoot() {
        backStack = emptyList()
        reverseTransition = true
        screen = Screen.LIST
    }

    // 退到后台立即重新上锁，避免最近任务里被直接看到验证码
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && store.isEnabled()) locked = true
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_START) {
                codesHidden = !vm.showCodesOnLaunch
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (locked) {
        PhoneLockScreen(
            store = store,
            onUnlocked = { locked = false },
            onWipe = {
                vm.wipeVault()
                locked = false
                Toast.makeText(
                    context,
                    I18n.pick("紧急密码已生效，全部密钥已删除", "Emergency code used. All secrets deleted."),
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
        return
    }

    // 未激活时主页一直悬挂激活提示，用户操作后才收纳进设置
    val showPremiumBanner = !vm.premium.activated && !vm.premium.bannerResolved

    BackHandler(enabled = screen != Screen.LIST) { navigateBack() }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // 进入 / 返回方向互为镜像：深层页面从右侧进入，返回时从右侧退出
            val forward = !reverseTransition && targetState.depth >= initialState.depth
            val enter = if (forward) 1 else -1
            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { enter * it / 10 }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -enter * it / 10 })
        },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.LIST -> AccountListScreen(
                accounts = accounts,
                displayNameMode = vm.displayNameMode,
                copyEnabled = vm.copyEnabled,
                qrEnabled = vm.qrEnabled,
                codesHidden = codesHidden,
                onRevealCodes = { codesHidden = false },
                editing = homeEditing,
                onEditingChange = { homeEditing = it },
                showPremiumBanner = showPremiumBanner,
                onAdd = {
                    homeEditing = false
                    navigate(Screen.ADD)
                },
                onSettings = {
                    homeEditing = false
                    navigate(Screen.SETTINGS)
                },
                onTransfer = {
                    homeEditing = false
                    navigate(Screen.TRANSFER)
                },
                onEdit = {
                    editAccount = it
                    navigate(Screen.EDIT_ACCOUNT)
                },
                onDelete = vm::delete,
                onMove = vm::moveAccount,
                onDonate = { dialog = HomeDialog.Donate },
            )

            Screen.ADD -> AddAccountScreen(
                onBack = { navigateBack() },
                onScan = { navigate(Screen.SCAN_ADD) },
                onGoogleImport = { navigate(Screen.SCAN_GOOGLE) },
                onSave = {
                    vm.upsert(it)
                    popToRoot()
                },
            )

            Screen.SCAN_ADD -> ScanScreen(
                mode = ScanMode.ANY,
                onBack = { navigateBack() },
                onImported = {
                    vm.upsertAll(it)
                    popToRoot()
                },
                onSwitchToGoogle = { navigate(Screen.SCAN_GOOGLE) },
            )

            Screen.SCAN_GOOGLE -> ScanScreen(
                mode = ScanMode.GOOGLE_EXPORT,
                onBack = { navigateBack() },
                onImported = {
                    vm.upsertAll(it)
                    popToRoot()
                },
                onSwitchToGoogle = null,
            )

            Screen.SETTINGS -> SettingsScreen(
                vm = vm,
                onBack = { navigateBack() },
                onImportExport = { navigate(Screen.IMPORT_EXPORT) },
                onLicenses = { navigate(Screen.LICENSES) },
                onChangelog = { navigate(Screen.CHANGELOG) },
            )

            Screen.LICENSES -> LicensesScreen(onBack = { navigateBack() })

            Screen.CHANGELOG -> ChangelogScreen(onBack = { navigateBack() })

            Screen.IMPORT_EXPORT -> ImportExportScreen(
                vm = vm,
                onBack = { navigateBack() },
            )

            Screen.EDIT_ACCOUNT -> editAccount?.let { account ->
                RenameAccountScreen(
                    account = account,
                    onBack = { navigateBack() },
                    onSave = {
                        vm.upsert(it)
                        editAccount = null
                        navigateBack()
                    },
                )
            }

            Screen.TRANSFER -> AdbSyncScreen(
                vm = vm,
                onBack = { navigateBack() },
            )
        }
    }

    when (dialog) {
        HomeDialog.Donate -> PremiumDonateDialog(
            onDonated = {
                vm.premium.markDonated()
                dialog = HomeDialog.DonatedThanks
            },
            onDeclined = { dialog = HomeDialog.DeclinedConfirm },
            onDismiss = { dialog = null },
        )

        HomeDialog.DonatedThanks -> DonationThanksDialog(
            onGoTransfer = {
                dialog = null
                homeEditing = false
                navigate(Screen.TRANSFER)
            },
            onDismiss = { dialog = null },
        )

        HomeDialog.DeclinedConfirm -> PremiumDeclineConfirmDialog(
            onConfirm = {
                vm.premium.markDeclined()
                dialog = HomeDialog.DeclinedDone
            },
            onDismiss = { dialog = null },
        )

        HomeDialog.DeclinedDone -> PremiumDeclineDoneDialog(onDismiss = { dialog = null })

        null -> Unit
    }

    // 首启隐私协议：确认后接着提示激活相关信息
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(tr("隐私协议", "Privacy")) },
            text = {
                Text(
                    tr(
                        "ToFA 是离线 TOTP 工具：全部密钥与验证码只保存在本机，不上传、不联网，" +
                            "也不含统计或广告 SDK。\n\n点击「同意并继续」表示你已阅读并同意上述说明。",
                        "ToFA is an offline TOTP tool. All secrets and codes stay on this device. " +
                            "No uploads, no network access, no analytics or ads.\n\n" +
                            "Tap Agree to continue.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.agreePrivacy()
                        showPrivacy = false
                        showActivationInfo = true
                    },
                ) { Text(tr("同意并继续", "Agree")) }
            },
        )
    }

    if (showActivationInfo) {
        AlertDialog(
            onDismissRequest = { showActivationInfo = false },
            title = { Text(tr("激活说明", "Activation")) },
            text = {
                Text(
                    tr(
                        "可在 whmwws.icu/tools/ToFA 更新，激活码在 whmwws.icu/tools/ToFA/activate 生成。\n" +
                            "手机端可直接激活；手表端请在「传递」页连接手表后激活。",
                        "Updates: whmwws.icu/tools/ToFA. Activation codes: whmwws.icu/tools/ToFA/activate.\n" +
                            "Activate the phone app directly; for the watch, connect it from the Transfer page.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { showActivationInfo = false }) {
                    Text(tr("知道了", "Got it"))
                }
            },
        )
    }
}
