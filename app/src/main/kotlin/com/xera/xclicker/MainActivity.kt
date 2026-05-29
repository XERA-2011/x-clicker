package com.xera.xclicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.xera.xclicker.a11y.topActivityFlow
import com.xera.xclicker.a11y.updateSystemDefaultAppId
import com.xera.xclicker.a11y.updateTopActivity
import com.xera.xclicker.permission.AuthDialog
import com.xera.xclicker.permission.updatePermissionState
import com.xera.xclicker.service.A11yService

import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.A11YScopeAppListRoute
import com.xera.xclicker.ui.A11yEventLogPage
import com.xera.xclicker.ui.A11yEventLogRoute
import com.xera.xclicker.ui.A11yScopeAppListPage
import com.xera.xclicker.ui.AboutPage
import com.xera.xclicker.ui.AboutRoute
import com.xera.xclicker.ui.ActionLogPage
import com.xera.xclicker.ui.ActionLogRoute
import com.xera.xclicker.ui.ActivityLogPage
import com.xera.xclicker.ui.ActivityLogRoute

import com.xera.xclicker.ui.AppConfigPage
import com.xera.xclicker.ui.AppConfigRoute
import com.xera.xclicker.ui.AppOpsAllowPage
import com.xera.xclicker.ui.AppOpsAllowRoute
import com.xera.xclicker.ui.AuthA11yPage
import com.xera.xclicker.ui.AuthA11yRoute
import com.xera.xclicker.ui.BlockA11yAppListPage
import com.xera.xclicker.ui.BlockA11yAppListRoute
import com.xera.xclicker.ui.CrashReportPage
import com.xera.xclicker.ui.CrashReportRoute
import com.xera.xclicker.ui.EditBlockAppListPage
import com.xera.xclicker.ui.EditBlockAppListRoute
import com.xera.xclicker.ui.ImagePreviewPage
import com.xera.xclicker.ui.ImagePreviewRoute
import com.xera.xclicker.ui.SlowGroupPage
import com.xera.xclicker.ui.SlowGroupRoute
import com.xera.xclicker.ui.SnapshotPage
import com.xera.xclicker.ui.SnapshotPageRoute
import com.xera.xclicker.ui.SubsAppGroupListPage
import com.xera.xclicker.ui.SubsAppGroupListRoute
import com.xera.xclicker.ui.SubsAppListPage
import com.xera.xclicker.ui.SubsAppListRoute
import com.xera.xclicker.ui.SubsCategoryGroupPage
import com.xera.xclicker.ui.SubsCategoryGroupRoute
import com.xera.xclicker.ui.SubsCategoryPage
import com.xera.xclicker.ui.SubsCategoryRoute
import com.xera.xclicker.ui.SubsGlobalGroupExcludePage
import com.xera.xclicker.ui.SubsGlobalGroupExcludeRoute
import com.xera.xclicker.ui.SubsGlobalGroupListPage
import com.xera.xclicker.ui.SubsGlobalGroupListRoute
import com.xera.xclicker.ui.UpsertRuleGroupPage
import com.xera.xclicker.ui.UpsertRuleGroupRoute
import com.xera.xclicker.ui.WebViewPage
import com.xera.xclicker.ui.WebViewRoute
import com.xera.xclicker.ui.component.BuildDialog
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.component.ShareLogDlg
import com.xera.xclicker.ui.component.SubsSheet
import com.xera.xclicker.ui.component.TermsAcceptDialog
import com.xera.xclicker.ui.component.TextDialog
import com.xera.xclicker.ui.home.HomePage
import com.xera.xclicker.ui.home.HomeRoute
import com.xera.xclicker.ui.share.FixedWindowInsets
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.style.AppTheme
import com.xera.xclicker.util.AndroidTarget
import com.xera.xclicker.util.BarUtils
import com.xera.xclicker.util.EditGithubCookieDlg
import com.xera.xclicker.util.KeyboardUtils
import com.xera.xclicker.util.LogUtils
import com.xera.xclicker.util.ShortUrlSet
import com.xera.xclicker.util.appInfoMapFlow
import com.xera.xclicker.util.componentName
import com.xera.xclicker.util.copyText
import com.xera.xclicker.util.fixSomeProblems
import com.xera.xclicker.util.launchTry
import com.xera.xclicker.util.mapState
import com.xera.xclicker.util.openApp
import com.xera.xclicker.util.openUri
import com.xera.xclicker.util.shizukuAppId
import com.xera.xclicker.util.throttle
import com.xera.xclicker.util.toast
import kotlin.concurrent.Volatile
import kotlin.reflect.jvm.jvmName

