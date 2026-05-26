@file:OptIn(kotlin.contracts.ExperimentalContracts::class)
package dev.xera.xclicker.engine

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.initDefaultTypeInfo
import kotlin.contracts.contract

const val STATE_CHANGED = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
const val CONTENT_CHANGED = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

private val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null
    } else {
        try {
            source?.setGeneratedTime()
        } catch (_: Exception) {
            null
        }
    }

fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId.toString()) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length
        )
    }
    return null
}

const val MAX_CHILD_SIZE = 512
const val MAX_DESCENDANTS_SIZE = 4096

private const val A11Y_NODE_TIME_KEY = "generatedTime"

fun AccessibilityNodeInfo.setGeneratedTime(): AccessibilityNodeInfo {
    extras.putLong(A11Y_NODE_TIME_KEY, System.currentTimeMillis())
    return this
}

fun AccessibilityNodeInfo.isExpired(expiryMillis: Long): Boolean {
    val generatedTime = extras.getLong(A11Y_NODE_TIME_KEY, -1)
    if (generatedTime == -1L) {
        return true
    }
    return (System.currentTimeMillis() - generatedTime) > expiryMillis
}

val typeInfo by lazy { initDefaultTypeInfo().globalType }

private const val interestedEvents = STATE_CHANGED or CONTENT_CHANGED

fun AccessibilityEvent?.isUseful(): Boolean {
    contract {
        returns(true) implies (this@isUseful != null)
    }
    return (this != null && packageName != null && className != null && (eventType and interestedEvents != 0))
}

data class A11yEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val name: String,
    val event: AccessibilityEvent,
) {
    val safeSource: AccessibilityNodeInfo?
        get() = event.safeSource

    fun sameAs(other: A11yEvent): Boolean {
        if (other === this) return true
        return type == other.type && appId == other.appId && name == other.name
    }
}

fun AccessibilityEvent.toA11yEvent(): A11yEvent? {
    val appId = packageName ?: return null
    val b = className ?: return null
    return A11yEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = appId.toString(),
        name = b.toString(),
        event = this,
    )
}
