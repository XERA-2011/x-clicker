package dev.xera.xclicker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.xera.xclicker.data.AppContainer
import dev.xera.xclicker.data.model.AppRuleSet
import dev.xera.xclicker.service.XClickerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.ObjectMapper

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
}

data class UiState(
    val isServiceRunning: Boolean = false,
    val rules: List<AppRuleSet> = emptyList(),
    val ruleCount: Int = 0,
    val globalDelay: Long = 0L,
    val currentScreen: Screen = Screen.Home,
    val importResult: String? = null
)

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    private val _importResult = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        XClickerService.isRunning,
        container.ruleManager.rulesFlow,
        container.settingsStore.globalDelay,
        _currentScreen,
        _importResult
    ) { isRunning, rules, globalDelay, screen, importResult ->
        UiState(
            isServiceRunning = isRunning,
            rules = rules,
            ruleCount = rules.sumOf { it.popupRules.size },
            globalDelay = globalDelay,
            currentScreen = screen,
            importResult = importResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun importRules(json: String) {
        val result = container.ruleManager.importRules(json)
        result.onSuccess { count ->
            _importResult.value = "成功导入 $count 条规则"
        }.onFailure { error ->
            _importResult.value = "导入失败：${error.message}"
        }
    }

    fun importRulesFromUrl(url: String) {
        _importResult.value = "正在下载规则..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _importResult.value = "下载失败：HTTP ${response.code}"
                        return@launch
                    }
                    val bodyText = response.body?.string()
                    if (bodyText.isNullOrEmpty()) {
                        _importResult.value = "下载失败：内容为空"
                        return@launch
                    }

                    // 使用 Jackson 宽容解析 JSON5
                    val factory = JsonFactory.builder()
                        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                        .build()
                    val mapper = ObjectMapper(factory)
                    
                    val rootNode = mapper.readTree(bodyText)
                    val standardJson = rootNode.toString()

                    withContext(Dispatchers.Main) {
                        importRules(standardJson)
                    }
                }
            } catch (e: Exception) {
                _importResult.value = "网络或解析错误：${e.message}"
            }
        }
    }

    fun exportRules(): String {
        return container.ruleManager.exportRules()
    }

    fun clearRules() {
        container.ruleManager.clearRules()
        _importResult.value = null
    }

    fun setGlobalDelay(delay: Long) {
        viewModelScope.launch {
            container.settingsStore.setGlobalDelay(delay)
        }
    }

    fun clearImportResult() {
        _importResult.value = null
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
