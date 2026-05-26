package dev.xera.xclicker.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import dev.xera.xclicker.data.gkd.Rule
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class ActionResult(
    val action: String,
    val result: Boolean,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    abstract suspend fun perform(
        node: AccessibilityNodeInfo,
        rule: Rule,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            rule: Rule,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            rule: Rule,
        ): ActionResult {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val x = (rect.left + rect.right) / 2f
            val y = (rect.top + rect.bottom) / 2f

            val service = SelectToSpeakService.instance ?: return ActionResult(action, false)
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, ViewConfiguration.getTapTimeout().toLong()
                )
            )

            val future = CompletableFuture<Boolean>()
            service.dispatchGesture(
                gestureDescription.build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        future.complete(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        future.complete(false)
                    }
                },
                null
            )

            val result = try {
                future.get(1, TimeUnit.SECONDS)
            } catch (e: Exception) {
                false
            }

            return ActionResult(
                action = action,
                result = result,
                position = x to y
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            rule: Rule,
        ): ActionResult {
            if (node.isClickable) {
                val res = ClickNode.perform(node, rule)
                if (res.result) return res
            }
            return ClickCenter.perform(node, rule)
        }
    }

    data object Back : ActionPerformer("back") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            rule: Rule,
        ): ActionResult {
            val service = SelectToSpeakService.instance ?: return ActionResult(action, false)
            val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return ActionResult(action, result)
        }
    }

    data object None : ActionPerformer("none") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            rule: Rule,
        ): ActionResult {
            return ActionResult(action, true)
        }
    }

    companion object {
        private val allSubObjects by lazy {
            arrayOf(
                ClickNode,
                ClickCenter,
                Click,
                Back,
                None,
            )
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
