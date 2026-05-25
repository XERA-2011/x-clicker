package dev.xera.xclicker.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.xera.xclicker.XClickerApp
import dev.xera.xclicker.data.AppContainer
import dev.xera.xclicker.data.gkd.AppRule
import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.RuleGroup
import dev.xera.xclicker.data.gkd.Subscription
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
import li.songe.selector.Selector
import java.util.concurrent.Executors
import java.io.File

/**
 * XClicker 无障碍服务 —— 纯正 GKD 精简版架构
 *
 * 完全摒弃旧有李跳跳混合逻辑，严格按照 GKD `App -> Group -> Rule` 进行解析和拦截。
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
    private var subscription: Subscription? = null

    // ── 智能节流与状态追踪 ──
    private var lastContentEventTime = 0L
    private var appChangeTime = 0L          // 上次应用切换的时间
    private var activityChangeTime = 0L     // 上次 Activity 切换的时间
    private var lastTriggerTime = 0L        // 上次成功触发点击的时间
    private var currentAppId = ""           // 当前处于前台的包名
    private val appActivityIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    // ── 单线程查询 ──
    private val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Volatile
    private var querying = false

    @Volatile
    private var needRequery = false

    // 记录每个 Group 的最后触发时间，用于 ActionCd (冷却)
    // Key: "$appId-$groupKey", Value: timestamp
    private val groupTriggerTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val ruleTriggerTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val ruleTriggerCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val lastTriggeredRuleKeys = java.util.concurrent.ConcurrentHashMap<String, Int>()

    @Volatile
    private var isExecutingClick = false

    // 白名单过滤（禁止对这些应用进行扫描和点击）
    private val packageWhitelist = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.miui.home",
        "dev.xera.xclicker"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        container = (application as XClickerApp).container
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        serviceScope.launch {
            container.ruleManager.subscriptionFlow.collect { sub ->
                subscription = sub
                resetAllRuleState()
                Log.d(TAG, "Subscription updated: ${sub?.apps?.size ?: 0} app rules loaded")
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

        // ── 应用和 Activity 切换检测 ──
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            var needWarmQueries = false
            
            if (packageName != currentAppId) {
                currentAppId = packageName
                appChangeTime = System.currentTimeMillis()
                resetPackageRuleState(packageName)
                needWarmQueries = true
                Log.d(TAG, "应用切换: $packageName")
            }
            
            // 验证 className 是否为真实的 Activity，防止被 Dialog/FrameLayout 覆盖
            if (className.isNotEmpty()) {
                val isActivity = try {
                    packageManager.getActivityInfo(android.content.ComponentName(packageName, className), 0)
                    true
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    false
                }
                
                if (isActivity) {
                    if (appActivityIds[packageName] != className) {
                        appActivityIds[packageName] = className
                        activityChangeTime = System.currentTimeMillis()
                        needWarmQueries = true
                        Log.d(TAG, "Activity切换: $className")
                    }
                }
            }
            
            if (needWarmQueries) {
                scheduleWarmQueries(packageName)
            }
        }

        if (packageWhitelist.contains(packageName)) return

        // ── 智能节流 ──
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            val timeSinceAppChange = now - appChangeTime
            val timeSinceLastTrigger = now - lastTriggerTime
            val timeSinceLastContent = now - lastContentEventTime

            if (timeSinceLastContent < 100 && timeSinceAppChange > 5000 && timeSinceLastTrigger > 3000) {
                return
            }
            lastContentEventTime = now
        }

        startQueryJob(event, packageName)
    }

    private fun resetPackageRuleState(packageName: String) {
        val prefix = "$packageName-"
        groupTriggerTimes.keys.removeAll { it.startsWith(prefix) }
        ruleTriggerTimes.keys.removeAll { it.startsWith(prefix) }
        ruleTriggerCounts.keys.removeAll { it.startsWith(prefix) }
        lastTriggeredRuleKeys.keys.removeAll { it.startsWith(prefix) }
    }

    private fun resetAllRuleState() {
        groupTriggerTimes.clear()
        ruleTriggerTimes.clear()
        ruleTriggerCounts.clear()
        lastTriggeredRuleKeys.clear()
    }

    private fun scheduleWarmQueries(packageName: String) {
        val delays = longArrayOf(250L, 600L, 1000L, 1500L, 2200L)
        delays.forEach { delayMs ->
            serviceScope.launch(queryDispatcher) {
                delay(delayMs)
                if (currentAppId == packageName && !isExecutingClick) {
                    retryQuery(expectedPackageName = packageName)
                }
            }
        }
    }

    @Synchronized
    private fun startQueryJob(event: AccessibilityEvent?, packageName: String) {
        if (isExecutingClick) return
        
        if (querying) {
            needRequery = true
            return
        }

        querying = true
        needRequery = false

        var eventSourceNode = try { event?.source } catch (_: Exception) { null }
        var activeWindowNode = try { rootInActiveWindow } catch (_: Exception) { null }
        val windowRootsCount = try { windows?.size ?: 0 } catch (_: Exception) { 0 }



        // 核心修复：如果 activeWindowNode 属于 Launcher 或其他应用，或者获取为 null，说明目标应用还没完全渲染
        // 我们等待最多 200ms 让 activeWindowNode 更新，这与 GKD 的 timeout 机制一致
        var activePkg = activeWindowNode?.packageName?.toString()
        if (activePkg != packageName && activePkg != "com.android.systemui") {
            var retryCount = 0
            while (retryCount < 10) {
                Thread.sleep(20)
                try { activeWindowNode?.recycle() } catch (_: Exception) {}
                activeWindowNode = try { rootInActiveWindow } catch (_: Exception) { null }
                activePkg = activeWindowNode?.packageName?.toString()
                if (activePkg == packageName) {
                    break
                }
                retryCount++
            }
            if (activePkg != null && activePkg != packageName && activePkg != "com.android.systemui") {
                Log.d(TAG, "Window mismatch after wait: event=$packageName, active=$activePkg, falling back to windows")
            }
        }

        val appRule = subscription?.apps?.find { it.id == packageName }
        val globalRule = subscription?.apps?.find { it.id == "gkd.global" || it.id == "global" }
        val rulesToEvaluate = listOfNotNull(globalRule, appRule)

        if (event != null) {
            ActionLog.log(packageName, "EVENT", "收到无障碍事件，开始扫描节点树")
        }
        val windowPackages = try { windows?.mapNotNull { it.root?.packageName?.toString() } ?: emptyList() } catch (_: Exception) { emptyList() }



        serviceScope.launch(queryDispatcher) {
            try {
                queryAndAct(eventSourceNode, activeWindowNode, packageName, rulesToEvaluate)
            } catch (e: Exception) {
                Log.e(TAG, "查询异常: $packageName", e)
            } finally {
                querying = false
                try { eventSourceNode?.recycle() } catch (_: Exception) {}
                try { activeWindowNode?.recycle() } catch (_: Exception) {}
                
                if (needRequery && !isExecutingClick) {
                    startQueryJob(null, packageName)
                }
                
                checkFutureStartJob(packageName)
            }
        }
    }

    private suspend fun queryAndAct(
        eventSourceNode: AccessibilityNodeInfo?,
        activeWindowNode: AccessibilityNodeInfo?,
        packageName: String,
        appRules: List<AppRule>
    ) {
        if (appRules.isEmpty() || appRules.all { it.groups.isEmpty() }) {
            ActionLog.log(packageName, "EVENT", "未找到该应用及全局规则配置，跳过匹配", success = false)
            return
        }

        val totalGroups = appRules.sumOf { it.groups.size }
        ActionLog.log(packageName, "EVENT", "开始 GKD 匹配: 检查 $totalGroups 个规则组")

        val now = System.currentTimeMillis()
        val currentActivityId = appActivityIds[packageName].orEmpty()
        var matchedTarget: AccessibilityNodeInfo? = null
        var matchedGroup: RuleGroup? = null
        var matchedRule: Rule? = null
        
        val nodeCache = dev.xera.xclicker.service.selector.NodeCache()
        val androidNodeTransform = dev.xera.xclicker.service.selector.createTransformWithFastQuery(nodeCache) {
            activeWindowNode
        }
        val allQueryNodes = collectQueryNodes(eventSourceNode, activeWindowNode, packageName)

        fun querySelector(node: AccessibilityNodeInfo, selector: Selector, option: li.songe.selector.MatchOption): AccessibilityNodeInfo? {
            try {
                if (selector.isMatchRoot) {
                    val result = selector.match(activeWindowNode ?: return null, androidNodeTransform, option)
                    Log.d(TAG, "  querySelector(root): selector=${selector.toString()}, result=${result != null}")
                    return result
                }
                selector.match(node, androidNodeTransform, option)?.let {
                    Log.d(TAG, "  querySelector(direct): selector=${selector.toString()}, matched=${it.className}")
                    return it
                }
                val result = androidNodeTransform.querySelector(node, selector, option)
                Log.d(TAG, "  querySelector(traverse): selector=${selector.toString()}, result=${result != null}, fastQuery=${option.fastQuery}")
                return result
            } catch (e: Exception) {
                Log.e(TAG, "Selector query failed: ${e.message}", e)
                return null
            }
        }

        fun querySelector(packageName: String, node: AccessibilityNodeInfo, selector: Selector, option: li.songe.selector.MatchOption): AccessibilityNodeInfo? {
            try {
                if (selector.isMatchRoot) {
                    val result = selector.match(activeWindowNode ?: return null, androidNodeTransform, option)
                    return result
                }
                selector.match(node, androidNodeTransform, option)?.let {
                    return it
                }
                val result = androidNodeTransform.querySelector(node, selector, option)
                return result
            } catch (e: Exception) {
                return null
            }
        }

        fun evaluateRule(packageName: String, rule: Rule, queryNode: AccessibilityNodeInfo, option: li.songe.selector.MatchOption): AccessibilityNodeInfo? {
            var finalTarget: AccessibilityNodeInfo? = null
            
            if (rule.anyMatches.isNotEmpty()) {
                var anyMatched = false
                for (matchStr in rule.anyMatches) {
                    val selector = try { Selector.parse(matchStr) } catch(e: Exception) { 
                        continue 
                    }
                    val node = querySelector(packageName, queryNode, selector, option)
                    if (node != null) {
                        anyMatched = true
                        if (finalTarget == null) {
                            finalTarget = node
                        }
                    } else {
                    }
                }
                if (!anyMatched) return null
            }
            
            for (matchStr in rule.matches) {
                val selector = try { Selector.parse(matchStr) } catch(e: Exception) { 
                    continue 
                }
                val node = querySelector(packageName, queryNode, selector, option)
                if (node == null) {
                    return null
                }
                finalTarget = node
            }
            
            for (matchStr in rule.excludeMatches) {
                val selector = try { Selector.parse(matchStr) } catch(e: Exception) { continue }
                if (querySelector(packageName, queryNode, selector, option) != null) {
                    return null
                }
            }

            if (rule.excludeAllMatches.isNotEmpty()) {
                val hasExcludedNode = rule.excludeAllMatches.any { matchStr ->
                    val selector = try { Selector.parse(matchStr) } catch(e: Exception) { return@any false }
                    querySelector(packageName, queryNode, selector, option) != null
                }
                if (hasExcludedNode) {
                    return null
                }
            }
            
            return finalTarget
        }

        for (appRule in appRules) {
            for (group in appRule.groups) {
                if (appRule.id == "gkd.global" || appRule.id == "global") {
                    val groupMatchesApp = !group.excludedAppIds.contains(packageName) &&
                        (group.matchAnyApp || group.targetAppIds.contains(packageName))
                    if (!groupMatchesApp) {
                        continue
                    }
                }

                val groupCdKey = "$packageName-${appRule.id}-${group.key}"
                val lastGroupTrigger = groupTriggerTimes[groupCdKey] ?: 0L
                if (now - lastGroupTrigger < group.actionCd) {
                    continue
                }
                val groupMaximum = group.actionMaximum
                if (groupMaximum != null) {
                    val count = ruleTriggerCounts[groupCdKey] ?: 0
                    if (count >= groupMaximum) {
                        continue
                    }
                }

                if (group.activityIds.isNotEmpty()) {
                    val groupActivityMatched = group.activityIds.any {
                        val target = if (it.startsWith(".")) packageName + it else it
                        currentActivityId == target || currentActivityId.startsWith(target)
                    }
                    if (!groupActivityMatched) {
                        continue
                    }
                }
                if (group.excludeActivityIds.isNotEmpty()) {
                    val groupActivityExcluded = group.excludeActivityIds.any {
                        val target = if (it.startsWith(".")) packageName + it else it
                        currentActivityId == target || currentActivityId.startsWith(target)
                    }
                    if (groupActivityExcluded) {
                        continue
                    }
                }

                for (rule in group.rules) {
                    if (rule.activityIds.isNotEmpty()) {
                        val activityMatched = rule.activityIds.any {
                            val target = if (it.startsWith(".")) packageName + it else it
                            currentActivityId == target || currentActivityId.startsWith(target)
                        }
                        if (!activityMatched) {
                            continue
                        }
                    }
                    if (rule.excludeActivityIds.isNotEmpty()) {
                        val activityExcluded = rule.excludeActivityIds.any {
                            val target = if (it.startsWith(".")) packageName + it else it
                            currentActivityId == target || currentActivityId.startsWith(target)
                        }
                        if (activityExcluded) {
                            continue
                        }
                    }

                    if (rule.preKeys.isNotEmpty()) {
                        val lastRuleKey = lastTriggeredRuleKeys[groupCdKey]
                        if (lastRuleKey == null || !rule.preKeys.contains(lastRuleKey)) {
                            continue
                        }
                    }

                    val ruleKey = "$groupCdKey-${rule.key ?: group.rules.indexOf(rule)}"
                    val ruleCd = rule.actionCd ?: group.actionCd
                    val lastRuleTrigger = ruleTriggerTimes[ruleKey] ?: 0L
                    if (now - lastRuleTrigger < ruleCd) {
                        continue
                    }
                    val ruleMaximum = rule.actionMaximum
                    if (ruleMaximum != null) {
                        val count = ruleTriggerCounts[ruleKey] ?: 0
                        if (count >= ruleMaximum) {
                            continue
                        }
                    }

                    val isFastQuery = rule.fastQuery || group.fastQuery
                    val option = li.songe.selector.MatchOption(fastQuery = isFastQuery)

                    val queryNodes = if (group.matchRoot || rule.matchRoot) {
                        listOfNotNull(activeWindowNode)
                    } else {
                        allQueryNodes
                    }
                    
                    if (queryNodes.isEmpty()) {
                        continue
                    }
                    
                    for (queryNode in queryNodes) {
                        matchedTarget = evaluateRule(packageName, rule, queryNode, option)
                        if (matchedTarget != null) {
                            matchedGroup = group
                            matchedRule = rule
                            break
                        }
                    }
                    if (matchedTarget != null) break
                }
                if (matchedTarget != null) break
            }
            if (matchedTarget != null) break
        }

        val targetGroup = matchedGroup
        val targetRule = matchedRule
        if (matchedTarget != null && targetGroup != null && targetRule != null) {
            val safeTarget = AccessibilityNodeInfo.obtain(matchedTarget)
            isExecutingClick = true
            try {
                ActionLog.log(packageName, "MATCH", "GKD 原生规则命中: [Group=${targetGroup.name}] [${safeTarget?.className}] (即将点击)")

                val delayMs = targetRule.actionDelay ?: targetGroup.actionDelay
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                ActionExecutor.performClick(this@XClickerService, safeTarget)
                ActionLog.log(packageName, "ACTION", "执行点击完毕")

                val appId = appRules.firstOrNull { it.groups.contains(targetGroup) }?.id ?: packageName
                val groupCdKey = "$packageName-$appId-${targetGroup.key}"
                val ruleKey = "$groupCdKey-${targetRule.key ?: targetGroup.rules.indexOf(targetRule)}"
                val triggerTime = System.currentTimeMillis()
                groupTriggerTimes[groupCdKey] = triggerTime
                ruleTriggerTimes[ruleKey] = triggerTime
                ruleTriggerCounts[groupCdKey] = (ruleTriggerCounts[groupCdKey] ?: 0) + 1
                ruleTriggerCounts[ruleKey] = (ruleTriggerCounts[ruleKey] ?: 0) + 1
                targetRule.key?.let { lastTriggeredRuleKeys[groupCdKey] = it }

                onActionTriggered()
            } finally {
                isExecutingClick = false
                try { safeTarget.recycle() } catch (_: Exception) {}
            }
        }
        if (matchedTarget == null) {
            val activityInfo = appActivityIds[packageName].orEmpty().ifEmpty { "未知Activity" }
            ActionLog.log(packageName, "EVENT", "匹配结束，未发现跳过目标 (Activity=$activityInfo)", success = false)
        }
    }

    private fun collectQueryNodes(
        eventSourceNode: AccessibilityNodeInfo?,
        activeWindowNode: AccessibilityNodeInfo?,
        packageName: String
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        eventSourceNode?.let { nodes.add(it) }
        activeWindowNode?.let { nodes.add(it) }
        val windowRoots = try {
            windows?.mapNotNull { window ->
                try { window.root } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            null
        }
        windowRoots?.forEach { root ->
            val rootPackage = root.packageName?.toString()
            if (rootPackage == packageName && nodes.none { it == root }) {
                nodes.add(root)
            }
        }
        return nodes
    }

    private fun onActionTriggered() {
        lastTriggerTime = System.currentTimeMillis()
        val packageName = currentAppId
        serviceScope.launch(queryDispatcher) {
            delay(300)
            retryQuery(expectedPackageName = packageName)
        }
    }

    private fun retryQuery(expectedPackageName: String? = null) {
        val rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
        val actualPackage = rootNode?.packageName?.toString()
        
        val targetPackage = expectedPackageName ?: actualPackage ?: run {
            try { rootNode?.recycle() } catch (_: Exception) {}
            checkFutureStartJob(expectedPackageName)
            return
        }

        if (packageWhitelist.contains(targetPackage)) {
            try { rootNode?.recycle() } catch (_: Exception) {}
            checkFutureStartJob(expectedPackageName)
            return
        }
        
        try { rootNode?.recycle() } catch (_: Exception) {}
        startQueryJob(null, targetPackage)
    }

    private fun checkFutureStartJob(expectedPackageName: String? = null) {
        val t = System.currentTimeMillis()
        if (t - appChangeTime < 3000L || t - lastTriggerTime < 3000L) {
            serviceScope.launch(queryDispatcher) {
                delay(300)
                retryQuery(expectedPackageName ?: currentAppId.takeIf { it.isNotEmpty() })
            }
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
