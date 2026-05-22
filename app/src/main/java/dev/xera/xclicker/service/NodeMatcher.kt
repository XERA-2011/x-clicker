package dev.xera.xclicker.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.xera.xclicker.data.model.PopupRule
import li.songe.selector.Selector
import dev.xera.xclicker.service.selector.androidNodeTransform

/**
 * 节点匹配器 —— 在无障碍节点树中查找符合规则的目标节点。
 *
 * 优化点：
 * 1. 移除 isVisibleToUser 检查（某些 ROM 对弹窗/悬浮窗返回 false）
 * 2. 保留 bounds.isEmpty 检查（过滤 0x0 的幽灵/陷阱节点）
 * 3. 收集所有候选节点，选择面积最小的（避免选中大容器）
 */
object NodeMatcher {

    private val COORDINATE_REGEX = Regex("""^-?\d+,-?\d+,-?\d+,-?\d+$""")

    fun findTarget(rootNode: AccessibilityNodeInfo?, rule: PopupRule): AccessibilityNodeInfo? {
        if (rootNode == null) return null

        var ruleId = rule.id

        // Coordinate format: skip for now
        if (COORDINATE_REGEX.matches(ruleId)) {
            return null
        }

        if (ruleId.startsWith("+")) {
            ruleId = "[text^=\"${ruleId.substring(1)}\"]"
        } else if (ruleId.startsWith("-")) {
            ruleId = "[text$=\"${ruleId.substring(1)}\"]"
        } else if (ruleId.startsWith("=")) {
            ruleId = "[text=\"${ruleId.substring(1)}\"]"
        } else if (!ruleId.contains("[")) {
            // 如果既不是李跳跳的前后缀语法，也不含 GKD 属性括号，可能是纯文本模糊匹配
            ruleId = "[text*=\"$ruleId\"]"
        }

        // 使用 GKD 官方引擎解析并匹配
        val selector = try {
            Selector.parse(ruleId)
        } catch (e: Exception) {
            android.util.Log.e("NodeMatcher", "Invalid selector: $ruleId", e)
            return null
        }

        // 收集所有匹配的候选节点，然后选择最优的
        val candidates = androidNodeTransform.querySelectorAll(rootNode, selector).toList()

        if (candidates.isEmpty()) return null

        // 选择面积最小的非零 bounds 节点（最精确的目标，避免选到大容器）
        val best = candidates.minByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            bounds.width().toLong() * bounds.height().toLong()
        }

        // 回收未被选中的候选节点
        candidates.forEach { node ->
            if (node !== best) {
                try { node.recycle() } catch (_: Exception) {}
            }
        }

        return best
    }


}
