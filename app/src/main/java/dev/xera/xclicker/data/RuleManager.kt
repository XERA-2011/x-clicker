package dev.xera.xclicker.data

import android.content.Context
import android.util.Log
import dev.xera.xclicker.data.gkd.AppRule
import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.RuleGroup
import dev.xera.xclicker.data.gkd.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class RuleManager(private val context: Context) {

    companion object {
        private const val TAG = "RuleManager"
        private const val RULES_DIR = "rules"
        private const val GKD_FILE_NAME = "subscription.json"
    }

    private val rulesDir: File
        get() = File(context.filesDir, RULES_DIR).also { it.mkdirs() }

    private val gkdFile: File
        get() = File(rulesDir, GKD_FILE_NAME)

    private val _subscriptionFlow = MutableStateFlow<Subscription?>(null)
    val subscriptionFlow: StateFlow<Subscription?> = _subscriptionFlow.asStateFlow()

    init {
        _subscriptionFlow.value = loadSubscription()
    }

    private fun loadSubscription(): Subscription? {
        if (!gkdFile.exists()) return null
        return try {
            val jsonString = gkdFile.readText()
            parseGkdJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GKD subscription file", e)
            null
        }
    }

    fun importRules(jsonString: String): Result<Int> {
        return try {
            val subscription = parseGkdJson(jsonString)
            if (subscription != null && subscription.apps.isNotEmpty()) {
                gkdFile.writeText(jsonString)
                _subscriptionFlow.value = subscription
                Result.success(subscription.apps.size)
            } else {
                Result.failure(Exception("无法识别的规则格式：未找到合法的 GKD apps 数据"))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import rules", e)
            val msg = e.message ?: e.javaClass.simpleName
            val shortMsg = if (msg.length > 200) msg.take(200) + "..." else msg
            Result.failure(Exception(shortMsg, e))
        }
    }

    private fun parseGkdJson(jsonString: String): Subscription? {
        val factory = JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build()
        val mapper = ObjectMapper(factory)
        val rootNode = mapper.readTree(jsonString)

        if (!rootNode.isObject || !rootNode.has("apps")) return null

        val appsArray = rootNode.get("apps")
        if (appsArray == null || !appsArray.isArray) return null

        val apps = mutableListOf<AppRule>()
        val globalGroups = parseGroups(rootNode.get("globalGroups"), isGlobal = true)
        if (globalGroups.isNotEmpty()) {
            apps.add(AppRule(id = "gkd.global", name = "全局规则", groups = globalGroups))
        }
        for (appObj in appsArray) {
            val id = appObj.path("id").asText(null) ?: continue
            val name = appObj.path("name").asText(null)
            val groups = parseGroups(appObj.get("groups"), isGlobal = false)
            apps.add(AppRule(id = id, name = name, groups = groups))
        }

        val hasGlobal = globalGroups.isNotEmpty() || apps.any { it.id == "gkd.global" || it.id == "global" }
        if (!hasGlobal) {
            val globalRule = AppRule(
                id = "gkd.global",
                name = "全局规则",
                groups = listOf(
                    RuleGroup(
                        key = 0,
                        name = "开屏广告",
                        actionCd = 1000L,
                        fastQuery = true,
                        matchAnyApp = true,
                        activityIds = emptyList(),
                        rules = listOf(
                            Rule(
                                key = 0,
                                matches = listOf("[text*=\"跳过\"][text.length<=10]"),
                                anyMatches = emptyList(),
                                excludeMatches = emptyList(),
                                activityIds = emptyList(),
                                fastQuery = true
                            ),
                            Rule(
                                key = 1,
                                matches = listOf("[desc*=\"跳过\"][desc.length<=10]"),
                                anyMatches = emptyList(),
                                excludeMatches = emptyList(),
                                activityIds = emptyList(),
                                fastQuery = true
                            ),
                            Rule(
                                key = 2,
                                matches = listOf("[id$=\"tt_splash_skip_btn\"]"),
                                anyMatches = emptyList(),
                                excludeMatches = emptyList(),
                                activityIds = emptyList(),
                                fastQuery = true
                            )
                        )
                    )
                )
            )
            apps.add(globalRule)
        }

        val subId = rootNode.path("id").asInt(0)
        val subName = rootNode.path("name").asText("GKD Subscription")
        return Subscription(id = subId, name = subName, apps = apps)
    }

    private fun parseGroups(groupsNode: JsonNode?, isGlobal: Boolean): List<RuleGroup> {
        val groupItems = normalizeArray(groupsNode)
        val groups = mutableListOf<RuleGroup>()
        for ((index, groupNode) in groupItems.withIndex()) {
            if (groupNode.isObject && groupNode.path("enable").isBoolean && !groupNode.path("enable").asBoolean()) {
                continue
            }
            val groupObj = if (groupNode.isObject) groupNode else null
            val groupKey = groupObj?.path("key")?.asInt(index) ?: index
            val groupName = groupObj?.path("name")?.asText("未命名") ?: "未命名"
            val desc = groupObj?.path("desc")?.asText(null)
            val actionCd = readLong(groupNode, "actionCd") ?: readLong(groupNode, "cd") ?: 1000L
            val actionDelay = readLong(groupNode, "actionDelay") ?: readLong(groupNode, "delay") ?: 0L
            val groupFastQuery = readBoolean(groupNode, "fastQuery") || readBoolean(groupNode, "quickFind")
            val matchRoot = readBoolean(groupNode, "matchRoot")
            val actionMaximum = readInt(groupNode, "actionMaximum")
            val groupActivityIds = extractStringList(groupNode, "activityIds")
            val groupExcludeActivityIds = extractStringList(groupNode, "excludeActivityIds")
            val rulesNode = groupObj?.get("rules")
            val rules = when {
                rulesNode != null -> normalizeArray(rulesNode).map { parseRule(it) }
                else -> listOf(parseRule(groupNode))
            }.filter { rule ->
                rule.matches.isNotEmpty() ||
                    rule.anyMatches.isNotEmpty() ||
                    rule.excludeMatches.isNotEmpty() ||
                    rule.excludeAllMatches.isNotEmpty()
            }

            groups.add(
                RuleGroup(
                    key = groupKey,
                    name = groupName,
                    desc = desc,
                    actionCd = actionCd,
                    actionDelay = actionDelay,
                    fastQuery = groupFastQuery,
                    matchRoot = matchRoot,
                    actionMaximum = actionMaximum,
                    matchAnyApp = isGlobal && readBoolean(groupNode, "matchAnyApp", defaultValue = true),
                    targetAppIds = if (isGlobal) extractGlobalAppIds(groupNode, enabled = true) else emptyList(),
                    excludedAppIds = if (isGlobal) extractGlobalAppIds(groupNode, enabled = false) else emptyList(),
                    activityIds = groupActivityIds,
                    excludeActivityIds = groupExcludeActivityIds,
                    rules = rules
                )
            )
        }
        return groups
    }

    private fun parseRule(node: JsonNode): Rule {
        if (node.isTextual) {
            return Rule(matches = listOf(node.asText()))
        }
        if (node.isArray) {
            return Rule(matches = extractStringList(node))
        }

        val key = readInt(node, "key")
        val fastQuery = readBoolean(node, "fastQuery") || readBoolean(node, "quickFind")
        val activityIds = extractStringList(node, "activityIds")
        val excludeActivityIds = extractStringList(node, "excludeActivityIds")

        val matchDelay = readLong(node, "matchDelay")
        val matchTime = readLong(node, "matchTime")
        val forcedTime = readLong(node, "forcedTime")
        val resetMatch = if (node.has("resetMatch")) node.path("resetMatch").asText(null) else null
        val action = if (node.has("action")) node.path("action").asText(null) else null
        val order = readInt(node, "order")

        return Rule(
            key = key,
            matches = extractMatches(node, "matches"),
            anyMatches = extractMatches(node, "anyMatches"),
            excludeMatches = extractMatches(node, "excludeMatches"),
            excludeAllMatches = extractMatches(node, "excludeAllMatches"),
            activityIds = activityIds,
            excludeActivityIds = excludeActivityIds,
            preKeys = extractIntList(node, "preKeys"),
            actionCd = readLong(node, "actionCd") ?: readLong(node, "cd"),
            actionDelay = readLong(node, "actionDelay") ?: readLong(node, "delay"),
            actionMaximum = readInt(node, "actionMaximum"),
            matchRoot = readBoolean(node, "matchRoot"),
            fastQuery = fastQuery,
            matchDelay = matchDelay,
            matchTime = matchTime,
            forcedTime = forcedTime,
            resetMatch = resetMatch,
            action = action,
            order = order
        )

    }

    private fun extractMatches(node: JsonNode, fieldName: String): List<String> {
        return extractStringList(node.get(fieldName))
    }

    private fun normalizeArray(node: JsonNode?): List<JsonNode> {
        if (node == null || node.isMissingNode || node.isNull) return emptyList()
        return if (node.isArray) node.toList() else listOf(node)
    }

    private fun extractStringList(node: JsonNode, fieldName: String): List<String> {
        return extractStringList(node.get(fieldName))
    }

    private fun extractStringList(node: JsonNode?): List<String> {
        if (node == null || node.isMissingNode || node.isNull) return emptyList()
        if (node.isTextual) return listOf(node.asText())
        if (!node.isArray) return emptyList()
        return node.mapNotNull { if (it.isTextual) it.asText() else null }
    }

    private fun extractIntList(node: JsonNode, fieldName: String): List<Int> {
        val value = node.get(fieldName) ?: return emptyList()
        if (value.isInt || value.isLong) return listOf(value.asInt())
        if (!value.isArray) return emptyList()
        return value.mapNotNull { if (it.isInt || it.isLong) it.asInt() else null }
    }

    private fun extractGlobalAppIds(node: JsonNode, enabled: Boolean): List<String> {
        val apps = node.get("apps") ?: return emptyList()
        if (!apps.isArray) return emptyList()
        return apps.mapNotNull { app ->
            val appEnabled = app.path("enable").asBoolean(true)
            if (appEnabled == enabled) app.path("id").asText(null) else null
        }
    }

    private fun readBoolean(node: JsonNode, fieldName: String, defaultValue: Boolean = false): Boolean {
        return node.path(fieldName).asBoolean(defaultValue)
    }

    private fun readInt(node: JsonNode, fieldName: String): Int? {
        val value = node.get(fieldName) ?: return null
        return if (value.isInt || value.isLong) value.asInt() else null
    }

    private fun readLong(node: JsonNode, fieldName: String): Long? {
        val value = node.get(fieldName) ?: return null
        return if (value.isInt || value.isLong) value.asLong() else null
    }

    fun clearRules() {
        gkdFile.delete()
        _subscriptionFlow.value = null
    }
}
