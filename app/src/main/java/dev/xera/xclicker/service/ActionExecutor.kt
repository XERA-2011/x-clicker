package dev.xera.xclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 动作执行器 —— 对标 GKD 的 ActionPerformer 架构。
 *
 * 核心点击策略（Click = ClickNode + ClickCenter）：
 * 1. 如果目标节点 isClickable → 直接 performAction(ACTION_CLICK)
 * 2. 如果上一步返回 false 或节点不可点击 → dispatchGesture 在节点中心坐标物理点击
 * 3. 绝不往父节点链上爬（防止误点广告容器）
 */
object ActionExecutor {

    private const val TAG = "ActionExecutor"

    /**
     * 对标 GKD 的 Click = ClickNode + ClickCenter 策略。
     */
    fun performClick(service: AccessibilityService, node: AccessibilityNodeInfo) {
        // Step 1: ClickNode — 如果节点声明了 isClickable，尝试 ACTION_CLICK
        if (node.isClickable) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (result) {
                Log.i(TAG, "ACTION_CLICK 成功: ${node.text ?: node.contentDescription}")
                return
            }
            Log.d(TAG, "ACTION_CLICK 返回 false，回退到 ClickCenter")
        }

        // Step 2: ClickCenter — 在节点中心坐标发送物理手势点击
        dispatchClickAtNodeCenter(service, node)
    }

    /**
     * 对标 GKD 的 ClickCenter performer。
     * 获取节点在屏幕上的绝对坐标，用 dispatchGesture 发送物理点击。
     *
     * @see GkdAction.ClickCenter (gkd/app/src/main/kotlin/li/songe/gkd/data/GkdAction.kt:52-89)
     */
    private fun dispatchClickAtNodeCenter(service: AccessibilityService, node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.isEmpty) {
            Log.w(TAG, "节点 bounds 为空，无法执行 ClickCenter")
            return
        }

        val x = (bounds.left + bounds.right) / 2f
        val y = (bounds.top + bounds.bottom) / 2f

        Log.i(TAG, "ClickCenter 手势点击: x=$x, y=$y, text=${node.text ?: node.contentDescription}")

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, ViewConfiguration.getTapTimeout().toLong()
                )
            )
            .build()

        service.dispatchGesture(gesture, null, null)
    }

    /**
     * 基于坐标字符串的点击（格式：x1,y1,x2,y2）
     */
    private fun dispatchClickAtCoordinates(service: AccessibilityService, coordinates: String) {
        val parts = coordinates.split(",").map { it.trim().toFloat() }
        if (parts.size != 4) return

        val centerX = (parts[0] + parts[2]) / 2f
        val centerY = (parts[1] + parts[3]) / 2f

        val path = Path().apply { moveTo(centerX, centerY) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, ViewConfiguration.getTapTimeout().toLong()
                )
            )
            .build()

        service.dispatchGesture(gesture, null, null)
    }
}
