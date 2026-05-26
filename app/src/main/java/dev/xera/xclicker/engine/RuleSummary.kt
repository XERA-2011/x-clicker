package dev.xera.xclicker.engine

import dev.xera.xclicker.data.gkd.Subscription

data class RuleSummary(
    val globalRules: List<ResolvedRule> = emptyList(),
    val appIdToRules: Map<String, List<ResolvedRule>> = emptyMap(),
)

fun Subscription.toRuleSummary(): RuleSummary {
    val globalList = mutableListOf<ResolvedRule>()
    val appIdMap = mutableMapOf<String, MutableList<ResolvedRule>>()

    for (appRule in apps) {
        if (appRule.id == "gkd.global" || appRule.id == "global") {
            for (group in appRule.groups) {
                for (rule in group.rules) {
                    globalList.add(GlobalRule(rule, group))
                }
            }
        } else {
            val list = appIdMap.getOrPut(appRule.id) { mutableListOf() }
            for (group in appRule.groups) {
                for (rule in group.rules) {
                    list.add(AppRule(rule, group, appRule.id))
                }
            }
        }
    }

    return RuleSummary(
        globalRules = globalList,
        appIdToRules = appIdMap
    )
}
