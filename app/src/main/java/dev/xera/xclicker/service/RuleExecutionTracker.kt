package dev.xera.xclicker.service

import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.Subscription
import java.util.concurrent.ConcurrentHashMap

object RuleExecutionTracker {
    val triggerTimes = ConcurrentHashMap<String, Long>()
    val triggerCounts = ConcurrentHashMap<String, Int>()
    val lastTriggeredRuleKeys = ConcurrentHashMap<String, Int>()

    fun isGroupCooldown(groupKey: String, actionCd: Long, now: Long): Boolean {
        val lastTrigger = triggerTimes[groupKey] ?: return false
        return now - lastTrigger < actionCd
    }

    fun isGroupMaxReached(groupKey: String, actionMaximum: Int?): Boolean {
        if (actionMaximum == null) return false
        val count = triggerCounts[groupKey] ?: 0
        return count >= actionMaximum
    }

    fun isRuleCooldown(ruleKey: String, ruleCd: Long, now: Long): Boolean {
        val lastTrigger = triggerTimes[ruleKey] ?: return false
        return now - lastTrigger < ruleCd
    }

    fun isRuleMaxReached(ruleKey: String, ruleMaximum: Int?): Boolean {
        if (ruleMaximum == null) return false
        val count = triggerCounts[ruleKey] ?: 0
        return count >= ruleMaximum
    }

    fun recordGroupTrigger(groupKey: String, now: Long) {
        triggerTimes[groupKey] = now
        triggerCounts[groupKey] = (triggerCounts[groupKey] ?: 0) + 1
    }

    fun recordRuleTrigger(ruleKey: String, ruleActionKey: Int?, groupKey: String, now: Long) {
        triggerTimes[ruleKey] = now
        triggerCounts[ruleKey] = (triggerCounts[ruleKey] ?: 0) + 1
        ruleActionKey?.let { lastTriggeredRuleKeys[groupKey] = it }
    }

    fun getLastTriggeredRuleKey(groupKey: String): Int? {
        return lastTriggeredRuleKeys[groupKey]
    }

    fun resetPackage(packageName: String) {
        val prefix = "$packageName-"
        triggerTimes.keys.removeAll { it.startsWith(prefix) }
        triggerCounts.keys.removeAll { it.startsWith(prefix) }
        lastTriggeredRuleKeys.keys.removeAll { it.startsWith(prefix) }
    }

    fun resetActivity(packageName: String, subscription: Subscription?) {
        val sub = subscription ?: return
        val appsToReset = sub.apps.filter { it.id == packageName || it.id == "gkd.global" || it.id == "global" }
        for (appRule in appsToReset) {
            for (group in appRule.groups) {
                val groupKey = "$packageName-${appRule.id}-${group.key}"
                var groupNeedsReset = false
                for (rule in group.rules) {
                    val rm = rule.resetMatch ?: "activity"
                    if (rm == "activity") {
                        val ruleKey = "$groupKey-${rule.key ?: group.rules.indexOf(rule)}"
                        triggerTimes.remove(ruleKey)
                        triggerCounts.remove(ruleKey)
                        groupNeedsReset = true
                    }
                }
                if (groupNeedsReset) {
                    triggerTimes.remove(groupKey)
                    triggerCounts.remove(groupKey)
                }
            }
        }
    }

    fun clear() {
        triggerTimes.clear()
        triggerCounts.clear()
        lastTriggeredRuleKeys.clear()
    }
}
