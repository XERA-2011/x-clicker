package dev.xera.xclicker.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlinx.coroutines.flow.MutableStateFlow

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0
) {
    val shortActivityId: String?
        get() {
            val a = if (activityId != null && activityId.startsWith(appId)) {
                activityId.substring(appId.length)
            } else {
                activityId
            }
            return a
        }

    fun format(): String {
        return "${appId}/${shortActivityId}/${number}"
    }

    fun sameAs(a: String, b: String?): Boolean {
        return appId == a && activityId == b
    }
}

val topActivityFlow = MutableStateFlow(TopActivity())
private var lastValidActivity: TopActivity = topActivityFlow.value
    set(value) {
        if (value.activityId != null) {
            field = value
        }
    }

private object ActivityCache : LruCache<Pair<String, String>, Boolean>(256) {
    override fun create(key: Pair<String, String>): Boolean = try {
        val service = SelectToSpeakService.instance ?: throw Exception("Service not connected")
        service.packageManager.getActivityInfo(
            ComponentName(key.first, key.second),
            0
        )
        true
    } catch (_: Exception) {
        false
    }
}

fun isActivity(
    appId: String,
    activityId: String,
): Boolean {
    return topActivityFlow.value.sameAs(appId, activityId) || ActivityCache.get(appId to activityId)
}

class ActivityRule(
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val appRules = ruleSummary.appIdToRules[topActivity.appId] ?: emptyList()
    val activityRules = appRules.filter { rule ->
        rule.matchActivity(topActivity.appId, topActivity.activityId)
    }
    val globalRules = ruleSummary.globalRules.filter { r ->
        r.matchActivity(topActivity.appId, topActivity.activityId)
    }

    val currentRules = (activityRules + globalRules).sortedBy { it.order }
    val hasPriorityRule = false
    val activePriority = false
    val priorityRules: List<ResolvedRule>
        get() = currentRules
    val skipMatch: Boolean
        get() {
            return currentRules.all { r -> !r.status.ok }
        }
    val skipConsumeEvent: Boolean
        get() {
            return currentRules.all { r -> !r.status.alive }
        }
    val hasFeatureAction: Boolean
        get() = currentRules.any { r -> r.checkForced() && (r.status == RuleStatus.StatusOk || r.status == RuleStatus.Status5) }
}

val activityRuleFlow = MutableStateFlow(ActivityRule())

enum class ActivityScene {
    ScreenOn,
    A11y,
    TaskStack
}

fun updateTopActivity(
    appId: String,
    activityId: String?,
    scene: ActivityScene = ActivityScene.A11y,
    loc: String = "",
) {
    val t = System.currentTimeMillis()
    val oldActivity = topActivityFlow.value
    val oldActivityRule = activityRuleFlow.value
    val idChanged = (scene == ActivityScene.ScreenOn || appId != oldActivityRule.topActivity.appId)
    val isSame = scene != ActivityScene.ScreenOn && oldActivity.sameAs(appId, activityId)

    val number = if (isSame) {
        oldActivity.number + 1
    } else {
        0
    }
    topActivityFlow.value = TopActivity(
        appId = appId,
        activityId = activityId ?: lastValidActivity.takeIf { it.appId == appId }?.activityId,
        number = number,
    )
    lastValidActivity = oldActivity

    val topActivity = topActivityFlow.value
    val ruleSummary = ruleSummaryFlow.value
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== ruleSummary
    if (topChanged || ruleChanged) {
        val newActivityRule = ActivityRule(
            ruleSummary = ruleSummary,
            topActivity = topActivity,
        )
        if (idChanged) {
            appChangeTime = t
            ruleSummary.globalRules.forEach { it.resetState(t) }
            ruleSummary.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { it.resetState(t) }
            newActivityRule.appRules.forEach { it.resetState(t) }
        } else {
            newActivityRule.currentRules.forEach { r ->
                when (r.resetMatchType) {
                    ResetMatchType.App -> {
                        if (r.isFirstMatchApp) {
                            r.resetState(t)
                        }
                    }
                    ResetMatchType.Activity -> r.resetState(t)
                    ResetMatchType.Match -> {
                        if (!oldActivityRule.currentRules.contains(r)) {
                            r.resetState(t)
                        }
                    }
                }
            }
        }
        activityRuleFlow.value = newActivityRule
        Log.d(
            "updateTopActivity",
            "${oldActivity.format()} -> ${topActivityFlow.value.format()} (scene=$scene) loc=$loc"
        )
    }
}

@Volatile
var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var imeAppId = ""
var launcherAppId = ""
val systemAppsFlow = MutableStateFlow<Set<String>>(emptySet())

fun updateSystemDefaultAppId(context: Context) {
    try {
        imeAppId = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)?.packageName ?: ""
        val launcherCn = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .resolveActivity(context.packageManager)
        launcherAppId = launcherCn?.packageName ?: ""

        val pm = context.packageManager
        val pkgs = pm.getInstalledPackages(0)
        val systemApps = pkgs.filter {
            val appInfo = it.applicationInfo
            appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }.map { it.packageName }.toSet()
        systemAppsFlow.value = systemApps
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

val ruleSummaryFlow = MutableStateFlow(RuleSummary())
