package com.google.android.accessibility.selecttospeak

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
import dev.xera.xclicker.service.ActionLog
import dev.xera.xclicker.service.ActionExecutor
import dev.xera.xclicker.service.RuleExecutionTracker

/**
 * XClicker 无障碍服务 —— 纯正 GKD 精简版架构 (已伪装为 SelectToSpeak 官方服务)
 */
class SelectToSpeakService : AccessibilityService() {

    companion object {
        private const val TAG = "SelectToSpeakService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        var instance: SelectToSpeakService? = null
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
    private var isExecutingClick = false

    // 白名单过滤（禁止对这些应用进行扫描 and 点击）
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

    private fun getTrueActivePackage(): String? {
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val pkg = root?.packageName?.toString()
        try { root?.recycle() } catch (_: Exception) {}
        return pkg
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

        if (currentAppId.isEmpty()) {
            val truePkg = if (packageName != "com.android.systemui") packageName else (getTrueActivePackage() ?: packageName)
            currentAppId = truePkg
            appChangeTime = System.currentTimeMillis()
            resetPackageRuleState(truePkg)
            Log.d(TAG, "初始化前台应用: $truePkg")
        }

        // ── 应用和 Activity 切换检测 ──
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            val truePkg = if (packageName != "com.android.systemui") packageName else (getTrueActivePackage() ?: packageName)
            
            if (truePkg != currentAppId) {
                currentAppId = truePkg
                appChangeTime = System.currentTimeMillis()
                resetPackageRuleState(truePkg)
                Log.d(TAG, "应用切换: $truePkg")
            }
            
            // 验证 className 是否为真实的 Activity，防止被 Dialog/FrameLayout 覆盖
            if (className.isNotEmpty()) {
                val isActivity = try {
                    packageManager.getActivityInfo(android.content.ComponentName(truePkg, className), 0)
                    true
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    false
                }
                
                if (isActivity) {
                    if (appActivityIds[truePkg] != className) {
                        appActivityIds[truePkg] = className
                        activityChangeTime = System.currentTimeMillis()
                        resetActivityRuleState(truePkg)
                        Log.d(TAG, "Activity切换: $className")
                    }
                }
            }
        }

        if (packageName != currentAppId) return

        if (packageWhitelist.contains(packageName) || !hasRulesForPackage(packageName)) return

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
        RuleExecutionTracker.resetPackage(packageName)
    }

    private fun resetActivityRuleState(packageName: String) {
        RuleExecutionTracker.resetActivity(packageName, subscription)
    }

    private fun resetAllRuleState() {
        RuleExecutionTracker.clear()
    }

    private fun hasRulesForPackage(packageName: String): Boolean {
        val sub = subscription ?: return false
        val appRule = sub.apps.find { it.id == packageName }
        return appRule != null && appRule.groups.isNotEmpty()
    }

    @Volatile
    private var pendingQueryPkg: String? = null

