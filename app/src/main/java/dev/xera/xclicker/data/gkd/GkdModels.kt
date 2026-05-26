package dev.xera.xclicker.data.gkd

data class Subscription(
    val id: Int,
    val name: String,
    val apps: List<AppRule>
)

data class AppRule(
    val id: String, // e.g. com.baidu.tieba
    val name: String? = null,
    val groups: List<RuleGroup> = emptyList()
)

data class RuleGroup(
    val key: Int,
    val name: String,
    val desc: String? = null,
    val actionCd: Long = 1000L, // Cooldown in ms
    val actionDelay: Long = 0L,
    val fastQuery: Boolean = false,
    val matchRoot: Boolean = false,
    val actionMaximum: Int? = null,
    val matchAnyApp: Boolean = false,
    val targetAppIds: List<String> = emptyList(),
    val excludedAppIds: List<String> = emptyList(),
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    val rules: List<Rule> = emptyList()
)

data class Rule(
    val key: Int? = null,
    val matches: List<String> = emptyList(),
    val anyMatches: List<String> = emptyList(),
    val excludeMatches: List<String> = emptyList(),
    val excludeAllMatches: List<String> = emptyList(),
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    val preKeys: List<Int> = emptyList(),
    val actionCd: Long? = null,
    val actionDelay: Long? = null,
    val actionMaximum: Int? = null,
    val matchRoot: Boolean = false,
    val fastQuery: Boolean = false,
    val matchDelay: Long? = null,
    val matchTime: Long? = null,
    val forcedTime: Long? = null,
    val resetMatch: String? = null,
    val action: String? = null,
    val order: Int? = null
)

