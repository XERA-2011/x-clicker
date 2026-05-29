package com.xera.xclicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xera.xclicker.a11y.useA11yServiceEnabledFlow
import com.xera.xclicker.a11y.useEnabledA11yServicesFlow
import com.xera.xclicker.data.CrashData
import com.xera.xclicker.data.RawSubscription
import com.xera.xclicker.data.SubsItem
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.permission.AuthReason
import com.xera.xclicker.service.A11yService
import com.xera.xclicker.store.createTextFlow
import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.AppOpsAllowRoute
import com.xera.xclicker.ui.CrashReportRoute
import com.xera.xclicker.ui.SnapshotPageRoute
import com.xera.xclicker.ui.WebViewRoute
import com.xera.xclicker.ui.component.AlertDialogOptions
import com.xera.xclicker.ui.component.InputSubsLinkOption
import com.xera.xclicker.ui.component.RuleGroupState
import com.xera.xclicker.ui.component.UploadOptions
import com.xera.xclicker.ui.home.BottomNavItem
import com.xera.xclicker.ui.home.HomeRoute
import com.xera.xclicker.ui.share.BaseViewModel
import com.xera.xclicker.util.AutomatorModeOption
import com.xera.xclicker.util.DefaultSimpleLifeImpl
import com.xera.xclicker.util.LOCAL_SUBS_ID
import com.xera.xclicker.util.LogUtils
import com.xera.xclicker.util.OnSimpleLife
import com.xera.xclicker.util.ThrottleTimer
import com.xera.xclicker.util.UpdateStatus
import com.xera.xclicker.util.appIconMapFlow
import com.xera.xclicker.util.clearCache
import com.xera.xclicker.util.client
import com.xera.xclicker.util.crashFolder
import com.xera.xclicker.util.crashTempFolder
import com.xera.xclicker.util.findOption
import com.xera.xclicker.util.json
import com.xera.xclicker.util.launchTry
import com.xera.xclicker.util.openUri
import com.xera.xclicker.util.openWeChatScaner
import com.xera.xclicker.util.runMainPost
import com.xera.xclicker.util.stopCoroutine
import com.xera.xclicker.util.subsFolder
import com.xera.xclicker.util.subsItemsFlow
import com.xera.xclicker.util.toast
import com.xera.xclicker.util.updateSubsMutex
import com.xera.xclicker.util.updateSubscription
import li.songe.loc.Loc
import kotlin.reflect.jvm.jvmName
import java.nio.file.Files
import kotlin.time.Duration.Companion.days

class MainViewModel : BaseViewModel(), OnSimpleLife by DefaultSimpleLifeImpl() {
    companion object {
        private var _instance: MainViewModel? = null
        val instance get() = _instance!!
        private var tempTermsAccepted = false
    }

    init {
        LogUtils.d("MainViewModel:init")
        _instance = this
        addCloseable {
            LogUtils.d("MainViewModel:close")
            if (_instance == this) { // 可能同时存在 2 个 MainViewModel 实例
                _instance = null
            }
        }
    }

    override val scope get() = viewModelScope

