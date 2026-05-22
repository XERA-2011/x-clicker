package dev.xera.xclicker.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.xera.xclicker.XClickerApp
import dev.xera.xclicker.data.AppContainer
import dev.xera.xclicker.data.model.AppRuleSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * x-clicker 无障碍服务 —— 对标 GKD 的 A11yRuleEngine 架构重写。
 *
 * 核心改进：
 * 1. 智能节流：应用切换后 5 秒内不节流，成功触发后 3 秒内不节流
 * 2. 单线程查询：用专用 Dispatcher + @Synchronized 防止并发竞争
 * 3. 正确的节点获取：优先 event.source 快速匹配，失败回退 rootInActiveWindow 全树搜索
 * 4. 点击后 300ms 重查：处理连环弹窗
 * 5. GKD 式点击策略：先 ACTION_CLICK，失败则 dispatchGesture
 */
class XClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "XClickerService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        var instance: XClickerService? = null
            private set
    }

    private lateinit var container: AppContainer
    private lateinit var serviceScope: CoroutineScope
    private var cachedRules: List<AppRuleSet> = emptyList()
    private var globalDelayMs: Long = 0L

    // ── 智能节流相关（对标 GKD A11yRuleEngine.kt:113-139） ──
    private var lastContentEventTime = 0L
    private var appChangeTime = 0L          // 上次应用切换的时间
    private var lastTriggerTime = 0L        // 上次成功触发点击的时间
    private var lastTopPackage = ""         // 上次处于前台的包名

    // ── 单线程查询（对标 GKD A11yRuleEngine.kt:50-52, 241-252） ──
    private val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Volatile
    private var querying = false

    @Volatile
    private var needRequery = false

    // 记录每条规则的最后触发时间，用于冷却 (Action Cooldown)
    private val ruleTriggerTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    private var isExecutingClick = false

    // ── 全局兜底关键字 ──
    private val globalSkipKeywords = listOf(
        "跳过", "跳過", "skip", "Skip", "SKIP",
        "关闭广告", "关闭推荐", "关闭弹窗",
        "跳过广告"
    )
    private val packageWhitelist = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher",
        "dev.xera.xclicker",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.sohu.inputmethod.sogou",
        "com.baidu.input",
        "com.iflytek.vflynote"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        container = (application as XClickerApp).container
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Load initial rules and observe changes
        serviceScope.launch {
            container.ruleManager.rulesFlow.collect { rules ->
                cachedRules = rules
                Log.d(TAG, "Rules updated: ${rules.size} app rule sets loaded")
            }
        }

        // Observe global delay setting
        serviceScope.launch {
            container.settingsStore.globalDelay.collect { delay ->
                globalDelayMs = delay
            }
        }

        instance = this
        _isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // 白名单过滤
        if (packageWhitelist.contains(packageName)) return

        // ── 应用切换检测 ──
        if (packageName != lastTopPackage) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastTopPackage = packageName
                appChangeTime = System.currentTimeMillis()
                Log.d(TAG, "应用切换: $packageName")
            }
        }

        // ── 智能节流（对标 GKD A11yRuleEngine.kt:134-139） ──
        // 核心思想：刚切换应用的前 5 秒 和 最近成功触发后的 3 秒内，绝不节流！
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            val timeSinceAppChange = now - appChangeTime
            val timeSinceLastTrigger = now - lastTriggerTime
            val timeSinceLastContent = now - lastContentEventTime

            if (timeSinceLastContent < 100 && timeSinceAppChange > 5000 && timeSinceLastTrigger > 3000) {
                // 应用已稳定运行超过 5 秒，且最近 3 秒没有触发过，节流 100ms 内的重复事件
                return
            }
            lastContentEventTime = now
        }

        // ── 启动查询任务 ──
        startQueryJob(event, packageName)
    }

    /**
     * 对标 GKD 的 startQueryJob —— @Synchronized 保证同一时间只有一个查询在执行。
     *
     * @see A11yRuleEngine.startQueryJob (gkd/app/src/main/kotlin/li/songe/gkd/a11y/A11yRuleEngine.kt:244-275)
     */
    @Synchronized
    private fun startQueryJob(event: AccessibilityEvent?, packageName: String) {
        if (isExecutingClick) return // 如果正在等待点击，忽略新事件
        
        if (querying) {
            needRequery = true // 标记需要重查，不要漏掉这个新事件（可能是布局刚完成）
            return
        }

        querying = true
        needRequery = false

        // 在主线程同步获取节点快照，避免协程延迟导致节点过期
        val eventSourceNode = try { event?.source } catch (_: Exception) { null }
        val activeWindowNode = try { rootInActiveWindow } catch (_: Exception) { null }

        if (eventSourceNode == null && activeWindowNode == null) {
            querying = false
            return
        }

        val packageHash = packageName.hashCode().toString()
        val ruleSet = cachedRules.find { it.packageHash == packageName || it.packageHash == packageHash }

        ActionLog.log(packageName, "EVENT", "收到无障碍事件，开始扫描节点树")

        serviceScope.launch(queryDispatcher) {
            try {
                queryAndAct(eventSourceNode, activeWindowNode, packageName, ruleSet)
            } catch (e: Exception) {
                Log.e(TAG, "查询异常: $packageName", e)
                ActionLog.log(packageName, "ERROR", "查询过程发生异常: ${e.message}", success = false)
            } finally {
                querying = false
                // 回收节点
                try { eventSourceNode?.recycle() } catch (_: Exception) {}
                try { activeWindowNode?.recycle() } catch (_: Exception) {}
                
                // 如果在查询期间来了新事件，马上重查一次最新的节点树
                if (needRequery && !isExecutingClick) {
                    startQueryJob(null, packageName)
                }
            }
        }
    }

    /**
     * 核心查询与执行逻辑 —— 对标 GKD 的 queryAction。
     *
     * 节点获取策略（对标 GKD A11yRuleEngine.kt:358-398）：
     * 1. 优先在 event.source 上搜索（速度快，直接命中事件发生的子树）
     * 2. 如果 event.source 没找到，回退到 rootInActiveWindow 全树搜索
     */
    private suspend fun queryAndAct(
        eventSourceNode: AccessibilityNodeInfo?,
        activeWindowNode: AccessibilityNodeInfo?,
        packageName: String,
        ruleSet: AppRuleSet?
    ) {
        // ── 尝试专属规则匹配 ──
        if (ruleSet != null && ruleSet.lttService) {
            for (popupRule in ruleSet.popupRules) {
                // Step 1: 先在 event.source 子树上找
                var targetNode = if (eventSourceNode != null) {
                    NodeMatcher.findTarget(eventSourceNode, popupRule)
                } else null

                // Step 2: 没找到则在 rootInActiveWindow 全树上找
                if (targetNode == null && activeWindowNode != null) {
                    targetNode = NodeMatcher.findTarget(activeWindowNode, popupRule)
                }

                if (targetNode != null) {
                    // Check Cooldown (3000ms by default)
                    val lastTrigger = ruleTriggerTimes[popupRule.id] ?: 0L
                    if (System.currentTimeMillis() - lastTrigger < 3000) {
                        try { targetNode.recycle() } catch (_: Exception) {}
                        continue
                    }

                    // 匹配到了！执行点击
                    isExecutingClick = true
                    val totalDelay = popupRule.delay + globalDelayMs
                    
                    val b = android.graphics.Rect()
                    targetNode.getBoundsInScreen(b)
                    
                    ActionLog.log(packageName, "MATCH", "专属规则命中: ${targetNode.text ?: targetNode.contentDescription} [${b.width()}x${b.height()}] (即将点击)")
                    if (totalDelay > 0) delay(totalDelay)

                    Log.i(TAG, "规则命中: rule=${popupRule.id}, text=${targetNode.text ?: targetNode.contentDescription}, pkg=$packageName")
                    ActionExecutor.execute(
                        service = this@XClickerService,
                        node = targetNode,
                        rule = popupRule,
                        action = popupRule.action
                    )
                    ActionLog.log(packageName, "ACTION", "已执行专属规则动作")
                    
                    // 记录规则的触发时间
                    ruleTriggerTimes[popupRule.id] = System.currentTimeMillis()

                    onActionTriggered()
                    isExecutingClick = false
                    try { targetNode.recycle() } catch (_: Exception) {}
                    return
                }
            }
        }

        // ── 全局兜底匹配 ──
        val lastGlobalTrigger = ruleTriggerTimes["GLOBAL_FALLBACK"] ?: 0L
        if (System.currentTimeMillis() - lastGlobalTrigger > 1000) {
            // 按照范围从小到大搜索：先事件子树，再当前活动窗口全树，最后搜索所有窗口（覆盖悬浮窗/Dialog）
            var fallbackNode = findFallbackNode(eventSourceNode)
                ?: findFallbackNode(activeWindowNode)

        if (fallbackNode == null) {
            val allWindows = try { windows } catch (_: Exception) { null }
            if (allWindows != null) {
                for (window in allWindows) {
                    val root = try { window.root } catch (_: Exception) { null }
                    if (root != null && root != activeWindowNode) {
                        fallbackNode = findFallbackNode(root)
                        if (fallbackNode != null) {
                            try { root.recycle() } catch (_: Exception) {}
                            break
                        }
                    }
                    try { root?.recycle() } catch (_: Exception) {}
                }
            }
        }

        if (fallbackNode != null) {
            isExecutingClick = true
            
            val b = android.graphics.Rect()
            fallbackNode.getBoundsInScreen(b)
            
            ActionLog.log(packageName, "MATCH", "全局兜底命中: ${fallbackNode.text ?: fallbackNode.contentDescription} [${b.width()}x${b.height()}] (即将点击)")
            if (globalDelayMs > 0) delay(globalDelayMs)

            Log.i(TAG, "全局兜底命中: text=${fallbackNode.text ?: fallbackNode.contentDescription}, pkg=$packageName")
            ActionExecutor.performClick(this@XClickerService, fallbackNode)
            ActionLog.log(packageName, "ACTION", "已执行全局物理兜底点击")
            
            // 全局兜底也加上冷却，避免疯狂点击同一个位置
            ruleTriggerTimes["GLOBAL_FALLBACK"] = System.currentTimeMillis()

            onActionTriggered()
            isExecutingClick = false
            try { fallbackNode.recycle() } catch (_: Exception) {}
        }
        }
    }

    /**
     * 成功执行动作后的回调 —— 对标 GKD 的 rule.trigger() + 300ms 重查。
     *
     * @see A11yRuleEngine.kt:420-424
     */
    private fun onActionTriggered() {
        lastTriggerTime = System.currentTimeMillis()

        // 对标 GKD：成功点击后 300ms 重查，处理连环弹窗
        serviceScope.launch(queryDispatcher) {
            delay(300)
            retryQuery()
        }
    }

    /**
     * 点击后的重试查询 —— 重新获取当前屏幕节点进行一次完整扫描。
     */
    private fun retryQuery() {
        val rootNode = try { rootInActiveWindow } catch (_: Exception) { null } ?: return
        val packageName = rootNode.packageName?.toString() ?: run {
            try { rootNode.recycle() } catch (_: Exception) {}
            return
        }

        if (packageWhitelist.contains(packageName)) {
            try { rootNode.recycle() } catch (_: Exception) {}
            return
        }

        val packageHash = packageName.hashCode().toString()
        val ruleSet = cachedRules.find { it.packageHash == packageName || it.packageHash == packageHash }

        serviceScope.launch(queryDispatcher) {
            try {
                queryAndAct(null, rootNode, packageName, ruleSet)
            } catch (e: Exception) {
                Log.e(TAG, "重查异常: $packageName", e)
            } finally {
                try { rootNode.recycle() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 全局关键字兜底搜索 —— 在节点树中查找包含跳过关键字的节点。
     * 只过滤 bounds 为空的幽灵节点，不检查 isVisibleToUser。
     */
    private fun findFallbackNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectFallbackCandidates(node, candidates)
        
        // 选择面积最小的节点（避免选中整个全屏的广告容器）
        val best = candidates.minByOrNull { n ->
            val bounds = Rect()
            n.getBoundsInScreen(bounds)
            bounds.width().toLong() * bounds.height().toLong()
        }
        
        // 回收其他候选节点避免内存泄漏
        for (c in candidates) {
            if (c != best) {
                try { c.recycle() } catch (_: Exception) {}
            }
        }
        
        return best
    }

    private fun collectFallbackCandidates(node: AccessibilityNodeInfo?, candidates: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if (globalSkipKeywords.any { text.contains(it) || desc.contains(it) }) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                candidates.add(AccessibilityNodeInfo.obtain(node))
            } else {
                ActionLog.log(node.packageName?.toString() ?: "", "MATCH", "找到跳过关键字但被过滤 (尺寸为空)", success = false)
            }
        }

        for (i in 0 until node.childCount) {
            collectFallbackCandidates(node.getChild(i), candidates)
        }
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
