package dev.xera.xclicker.service.selector

import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import dev.xera.xclicker.engine.A11yRuleEngine
import dev.xera.xclicker.engine.MAX_CHILD_SIZE
import dev.xera.xclicker.engine.MAX_DESCENDANTS_SIZE
import dev.xera.xclicker.engine.ResolvedRule
import dev.xera.xclicker.engine.RuleStatus
import dev.xera.xclicker.engine.activityRuleFlow
import dev.xera.xclicker.engine.appChangeTime
import dev.xera.xclicker.engine.getVid
import dev.xera.xclicker.engine.isExpired
import dev.xera.xclicker.engine.setGeneratedTime
import dev.xera.xclicker.engine.topActivityFlow
import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.getBooleanInvoke
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke
import java.util.concurrent.atomic.AtomicReference

private operator fun <K, V> LruCache<K, V>.set(key: K, value: V): V {
    return put(key, value)
}

private fun List<Any>.getInt(i: Int = 0) = get(i) as Int

private const val MAX_CACHE_SIZE = MAX_DESCENDANTS_SIZE

private val AccessibilityNodeInfo?.notExpiredNode: AccessibilityNodeInfo?
    get() {
        if (this != null) {
            val expiryMillis = if (text == null) 2000L else 1000L
            if (isExpired(expiryMillis)) {
                return null
            }
        }
        return this
    }