    @Synchronized
    private fun startQueryJob(event: AccessibilityEvent?, packageName: String) {
        if (isExecutingClick) return
        if (querying) {
            // 如果正在查询，标记有积压的事件，等当前查询结束后再次查询
            pendingQueryPkg = packageName
            return
        }

        if (packageWhitelist.contains(packageName) || !hasRulesForPackage(packageName)) {
            return
        }

        querying = true
        pendingQueryPkg = null

        val appRule = subscription?.apps?.find { it.id == packageName }
        val globalRule = subscription?.apps?.find { it.id == "gkd.global" || it.id == "global" }
        val rulesToEvaluate = listOfNotNull(globalRule, appRule)

        if (event != null) {
            ActionLog.log(packageName, "EVENT", "收到无障碍事件，开始扫描节点树")
        }

        serviceScope.launch(queryDispatcher) {
            var activeWindowNode: AccessibilityNodeInfo? = null
            try {
                activeWindowNode = try { rootInActiveWindow } catch (_: Exception) { null }
                var activePkg = activeWindowNode?.packageName?.toString()
                if (activePkg != packageName && activePkg != "com.android.systemui") {
                    var retryCount = 0
                    while (retryCount < 10) {
                        delay(20)
                        try { activeWindowNode?.recycle() } catch (_: Exception) {}
                        activeWindowNode = try { rootInActiveWindow } catch (_: Exception) { null }
                        activePkg = activeWindowNode?.packageName?.toString()
                        if (activePkg == packageName) {
                            break
                        }
                        retryCount++
                    }
                }

                queryAndAct(activeWindowNode, packageName, rulesToEvaluate)
            } catch (e: Exception) {
                Log.e(TAG, "查询异常: $packageName", e)
            } finally {
                querying = false
                try { activeWindowNode?.recycle() } catch (_: Exception) {}
                
                checkFutureStartJob(packageName)
                
                val nextPkg = pendingQueryPkg
                if (nextPkg != null) {
                    pendingQueryPkg = null
                    startQueryJob(null, nextPkg)
                }
            }
        }
    }

    private suspend fun queryAndAct(
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
        val allQueryNodes = collectQueryNodes(activeWindowNode, packageName)

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
                    val result = selector.match(node, androidNodeTransform, option)
                    return result
                }
                
                // 核心修复：很多广告 SDK 会故意破坏 DOM 树 (childCount 异常)，导致深度遍历找不到节点。
                // 我们通过正则提取 selector 中的 text，使用系统底层的 findAccessibilityNodeInfosByText 穿透查找。
                val source = selector.toString()
                
                val idMatch = Regex("""(?:id|vid)[*\^$]?=['"]([^'"]+)['"]""").find(source)
                if (idMatch != null) {
                    val idToFind = idMatch.groupValues[1]
                    val nativeNodes = try { node.findAccessibilityNodeInfosByViewId(idToFind) } catch (e: Exception) { emptyList() }
                    for (nativeNode in nativeNodes) {
                        var current: AccessibilityNodeInfo? = nativeNode
                        var depthLimit = 10
                        while (current != null && depthLimit-- > 0) {
                            if (selector.match(current, androidNodeTransform, option) != null) {
                                return current
                            }
                            current = androidNodeTransform.getParent(current)
                        }
                    }
                }
                
                val textMatch = Regex("""(?:text|desc)[*\^$]?=['"]([^'"]+)['"]""").find(source)
                if (textMatch != null) {
                    val textToFind = textMatch.groupValues[1]
                    val nativeNodes = try { node.findAccessibilityNodeInfosByText(textToFind) } catch (e: Exception) { emptyList() }
                    for (nativeNode in nativeNodes) {
                        var current: AccessibilityNodeInfo? = nativeNode
                        var depthLimit = 10
                        while (current != null && depthLimit-- > 0) {
                            if (selector.match(current, androidNodeTransform, option) != null) {
                                return current
                            }
                            current = androidNodeTransform.getParent(current)
                        }
                    }
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

        val evaluatedGroups = mutableListOf<String>()
        val skippedActivity = mutableListOf<String>()
        val skippedCdMax = mutableListOf<String>()

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
                if (RuleExecutionTracker.isGroupCooldown(groupCdKey, group.actionCd, now)) {
                    skippedCdMax.add(group.name)
                    continue
                }
                if (RuleExecutionTracker.isGroupMaxReached(groupCdKey, group.actionMaximum)) {
                    skippedCdMax.add(group.name)
                    continue
                }

                if (group.activityIds.isNotEmpty()) {
                    val groupActivityMatched = group.activityIds.any {
                        val target = if (it.startsWith(".")) packageName + it else it
                        currentActivityId == target || currentActivityId.startsWith(target)
                    }
                    if (!groupActivityMatched) {
                        skippedActivity.add(group.name)
                        continue
                    }
                }
                if (group.excludeActivityIds.isNotEmpty()) {
                    val groupActivityExcluded = group.excludeActivityIds.any {
                        val target = if (it.startsWith(".")) packageName + it else it
                        currentActivityId == target || currentActivityId.startsWith(target)
                    }
                    if (groupActivityExcluded) {
                        skippedActivity.add(group.name)
                        continue
                    }
                }

                evaluatedGroups.add(group.name)

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
                        val lastRuleKey = RuleExecutionTracker.getLastTriggeredRuleKey(groupCdKey)
                        if (lastRuleKey == null || !rule.preKeys.contains(lastRuleKey)) {
                            continue
                        }
                    }

