package dev.xera.xclicker.engine

import android.view.accessibility.AccessibilityNodeInfo
import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.RuleGroup
import kotlinx.coroutines.Job
import li.songe.selector.MatchOption
import li.songe.selector.Selector
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

inline var AtomicLong.value: Long
    get() = get()
    set(v) = set(v)

inline var AtomicInteger.value: Int
    get() = get()
    set(v) = set(v)

inline var <T> AtomicReference<T>.value: T
    get() = get()
    set(v) = set(v)

inline fun <T> AtomicReference<T>.update(newValue: (T) -> T) {
    while (true) {
        val current = get()
        val update = newValue(current)
        if (compareAndSet(current, update)) return
    }
}

sealed class ResolvedRule(
    val rule: Rule,
    val group: RuleGroup,
) {
    val key = rule.key
    val index = group.rules.indexOfFirst { r -> r === rule }
    private val preKeys = (rule.preKeys ?: emptyList()).toSet()

    val matches = (rule.matches ?: emptyList()).map { s -> Selector.parse(s) }
    val anyMatches = (rule.anyMatches ?: emptyList()).map { s -> Selector.parse(s) }
    val excludeMatches = (rule.excludeMatches ?: emptyList()).map { s -> Selector.parse(s) }
    val excludeAllMatches = (rule.excludeAllMatches ?: emptyList()).map { s -> Selector.parse(s) }

    private val resetMatch = rule.resetMatch
    val matchDelay = rule.matchDelay ?: 0L
    val actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L
    private val matchTime = rule.matchTime
    private val forcedTime = rule.forcedTime ?: 0L
    val matchOption = MatchOption(
        fastQuery = rule.fastQuery || group.fastQuery
    )
    val matchRoot = rule.matchRoot || group.matchRoot
    val order = rule.order ?: 0

    private val actionCd = rule.actionCd ?: group.actionCd ?: 1000L
    private val actionMaximum = rule.actionMaximum ?: group.actionMaximum

    private val hasSlowSelector by lazy {
        (matches + excludeMatches + anyMatches + excludeAllMatches).any { s -> s.isSlow(matchOption) }
    }
    val priorityTime = 0L
    val priorityActionMaximum = 1
    val priorityEnabled: Boolean
        get() = false

    fun isPriority(): Boolean = false

    val isSlow by lazy { preKeys.isEmpty() && (matchTime == null || matchTime > 10_000L) && hasSlowSelector }

    private var preRules = emptySet<ResolvedRule>()
    val hasNext = group.rules.any { r -> r.preKeys.any { k -> k == rule.key } }

    private var actionDelayTriggerTime = AtomicLong(0L)
    val actionDelayJob = AtomicReference<Job?>(null)

    fun checkDelay(): Boolean {
        if (actionDelay > 0 && actionDelayTriggerTime.value == 0L) {
            actionDelayTriggerTime.value = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun checkForced(): Boolean {
        if (forcedTime <= 0) return false
        return System.currentTimeMillis() < matchChangedTime.value + matchDelay + forcedTime
    }

    private var actionTriggerTime = AtomicLong(0L)
    
    fun trigger() {
        val t = System.currentTimeMillis()
        actionTriggerTime.value = t
        actionDelayTriggerTime.value = 0L
        actionCount.incrementAndGet()
        lastTriggerTime = t
        lastTriggerRule = this
    }

    private var actionCount = AtomicInteger(0)

    val matchChangedTime = AtomicLong(0L)
    val isFirstMatchApp: Boolean
        get() = matchChangedTime.value < appChangeTime

    private val matchLimitTime = (matchTime ?: 0) + matchDelay

    val resetMatchType = ResetMatchType.allSubObject.find {
        it.value == resetMatch
    } ?: ResetMatchType.Activity

    fun resetState(t: Long) {
        actionCount.value = 0
        actionDelayTriggerTime.value = 0L
        actionTriggerTime.value = 0
        actionDelayJob.update { it?.cancel(); null }
        matchDelayJob.update { it?.cancel(); null }
        matchChangedTime.value = t
    }

    suspend fun performAction(node: AccessibilityNodeInfo): ActionResult {
        return ActionPerformer.getAction(rule.action).perform(node, rule)
    }

    val matchDelayJob = AtomicReference<Job?>(null)

    val status: RuleStatus
        get() {
            val maxAct = actionMaximum
            if (maxAct != null) {
                if (actionCount.value >= maxAct) {
                    return RuleStatus.Status1
                }
            }
            if (preRules.isNotEmpty() && !preRules.any { it === lastTriggerRule }) {
                return RuleStatus.Status2
            }
            val t = System.currentTimeMillis()
            val c = matchChangedTime.value
            if (matchDelay > 0 && t - c < matchDelay) {
                return RuleStatus.Status3
            }
            if (matchTime != null && t - c > matchLimitTime) {
                return RuleStatus.Status4
            }
            if (actionTriggerTime.value + actionCd > t) {
                return RuleStatus.Status5
            }
            val d = actionDelayTriggerTime.value
            if (d > 0) {
                if (d + actionDelay > t) {
                    return RuleStatus.Status6
                }
            }
            return RuleStatus.StatusOk
        }

    fun statusText(): String {
        return "v:${rule.key}, type:${type}, gKey=${group.key}, gName:${group.name}, index:${index}, key:${key}, status:${status.name}"
    }

    abstract val type: String

    abstract fun matchActivity(appId: String, activityId: String? = null): Boolean
}

fun getFixActivityIds(
    appId: String,
    activityIds: List<String>?,
): List<String> {
    if (activityIds.isNullOrEmpty()) return emptyList()
    return activityIds.map { activityId ->
        if (activityId.startsWith('.')) {
            appId + activityId
        } else {
            activityId
        }
    }
}
