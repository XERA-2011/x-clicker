package dev.xera.xclicker.engine

import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.RuleGroup

class GlobalRule(
    rule: Rule,
    group: RuleGroup,
) : ResolvedRule(
    rule = rule,
    group = group,
) {
    private val matchAnyApp = group.matchAnyApp
    private val targetAppIds = group.targetAppIds
    private val excludedAppIds = group.excludedAppIds

    override val type = "global"

    override fun matchActivity(appId: String, activityId: String?): Boolean {
        if (excludedAppIds.contains(appId)) {
            return false
        }

        if (targetAppIds.isNotEmpty()) {
            return targetAppIds.contains(appId)
        } else {
            if (appId == launcherAppId) {
                return false
            }
            if (systemAppsFlow.value.contains(appId)) {
                return false
            }
            return matchAnyApp
        }
    }
}
