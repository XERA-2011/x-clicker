package com.xera.xclicker.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.xera.xclicker.app
import com.xera.xclicker.appScope

import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.util.LogUtils
import com.xera.xclicker.util.UpdateTimeOption
import com.xera.xclicker.util.checkSubsUpdate

fun onA11yFeatEvent(event: AccessibilityEvent) = event.run {
    if (event.eventType == STATE_CHANGED) {

        if (event.packageName == launcherAppId) {

            watchAutoUpdateSubs()
        }
    }
}

private var lastUpdateSubsTime = 0L
private fun watchAutoUpdateSubs() {
    val i = storeFlow.value.updateSubsInterval
    if (i <= 0) return
    val t = System.currentTimeMillis()
    if (t - lastUpdateSubsTime > i.coerceAtLeast(UpdateTimeOption.Everyday.value)) {
        lastUpdateSubsTime = t
        checkSubsUpdate()
    }
}

private fun initRuleChangedLog() {
    appScope.launch(Dispatchers.Default) {
        activityRuleFlow.debounce(300).drop(1).collect {
            if (storeFlow.value.enableMatch && it.currentRules.isNotEmpty()) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
                }.toTypedArray())
            }
        }
    }
}

var isInteractive = true
    private set
private val screenStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?
    ) {
        val action = intent?.action ?: return
        LogUtils.d("screenStateReceiver->${action}")
        isInteractive = when (action) {
            Intent.ACTION_SCREEN_ON -> true
            Intent.ACTION_SCREEN_OFF -> false
            Intent.ACTION_USER_PRESENT -> true
            else -> isInteractive
        }
        if (isInteractive) {
            val t = System.currentTimeMillis()
            if (t - appChangeTime > 500) { // 37.872(a11y) -> 38.228(onReceive)
                A11yRuleEngine.onScreenForcedActive()
            }
        }
    }
}

private fun initScreenStateReceiver() {
    isInteractive = app.powerManager.isInteractive
    ContextCompat.registerReceiver(
        app,
        screenStateReceiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        },
        ContextCompat.RECEIVER_EXPORTED
    )
}

fun initA11yFeat() {
    initRuleChangedLog()
    initScreenStateReceiver()
}