                    val ruleKey = "$groupCdKey-${rule.key ?: group.rules.indexOf(rule)}"
                    val ruleCd = rule.actionCd ?: group.actionCd
                    if (RuleExecutionTracker.isRuleCooldown(ruleKey, ruleCd, now)) {
                        continue
                    }
                    if (RuleExecutionTracker.isRuleMaxReached(ruleKey, rule.actionMaximum)) {
                        continue
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
                ActionExecutor.performAction(this@SelectToSpeakService, safeTarget, targetRule.action)
                ActionLog.log(packageName, "ACTION", "执行点击完毕")

                val appId = appRules.firstOrNull { it.groups.contains(targetGroup) }?.id ?: packageName
                val groupCdKey = "$packageName-$appId-${targetGroup.key}"
                val ruleKey = "$groupCdKey-${targetRule.key ?: targetGroup.rules.indexOf(targetRule)}"
                val triggerTime = System.currentTimeMillis()
                RuleExecutionTracker.recordGroupTrigger(groupCdKey, triggerTime)
                RuleExecutionTracker.recordRuleTrigger(ruleKey, targetRule.key ?: targetGroup.rules.indexOf(targetRule), groupCdKey, triggerTime)

                onActionTriggered()
            } finally {
                isExecutingClick = false
                try { safeTarget.recycle() } catch (_: Exception) {}
            }
        }
        if (matchedTarget == null) {
            val activityInfo = appActivityIds[packageName].orEmpty().ifEmpty { "未知Activity" }
            val evalCount = evaluatedGroups.size
            val skipMaxCdCount = skippedCdMax.size
            val skipActCount = skippedActivity.size
            ActionLog.log(packageName, "EVENT", "匹配结束: 未发现目标 [已测:$evalCount] [CD/Max跳过:$skipMaxCdCount] [Activity跳过:$skipActCount] (Act=$activityInfo)", success = false)
        }
    }

    private fun collectQueryNodes(
        activeWindowNode: AccessibilityNodeInfo?,
        packageName: String
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        activeWindowNode?.let { nodes.add(it) }
        val windowRoots = try {
            windows?.mapNotNull { window ->
                try { window.root } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            null
        }
        windowRoots?.forEach { root ->
            // 不过滤 packageName，因为广告 SDK 弹出的悬浮窗/Dialog 的包名可能不是宿主 App 的包名
            if (nodes.none { it == root }) {
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
            return
        }

        if (packageWhitelist.contains(targetPackage) || !hasRulesForPackage(targetPackage)) {
            try { rootNode?.recycle() } catch (_: Exception) {}
            return
        }
        
        try { rootNode?.recycle() } catch (_: Exception) {}
        startQueryJob(null, targetPackage)
    }

    private fun checkFutureStartJob(expectedPackageName: String? = null) {
        val packageName = expectedPackageName ?: currentAppId
        if (packageName.isEmpty() || packageWhitelist.contains(packageName) || !hasRulesForPackage(packageName)) {
            return
        }

        val t = System.currentTimeMillis()
        if (t - appChangeTime < 3000L || t - lastTriggerTime < 3000L) {
            serviceScope.launch(queryDispatcher) {
                delay(300)
                retryQuery(packageName)
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
