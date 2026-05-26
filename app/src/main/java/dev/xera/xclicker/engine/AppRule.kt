package dev.xera.xclicker.engine

import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.RuleGroup

class AppRule(
    rule: Rule,
    group: RuleGroup,
    val appId: String,
) : ResolvedRule(
    rule = rule,
    group = group,
) {
    private val activityIds = getFixActivityIds(appId, rule.activityIds.ifEmpty { group.activityIds })
    private val excludeActivityIds = getFixActivityIds(appId, rule.excludeActivityIds.ifEmpty { group.excludeActivityIds })

    override val type = "app"

    override fun matchActivity(appId: String, activityId: String?): Boolean {
        if (appId != this.appId) return false
        activityId ?: return true
        if (excludeActivityIds.any { activityId.startsWith(it) }) return false
        return activityIds.isEmpty() || activityIds.any { activityId.startsWith(it) }
    }
}
