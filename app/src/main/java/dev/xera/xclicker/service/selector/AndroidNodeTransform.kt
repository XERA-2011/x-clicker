package dev.xera.xclicker.service.selector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.Transform
import li.songe.selector.QueryContext

fun List<Any>.getInt(index: Int = 0): Int {
    return (this.getOrNull(index) as? Number)?.toInt() ?: 0
}

val androidNodeTransform = Transform<AccessibilityNodeInfo>(
    getAttr = { target, name ->
        var node: AccessibilityNodeInfo? = null
        if (target is AccessibilityNodeInfo) {
            node = target
        } else if (target is QueryContext<*>) {
            if (name == "prev") return@Transform target.prev
            if (name == "current") return@Transform target.current
            node = target.current as? AccessibilityNodeInfo
        }

        if (node == null) {
            // string properties fallback
            if (target is CharSequence) {
                if (name == "length") return@Transform target.length
            }
            return@Transform null
        }

        when (name) {
            "id" -> node.viewIdResourceName
            "vid" -> node.viewIdResourceName?.substringAfterLast("/")
            "name", "className" -> node.className?.toString()
            "text" -> node.text?.toString()
            "desc" -> node.contentDescription?.toString()
            "clickable" -> node.isClickable
            "visibleToUser" -> node.isVisibleToUser
            "width" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.width()
            }
            "height" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.height()
            }
            "bottom" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.bottom
            }
            "top" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.top
            }
            "left" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.left
            }
            "right" -> {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.right
            }
            "childCount" -> node.childCount
            "checked" -> node.isChecked
            "enabled" -> node.isEnabled
            "selected" -> node.isSelected
            "focusable" -> node.isFocusable
            "focused" -> node.isFocused
            "longClickable" -> node.isLongClickable
            "scrollable" -> node.isScrollable
            "editable" -> node.isEditable
            "index" -> {
                val parent = node.parent
                if (parent != null) {
                    var index = -1
                    for (i in 0 until parent.childCount) {
                        if (parent.getChild(i) == node) {
                            index = i
                            break
                        }
                    }
                    if (index != -1) index else null
                } else null
            }
            else -> null
        }
    },
    getInvoke = { target, name, args ->
        if (target is AccessibilityNodeInfo) {
            when (name) {
                "getChild" -> {
                    val index = args.getInt(0)
                    if (index in 0 until target.childCount) target.getChild(index) else null
                }
                else -> null
            }
        } else if (target is QueryContext<*>) {
            when (name) {
                "getPrev" -> {
                    val index = args.getInt(0)
                    target.getPrev(index)
                }
                "getChild" -> {
                    val node = target.current as? AccessibilityNodeInfo
                    if (node != null) {
                        val index = args.getInt(0)
                        if (index in 0 until node.childCount) node.getChild(index) else null
                    } else null
                }
                else -> null
            }
        } else {
            null
        }
    },
    getName = { node -> node.className },
    getChildren = { node ->
        sequence {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) yield(child)
            }
        }
    },
    getParent = { node -> node.parent }
)

class NodeCache {
    val childCache = mutableMapOf<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo>()
    val parentCache = mutableMapOf<AccessibilityNodeInfo, AccessibilityNodeInfo>()
    val indexCache = mutableMapOf<AccessibilityNodeInfo, Int>()

    fun getChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) return null
        val key = Pair(node, index)
        return childCache[key] ?: node.getChild(index)?.also { child ->
            childCache[key] = child
            parentCache[child] = node
            indexCache[child] = index
        }
    }

    fun getParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return parentCache[node] ?: node.parent?.also { p ->
            parentCache[node] = p
        }
    }
    
    fun getChildren(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> {
        if (node == null) return emptySequence()
        return sequence {
            for (i in 0 until node.childCount) {
                val child = getChild(node, i)
                if (child != null) yield(child)
            }
        }
    }

    fun getIndex(node: AccessibilityNodeInfo): Int {
        indexCache[node]?.let { return it }
        val p = getParent(node) ?: return 0
        getChildren(p).forEachIndexed { i, child ->
            if (child == node) {
                indexCache[node] = i
                return i
            }
        }
        return 0
    }

    fun getDepth(node: AccessibilityNodeInfo): Int {
        var p: AccessibilityNodeInfo = node
        var depth = 0
        while (true) {
            val p2 = getParent(p)
            if (p2 != null) {
                p = p2
                depth++
            } else {
                break
            }
        }
        return depth
    }
}

fun createTransformWithFastQuery(nodeCache: NodeCache, getRoot: () -> AccessibilityNodeInfo?): Transform<AccessibilityNodeInfo> {
    return Transform(
        getAttr = androidNodeTransform.getAttr,
        getInvoke = androidNodeTransform.getInvoke,
        getName = androidNodeTransform.getName,
        getChildren = { node ->
            nodeCache.getChildren(node)
        },
        getParent = { node ->
            nodeCache.getParent(node)
        }
    )
}
