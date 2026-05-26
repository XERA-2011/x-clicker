package dev.xera.xclicker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.xera.xclicker.data.AppContainer
import dev.xera.xclicker.data.gkd.Subscription
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
}

data class UiState(
    val isServiceRunning: Boolean = false,
    val subscription: Subscription? = null,
    val ruleCount: Int = 0,
    val globalDelay: Long = 0L,
    val currentScreen: Screen = Screen.Home,
    val isImporting: Boolean = false,
    val importResultMsg: String? = null,
    val importResultTitle: String? = null
)

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    private val _isImporting = MutableStateFlow(false)
    private val _importResultMsg = MutableStateFlow<String?>(null)
    private val _importResultTitle = MutableStateFlow<String?>(null)

    private val importStateFlow = combine(
        _isImporting,
        _importResultMsg,
        _importResultTitle
    ) { isImporting, msg, title ->
        Triple(isImporting, msg, title)
    }

    val uiState: StateFlow<UiState> = combine(
        SelectToSpeakService.isRunning,
        container.ruleManager.subscriptionFlow,
        container.settingsStore.globalDelay,
        _currentScreen,
        importStateFlow
    ) { isRunning, subscription, globalDelay, screen, importState ->
        var totalRules = 0
        subscription?.apps?.forEach { app ->
            app.groups.forEach { group ->
                totalRules += group.rules.size
            }
        }

        UiState(
            isServiceRunning = isRunning,
            subscription = subscription,
            ruleCount = totalRules,
            globalDelay = globalDelay,
            currentScreen = screen,
            isImporting = importState.first,
            importResultMsg = importState.second,
            importResultTitle = importState.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun importRulesFromUrl(url: String) {
        android.util.Log.d("MainViewModel", "准备从网络导入规则: $url")
        _isImporting.value = true
        _importResultMsg.value = null
        _importResultTitle.value = null
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 使用带有合理超时时间限制的 OkHttpClient
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(url).build()
                android.util.Log.d("MainViewModel", "开始请求网络...")
                client.newCall(request).execute().use { response ->
                    android.util.Log.d("MainViewModel", "网络请求响应完成，HTTP Code = ${response.code}")
                    if (!response.isSuccessful) {
                        _isImporting.value = false
                        _importResultTitle.value = "下载失败"
                        _importResultMsg.value = "HTTP 错误码: ${response.code}"
                        return@launch
                    }
                    val bodyText = response.body?.string()
                    if (bodyText.isNullOrEmpty()) {
                        android.util.Log.w("MainViewModel", "下载响应体内容为空")
                        _isImporting.value = false
                        _importResultTitle.value = "下载失败"
                        _importResultMsg.value = "内容为空，请检查链接是否有效"
                        return@launch
                    }

                    android.util.Log.d("MainViewModel", "内容下载成功，大小 = ${bodyText.length} 字节，准备写入 Rules 数据库")

                    val result = container.ruleManager.importRules(bodyText)
                    _isImporting.value = false
                    
                    result.onSuccess { count ->
                        android.util.Log.i("MainViewModel", "规则导入成功，共解析出 $count 个应用")
                        _importResultTitle.value = "导入成功"
                        _importResultMsg.value = "成功下载并解析了 $count 个应用的订阅规则！\n如果您发现没有生效，请确保无障碍服务已开启。"
                    }.onFailure { error ->
                        android.util.Log.e("MainViewModel", "RuleManager 导入规则失败", error)
                        _importResultTitle.value = "导入失败"
                        _importResultMsg.value = "解析规则文件失败：${error.message}"
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "网络下载或解析抛出致命异常/错误", t)
                _isImporting.value = false
                _importResultTitle.value = "操作异常"
                _importResultMsg.value = "下载或解析时发生错误：\n${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    fun clearRules() {
        container.ruleManager.clearRules()
    }

    fun setGlobalDelay(delay: Long) {
        viewModelScope.launch {
            container.settingsStore.setGlobalDelay(delay)
        }
    }

    fun clearImportResult() {
        _importResultMsg.value = null
        _importResultTitle.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        return MainViewModel(container) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
