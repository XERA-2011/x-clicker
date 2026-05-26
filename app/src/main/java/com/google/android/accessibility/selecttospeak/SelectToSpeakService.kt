package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.xera.xclicker.XClickerApp
import dev.xera.xclicker.engine.A11yRuleEngine
import dev.xera.xclicker.engine.ruleSummaryFlow
import dev.xera.xclicker.engine.toRuleSummary
import dev.xera.xclicker.engine.updateSystemDefaultAppId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * XClicker 核心无障碍服务
 * 现已彻底使用 GKD 架构重构，废除复杂的单线程轮询锁，改用状态流分发。
 */
class SelectToSpeakService : AccessibilityService() {

    companion object {
        private const val TAG = "SelectToSpeakService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        var instance: SelectToSpeakService? = null
            private set
    }

    private lateinit var serviceScope: CoroutineScope
    private lateinit var ruleEngine: A11yRuleEngine

    val scope: CoroutineScope get() = serviceScope

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected (GKD Engine Active)")

        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        // Initialize default system packages (IME, Launcher, system apps)
        updateSystemDefaultAppId(applicationContext)

        ruleEngine = A11yRuleEngine(this)
        
        val container = (application as XClickerApp).container

        serviceScope.launch {
            container.ruleManager.subscriptionFlow.collect { sub ->
                if (sub != null) {
                    ruleSummaryFlow.value = sub.toRuleSummary()
                    Log.d(TAG, "Subscription updated in Engine: ${sub.apps.size} app rules loaded")
                }
            }
        }

        instance = this
        _isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        ruleEngine.onA11yEvent(event)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        instance = null
        _isRunning.value = false
    }
}