class A11yContext(
    private val a11yEngine: A11yRuleEngine,
    private val interruptable: Boolean = true,
) {
    private var childCache =
        LruCache<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    private var indexCache = LruCache<AccessibilityNodeInfo, Int>(MAX_CACHE_SIZE)
    private var parentCache = LruCache<AccessibilityNodeInfo, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    val rootCache = AtomicReference<AccessibilityNodeInfo?>(null)

    private fun clearChildCache(node: AccessibilityNodeInfo) {
        repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
            childCache.remove(node to i)?.let {
                clearChildCache(it)
            }
        }
    }

    fun clearNodeCache(eventNode: AccessibilityNodeInfo? = null) {
        if (rootCache.get()?.packageName != topActivityFlow.value.appId) {
            rootCache.set(null)
        }
        if (eventNode != null) {
            clearChildCache(eventNode)
            parentCache[eventNode]?.let { p ->
                getPureIndex(eventNode)?.let { i ->
                    childCache[p to i] = eventNode
                }
            }
            if (rootCache.get() == eventNode) {
                rootCache.set(eventNode)
            } else {
                return
            }
        }
        try {
            childCache.evictAll()
            parentCache.evictAll()
            indexCache.evictAll()
        } catch (_: Exception) {
            childCache = LruCache(MAX_CACHE_SIZE)
            indexCache = LruCache(MAX_CACHE_SIZE)
            parentCache = LruCache(MAX_CACHE_SIZE)
        }
    }

    private var lastAppChangeTime = appChangeTime
    fun clearOldAppNodeCache(): Boolean {
        if (appChangeTime != lastAppChangeTime) {
            lastAppChangeTime = appChangeTime
            clearNodeCache()
            return true
        }
        return false
    }

    var currentRule: ResolvedRule? = null

    @Volatile
    var interruptKey = 0
    private var interruptInnerKey = 0

    private fun guardInterrupt() {
        if (!interruptable) return
        if (interruptInnerKey == interruptKey) return
        interruptInnerKey = interruptKey
        val rule = currentRule ?: return
        if (!activityRuleFlow.value.activePriority) return
        if (!activityRuleFlow.value.currentRules.any { it === rule }) return
        if (rule.isPriority()) return
        throw Exception("Interrupt rule match")
    }

    private fun getA11Root(): AccessibilityNodeInfo? {
        guardInterrupt()
        return a11yEngine.safeActiveWindow
    }

    private fun getA11Child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        guardInterrupt()
        return node.getChild(index)?.setGeneratedTime()
    }

    private fun getA11Parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        guardInterrupt()
        return node.parent?.setGeneratedTime()
    }

    private fun getA11ByText(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return (node.findAccessibilityNodeInfosByText(value) ?: emptyList()).apply {
            forEach { it.setGeneratedTime() }
        }
    }

    private fun getA11ById(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return (node.findAccessibilityNodeInfosByViewId(value) ?: emptyList()).apply {
            forEach { it.setGeneratedTime() }
        }
    }

    private fun getFastQueryNodes(
        node: AccessibilityNodeInfo,
        fastQuery: FastQuery
    ): List<AccessibilityNodeInfo> {
        return when (fastQuery) {
            is FastQuery.Id -> getA11ById(node, fastQuery.value)
            is FastQuery.Text -> getA11ByText(node, fastQuery.value)
            is FastQuery.Vid -> getA11ById(node, "${node.packageName}:id/${fastQuery.value}")
        }
    }

    private fun getCacheRoot(node: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
        if (rootCache.get().notExpiredNode == null) {
            rootCache.set(getA11Root())
        }
        if (node == rootCache.get()) return null
        return rootCache.get()
    }

    private fun getCacheParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (getCacheRoot() == node) {
            return null
        }
        parentCache[node].notExpiredNode?.let { return it }
        return getA11Parent(node).apply {
            if (this != null) {
                parentCache[node] = this
            } else {
                rootCache.set(node)
            }
        }
    }

    private fun getCacheChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) {
            return null
        }
        return childCache[node to index].notExpiredNode ?: getA11Child(node, index)?.also { child ->
            indexCache[child] = index
            parentCache[child] = node
            childCache[node to index] = child
        }
    }

    private fun getPureIndex(node: AccessibilityNodeInfo): Int? {
        return indexCache[node]
    }

    private fun getCacheIndex(node: AccessibilityNodeInfo): Int {
        indexCache[node]?.let { return it }
        getCacheChildren(getCacheParent(node)).forEachIndexed { index, child ->
            if (child == node) {
                indexCache[node] = index
                return index
            }
        }
        return 0
    }

    private fun getCacheDepth(node: AccessibilityNodeInfo): Int {
        var p: AccessibilityNodeInfo = node
        var depth = 0
        while (true) {
            val p2 = getCacheParent(p)
            if (p2 != null) {
                p = p2
                depth++
            } else {
                break
            }
        }
        return depth
    }

    private fun getCacheChildren(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> {
        if (node == null) return emptySequence()
        return sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { index ->
                val child = getCacheChild(node, index) ?: return@sequence
                yield(child)
            }
        }
    }

    private var tempVid: CharSequence? = null
    private var tempVidNode: AccessibilityNodeInfo? = null
    private fun getTempVid(n: AccessibilityNodeInfo): CharSequence? {
        if (n !== tempVidNode) {
            tempVid = n.getVid()
            tempVidNode = n
        }
        return tempVid
    }

    private fun getCacheAttr(node: AccessibilityNodeInfo, name: String): Any? = when (name) {
        "id" -> node.viewIdResourceName
        "vid" -> getTempVid(node)
        "name", "className" -> node.className
        "text" -> node.text
        "desc" -> node.contentDescription
        "clickable" -> node.isClickable
        "focusable" -> node.isFocusable
        "checkable" -> node.isCheckable
        "checked" -> node.isChecked
        "editable" -> node.isEditable
        "longClickable" -> node.isLongClickable
        "visibleToUser" -> node.isVisibleToUser
        "left" -> { val b = Rect(); node.getBoundsInScreen(b); b.left }
        "top" -> { val b = Rect(); node.getBoundsInScreen(b); b.top }
        "right" -> { val b = Rect(); node.getBoundsInScreen(b); b.right }
        "bottom" -> { val b = Rect(); node.getBoundsInScreen(b); b.bottom }
        "width" -> { val b = Rect(); node.getBoundsInScreen(b); b.width() }
        "height" -> { val b = Rect(); node.getBoundsInScreen(b); b.height() }
        "index" -> getCacheIndex(node)
        "depth" -> getCacheDepth(node)
        "childCount" -> node.childCount
        "parent" -> getCacheParent(node)
        else -> null
    }

    private val transform = Transform<AccessibilityNodeInfo>(
        getAttr = { target, name ->
            when (target) {
                is QueryContext<*> -> when (name) {
                    "prev" -> target.prev
                    "current" -> target.current
                    else -> getCacheAttr(target.current as AccessibilityNodeInfo, name)
                }
                is AccessibilityNodeInfo -> getCacheAttr(target, name)
                is CharSequence -> getCharSequenceAttr(target, name)
                else -> null
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is AccessibilityNodeInfo -> when (name) {
                    "getChild" -> {
                        getCacheChild(target, args.getInt())
                    }
                    else -> null
                }
                is QueryContext<*> -> when (name) {
                    "getPrev" -> {
                        target.getPrev(args.getInt())
                    }
                    "getChild" -> {
                        getCacheChild(target.current as AccessibilityNodeInfo, args.getInt())
                    }
                    else -> null
                }
                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                is Boolean -> getBooleanInvoke(target, name, args)
                else -> null
            }
        },
        getName = { node -> node.className },
        getChildren = ::getCacheChildren,
        getParent = ::getCacheParent,
        getRoot = ::getCacheRoot,
        getDescendants = { node ->
            sequence {
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    yield(top)
                    for (childNode in getCacheChildren(top)) {
                        tempNodes.add(childNode)
                    }
                    if (tempNodes.isNotEmpty()) {
                        for (i in tempNodes.size - 1 downTo 0) {
                            stack.add(tempNodes[i])
                        }
                        tempNodes.clear()
                    }
                } while (stack.isNotEmpty())
            }.take(MAX_DESCENDANTS_SIZE)
        },
        traverseChildren = { node, connectExpression ->
            sequence {
                repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { offset ->
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    if (connectExpression.checkOffset(offset)) {
                        val child = getCacheChild(node, offset) ?: return@sequence
                        yield(child)
                    }
                }
            }
        },
        traverseBeforeBrothers = { node, connectExpression ->
            sequence {
                val parentVal = getCacheParent(node) ?: return@sequence
                val index = getPureIndex(node)
                if (index != null) {
                    var i = index - 1
                    var offset = 0
                    while (0 <= i && i < parentVal.childCount) {
                        connectExpression.maxOffset?.let { maxOffset ->
                            if (offset > maxOffset) return@sequence
                        }
                        if (connectExpression.checkOffset(offset)) {
                            val child = getCacheChild(parentVal, i) ?: return@sequence
                            yield(child)
                        }
                        i--
                        offset++
                    }
                } else {
                    val list = getCacheChildren(parentVal).takeWhile { it != node }.toMutableList()
                    list.reverse()
                    yieldAll(list.filterIndexed { i, _ ->
                        connectExpression.checkOffset(
                            i
                        )
                    })
                }
            }
        },
        traverseAfterBrothers = { node, connectExpression ->
            val parentVal = getCacheParent(node)
            if (parentVal != null) {
                val index = getPureIndex(node)
                if (index != null) {
                    sequence {
                        var i = index + 1
                        var offset = 0
                        while (0 <= i && i < parentVal.childCount) {
                            connectExpression.maxOffset?.let { maxOffset ->
                                if (offset > maxOffset) return@sequence
                            }
                            if (connectExpression.checkOffset(offset)) {
                                val child = getCacheChild(parentVal, i) ?: return@sequence
                                yield(child)
                            }
                            i++
                            offset++
                        }
                    }
                } else {
                    getCacheChildren(parentVal).dropWhile { it != node }
                        .drop(1)
                        .let {
                            if (connectExpression.maxOffset != null) {
                                it.take(connectExpression.maxOffset!! + 1)
                            } else {
                                it
                            }
                        }
                        .filterIndexed { i, _ ->
                            connectExpression.checkOffset(
                                i
                            )
                        }
                }
            } else {
                emptySequence()
            }
        },
        traverseDescendants = { node, connectExpression ->
            sequence {
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                var offset = 0
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    if (connectExpression.checkOffset(offset)) {
                        yield(top)
                    }
                    offset++
                    if (offset > MAX_DESCENDANTS_SIZE) {
                        return@sequence
                    }
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    for (childNode in getCacheChildren(top)) {
                        tempNodes.add(childNode)
                    }
                    if (tempNodes.isNotEmpty()) {
                        for (i in tempNodes.size - 1 downTo 0) {
                            stack.add(tempNodes[i])
                        }
                        tempNodes.clear()
                    }
                } while (stack.isNotEmpty())
            }
        },
        traverseFastQueryDescendants = { node, list ->
            sequence {
                for (fastQuery in list) {
                    val nodes = getFastQueryNodes(node, fastQuery)
                    nodes.forEach { childNode ->
                        yield(childNode)
                    }
                }
            }
        }
    )

    fun querySelfOrSelector(
        node: AccessibilityNodeInfo,
        selector: Selector,
        option: MatchOption,
    ): AccessibilityNodeInfo? {
        if (selector.isMatchRoot) {
            return selector.match(
                getCacheRoot() ?: return null,
                transform,
                option
            )
        }
        selector.match(node, transform, option)?.let {
            return it
        }
        return transform.querySelector(node, selector, option)
    }

    fun queryRule(
        rule: ResolvedRule,
        node: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo? {
        currentRule = rule
        try {
            val queryNode = if (rule.matchRoot) {
                getCacheRoot()
            } else {
                node
            } ?: return null
            var resultNode: AccessibilityNodeInfo? = null
            if (rule.anyMatches.isNotEmpty()) {
                for (selector in rule.anyMatches) {
                    resultNode = querySelfOrSelector(
                        queryNode,
                        selector,
                        rule.matchOption,
                    )
                    if (resultNode != null) break
                }
                if (resultNode == null) return null
            }
            for (selector in rule.matches) {
                resultNode = querySelfOrSelector(
                    queryNode,
                    selector,
                    rule.matchOption,
                ) ?: return null
            }
            for (selector in rule.excludeMatches) {
                querySelfOrSelector(
                    queryNode,
                    selector,
                    rule.matchOption,
                )?.let { return null }
            }
            if (rule.excludeAllMatches.isNotEmpty()) {
                val allExclude = rule.excludeAllMatches.all {
                    querySelfOrSelector(
                        queryNode,
                        it,
                        rule.matchOption,
                    ) == null
                }
                if (!allExclude) {
                    return null
                }
            }
            return resultNode
        } finally {
            currentRule = null
        }
    }
}