    val backStack: NavBackStack<NavKey> = NavBackStack(HomeRoute)
    val topRoute get() = backStack.last()

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@Loc loc: String = "") = runMainPost {
        if (backThrottleTimer.expired() && backStack.size > 1) {
            val old = backStack.last()
            backStack.removeAt(backStack.lastIndex)
            LogUtils.d("popPage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigatePage(
        navKey: NavKey,
        replaced: Boolean = false,
        @Loc loc: String = "",
    ) = runMainPost {
        if (navKey != backStack.last()) {
            val old = backStack.last()
            if (replaced) {
                backStack[backStack.lastIndex] = navKey
            } else {
                backStack.add(navKey)
            }
            LogUtils.d("navigatePage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigateWebPage(url: String) = navigatePage(WebViewRoute(url))

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = if (META.updateEnabled) UpdateStatus(viewModelScope) else null

    val shizukuErrorFlow = MutableStateFlow<Throwable?>(null)

    val uploadOptions = UploadOptions(this)

    val showEditCookieDlgFlow = MutableStateFlow(false)

    val inputSubsLinkOption = InputSubsLinkOption()

    val sheetSubsIdFlow = MutableStateFlow<Long?>(null)

    val appOrderListFlow = DbSet.actionLogDao.queryLatestUniqueAppIds().stateInit(emptyList())
    val appVisitOrderMapFlow = DbSet.appVisitLogDao.query().map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
    }.debounce(500).stateInit(emptyMap())

    fun addOrModifySubs(
        url: String,
        oldItem: SubsItem? = null,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (updateSubsMutex.mutex.isLocked) return@launchTry
        updateSubsMutex.withStateLock {
            val subItems = subsItemsFlow.value
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast("下载订阅文件失败\n${e.message}".trimEnd())
                return@launchTry
            }
            val newSubsRaw = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast("解析订阅文件失败\n${e.message}".trimEnd())
                return@launchTry
            }
            if (oldItem == null) {
                if (subItems.any { it.id == newSubsRaw.id }) {
                    toast("订阅已存在")
                    return@launchTry
                }
            } else {
                if (oldItem.id != newSubsRaw.id) {
                    toast("订阅id不对应")
                    return@launchTry
                }
            }
            if (newSubsRaw.id < 0) {
                toast("订阅id不可为${newSubsRaw.id}\n负数id为内部使用")
                return@launchTry
            }
            val newItem = oldItem?.copy(updateUrl = url) ?: SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            updateSubscription(newSubsRaw)
            if (oldItem == null) {
                DbSet.subsItemDao.insert(newItem)
                toast("成功添加订阅")
            } else {
                DbSet.subsItemDao.update(newItem)
                toast("成功修改订阅")
            }
        }
    }

    val ruleGroupState = RuleGroupState(this)

    val textFlow = MutableStateFlow<String?>(null)
    fun openUrl(url: String) {
        if (URLUtil.isNetworkUrl(url)) {
            textFlow.value = url
        } else {
            openUri(url)
        }
    }

    val tabFlow = MutableStateFlow(BottomNavItem.Control.key)
    val resetPageScrollEvent = MutableSharedFlow<BottomNavItem>()
    private var lastClickTabTime = 0L
    fun handleClickTab(navItem: BottomNavItem) {
        val t = System.currentTimeMillis()
        // double click
        if (navItem.key == tabFlow.value && t - lastClickTabTime < 500) {
            viewModelScope.launch { resetPageScrollEvent.emit(navItem) }
        }
        tabFlow.value = navItem.key
        lastClickTabTime = t
    }

    fun handleXClickerUri(uri: Uri) {
        val notFoundToast = { toast("未知URI\n${uri}") }
        when (uri.host) {
            "page" -> when (uri.path) {
                "" -> {
                    val tab = uri.getQueryParameter("tab")?.toIntOrNull()
                    if (tab != null && BottomNavItem.allSubObjects.any { it.key == tab }) {
                        tabFlow.value = tab
                    }
                }

                "/2" -> navigatePage(SnapshotPageRoute)
                "/3" -> navigatePage(AppOpsAllowRoute)
                else -> notFoundToast()
            }

            "invoke" -> when (uri.path) {
                "/1" -> openWeChatScaner()
                else -> notFoundToast()
            }

            else -> notFoundToast()
        }
    }

    fun handleIntent(intent: Intent) = viewModelScope.launchTry {
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleXClickerUri(uri)
        }
    }

    val termsAcceptedFlow by lazy {
        if (tempTermsAccepted) {
            MutableStateFlow(true)
        } else {
            createTextFlow(
                key = "terms_accepted",
                decode = { it == "true" },
                encode = {
                    tempTermsAccepted = it
                    it.toString()
                },
                scope = viewModelScope,
            ).apply {
                tempTermsAccepted = value
            }
        }
    }

    val githubCookieFlow by lazy {
        createTextFlow(
            key = "github_cookie",
            decode = { it ?: "" },
            encode = { it },
            private = true,
            scope = viewModelScope,
        )
    }



    private val a11yServicesFlow = useEnabledA11yServicesFlow()
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(a11yServicesFlow)

    val automatorModeFlow = storeFlow.mapNew {
        AutomatorModeOption.objects.findOption(it.automatorMode)
    }

    fun updateAutomatorMode(option: AutomatorModeOption) {
        if (automatorModeFlow.value == option) return
        storeFlow.update { it.copy(automatorMode = option.value, enableAutomator = false) }
        A11yService.instance?.shutdown()

    }

    val showShareLogDlgFlow = MutableStateFlow(false)

    var tempCrashDataList = emptyList<CrashData>()

    init {
        // preload
        appIconMapFlow.value
        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (termsAcceptedFlow.value && updateStatus?.canRecheck == true) {
            updateStatus.checkUpdate()
        }

        viewModelScope.launch(Dispatchers.IO) {
            // preload
            githubCookieFlow.value
        }
        viewModelScope.launchTry(Dispatchers.IO) {
            val list = (crashTempFolder.listFiles() ?: emptyArray()).mapNotNull {
                try {
                    json.decodeFromString<CrashData>(it.readText())
                } catch (e: Exception) {
                    LogUtils.d("解析崩溃日志失败: ${it.name}", e)
                    null
                }
            }.sortedBy { -it.mtime }
            crashTempFolder.deleteRecursively()
            val t = System.currentTimeMillis()
            crashFolder.listFiles()?.filter {
                val name = it.name
                !list.any { f -> name == f.filename }
            }?.forEach {
                val mtime = Files.getLastModifiedTime(it.toPath()).toMillis()
                if (t - mtime > 30.days.inWholeMilliseconds) {
                    it.delete()
                }
            }
            tempCrashDataList = list
            if (list.isNotEmpty()) {
                navigatePage(CrashReportRoute)
            }
        }

        // for OnSimpleLife
        onCreated()
        addCloseable { onDestroyed() }
    }
}