class MainActivity : ComponentActivity() {
    val startTime = System.currentTimeMillis()
    val mainVm by viewModels<MainViewModel>()
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    val imeFullHiddenFlow = MutableStateFlow(true)
    val imePlayingFlow = MutableStateFlow(false)

    private val imeVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true  // fix #1315

    var topBarWindowInsets by mutableStateOf(WindowInsets(top = BarUtils.getStatusBarHeight()))

    private fun watchKeyboardVisible() {
        if (AndroidTarget.R) {
            ViewCompat.setWindowInsetsAnimationCallback(
                window.decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onStart(
                        animation: WindowInsetsAnimationCompat,
                        bounds: WindowInsetsAnimationCompat.BoundsCompat
                    ): WindowInsetsAnimationCompat.BoundsCompat {
                        imePlayingFlow.update { imeVisible }
                        return super.onStart(animation, bounds)
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        imeFullHiddenFlow.update { !imeVisible }
                        imePlayingFlow.update { false }
                        super.onEnd(animation)
                    }
                })
        } else {
            KeyboardUtils.registerSoftInputChangedListener(window) { height ->
                // onEnd
                imeFullHiddenFlow.update { height == 0 }
            }
        }
    }

    suspend fun hideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            imeFullHiddenFlow.drop(1).first()
            return true
        }
        return false
    }

    fun justHideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            return true
        }
        return false
    }

    suspend fun pickFile(contentType: String): Uri? {
        val u = launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = contentType
        }).data?.data
        if (u == null) {
            toast("未选择文件")
        }
        return u
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixSomeProblems()
        super.onCreate(savedInstanceState)
        LogUtils.d()
        mainVm
        launcher
        pickContentLauncher
        lifecycleScope.launch {
            storeFlow.mapState(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                app.activityManager.appTasks.forEach { task ->
                    task.setExcludeFromRecents(it)
                }
            }
        }
        addOnNewIntentListener {
            mainVm.handleIntent(it)
            intent = null
        }
        watchKeyboardVisible()

        setContent {
            val latestInsets = TopAppBarDefaults.windowInsets
            val density = LocalDensity.current
            if (latestInsets.getTop(density) > topBarWindowInsets.getTop(density)) {
                topBarWindowInsets = FixedWindowInsets(latestInsets)
            }
            CompositionLocalProvider(
                LocalMainViewModel provides mainVm
            ) {
                AppTheme {
                    NavDisplay(
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        backStack = mainVm.backStack,
                        onBack = mainVm::popPage,
                        entryProvider = entryProvider {
                            entry<HomeRoute> { HomePage() }
                            entry<AuthA11yRoute> { AuthA11yPage() }
                            entry<AboutRoute> { AboutPage() }
                            entry<BlockA11yAppListRoute> { BlockA11yAppListPage() }

                            entry<SnapshotPageRoute> { SnapshotPage() }
                            entry<AppOpsAllowRoute> { AppOpsAllowPage() }
                            entry<A11YScopeAppListRoute> { A11yScopeAppListPage() }
                            entry<ActivityLogRoute> { ActivityLogPage() }
                            entry<A11yEventLogRoute> { A11yEventLogPage() }
                            entry<EditBlockAppListRoute> { EditBlockAppListPage() }
                            entry<SlowGroupRoute> { SlowGroupPage() }
                            entry<SubsAppListRoute> { SubsAppListPage(it) }
                            entry<WebViewRoute> { WebViewPage(it) }
                            entry<SubsCategoryRoute> { SubsCategoryPage(it) }
                            entry<SubsGlobalGroupListRoute> { SubsGlobalGroupListPage(it) }
                            entry<SubsGlobalGroupExcludeRoute> { SubsGlobalGroupExcludePage(it) }
                            entry<ActionLogRoute> { ActionLogPage(it) }
                            entry<ImagePreviewRoute> { ImagePreviewPage(it) }
                            entry<UpsertRuleGroupRoute> { UpsertRuleGroupPage(it) }
                            entry<SubsAppGroupListRoute> { SubsAppGroupListPage(it) }
                            entry<AppConfigRoute> { AppConfigPage(it) }
                            entry<CrashReportRoute> { CrashReportPage() }
                            entry<SubsCategoryGroupRoute> { SubsCategoryGroupPage(it) }
                        },
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                    )
                    if (!mainVm.termsAcceptedFlow.collectAsState().value) {
                        TermsAcceptDialog()
                    } else {
                        AccessRestrictedSettingsDlg()
                        AuthDialog(mainVm.authReasonFlow)
                        BuildDialog(mainVm.dialogFlow)
                        mainVm.uploadOptions.ShowDialog()
                        EditGithubCookieDlg()
                        mainVm.updateStatus?.UpgradeDialog()
                        SubsSheet(mainVm, mainVm.sheetSubsIdFlow)
                        mainVm.inputSubsLinkOption.ContentDialog()
                        mainVm.ruleGroupState.Render()
                        TextDialog(mainVm.textFlow)
                        ShareLogDlg(mainVm.showShareLogDlgFlow)
                    }
                }
            }
            LaunchedEffect(null) {
                intent?.let {
                    mainVm.handleIntent(it)
                    intent = null
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LogUtils.d()
        activityVisibleState++
        if (topActivityFlow.value.appId != META.appId) {
            synchronized(topActivityFlow) {
                updateTopActivity(
                    META.appId,
                    MainActivity::class.jvmName
                )
            }
        }
    }

    var isFirstResume = true
    override fun onResume() {
        super.onResume()
        LogUtils.d()
        if (isFirstResume && startTime - app.startTime < 2000) {
            isFirstResume = false
        } else {
            syncFixState()
        }
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d()
        activityVisibleState--
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.d()
    }
}

@Volatile
private var activityVisibleState = 0
val isActivityVisible get() = activityVisibleState > 0

val activityNavSourceName by lazy { META.appId + ".activity.nav.source" }

fun Activity.navToMainActivity() {
    if (intent != null) {
        val navIntent = Intent(intent)
        navIntent.component = MainActivity::class.componentName
        navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        navIntent.putExtra(activityNavSourceName, this::class.jvmName)
        startActivity(navIntent)
    }
    finish()
}

private val syncStateMutex = Mutex()
fun syncFixState() {
    appScope.launchTry(Dispatchers.IO) {
        if (syncStateMutex.isLocked) {
            LogUtils.d("syncFixState isLocked")
        }
        syncStateMutex.withLock {
            updateSystemDefaultAppId()
            updatePermissionState()
        }
    }
}



val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

@Composable
fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsState()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsState()
    val mainVm = LocalMainViewModel.current
    val isA11yPage = mainVm.topRoute is AuthA11yRoute
    LaunchedEffect(isA11yPage, accessRestrictedSettingsShow) {
        if (isA11yPage && accessRestrictedSettingsShow && !a11yRunning) {
            toast("请重新授权以解除限制")
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isA11yPage && !a11yRunning) {
        AlertDialog(
            title = {
                Text(text = "权限受限")
            },
            text = {
                Text(text = "当前操作权限「访问受限设置」已被限制, 请先解除限制")
            },
            onDismissRequest = {
                accessRestrictedSettingsShowFlow.value = false
            },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    mainVm.navigateWebPage(ShortUrlSet.URL2)
                }) {
                    Text(text = "解除")
                }
            },
            dismissButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

