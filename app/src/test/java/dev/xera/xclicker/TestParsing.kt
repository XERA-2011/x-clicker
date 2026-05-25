package dev.xera.xclicker

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.xera.xclicker.data.gkd.*
import org.junit.Test
import java.io.File

class TestParsing {
    @Test
    fun testParse() {
        val jsonString = File("/Users/xera/.gemini/antigravity/brain/899ad40f-0683-46f9-862f-72e270b9cdac/scratch/gkd.json5").readText()
        val factory = JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build()
        val mapper = ObjectMapper(factory)
        val rootNode = mapper.readTree(jsonString)

        val appsArray = rootNode.get("apps")
        val apps = mutableListOf<AppRule>()
        for (appObj in appsArray) {
            val id = appObj.path("id").asText(null) ?: continue
            val name = appObj.path("name").asText(null)
            val groupsArray = appObj.get("groups")
            
            val groups = mutableListOf<RuleGroup>()
            if (groupsArray != null && groupsArray.isArray) {
                for (groupObj in groupsArray) {
                    val groupKey = groupObj.path("key").asInt(-1)
                    val groupName = groupObj.path("name").asText("未命名")
                    val desc = groupObj.path("desc").asText(null)
                    val actionCd = groupObj.path("actionCd").asLong(0L)
                    val groupFastQuery = groupObj.path("fastQuery").asBoolean(false)
                    
                    val groupActivityIds = mutableListOf<String>()
                    val gActs = groupObj.get("activityIds")
                    if (gActs != null) {
                        if (gActs.isTextual) groupActivityIds.add(gActs.asText())
                        else if (gActs.isArray) gActs.forEach { if (it.isTextual) groupActivityIds.add(it.asText()) }
                    }
                    
                    val rulesNode = groupObj.get("rules")
                    val rules = mutableListOf<Rule>()
                    
                    if (rulesNode != null && rulesNode.isArray) {
                        for (ruleObj in rulesNode) {
                            rules.add(parseRule(ruleObj))
                        }
                    } else if (rulesNode != null && rulesNode.isObject) {
                        rules.add(parseRule(rulesNode))
                    } else {
                        rules.add(parseRule(groupObj))
                    }
                    
                    groups.add(
                        RuleGroup(
                            key = groupKey,
                            name = groupName,
                            desc = desc,
                            actionCd = actionCd,
                            fastQuery = groupFastQuery,
                            activityIds = groupActivityIds,
                            rules = rules
                        )
                    )
                }
            }
            apps.add(AppRule(id = id, name = name, groups = groups))
        }

        println(apps.size)
        val bili = apps.find { it.id == "tv.danmaku.bili" }
        println(bili?.groups?.size)
        val weibo = apps.find { it.id == "com.sina.weibo" }
        println(weibo?.groups?.size)
    }

    private fun parseRule(node: JsonNode): Rule {
        val key = if (node.has("key")) node.path("key").asInt() else null
        val fastQuery = node.path("fastQuery").asBoolean(false)
        val activityIds = mutableListOf<String>()
        val acts = node.get("activityIds")
        if (acts != null) {
            if (acts.isTextual) activityIds.add(acts.asText())
            else if (acts.isArray) acts.forEach { if (it.isTextual) activityIds.add(it.asText()) }
        }

        val matches = extractMatches(node, "matches")
        val anyMatches = extractMatches(node, "anyMatches")

        return Rule(
            key = key,
            matches = matches + anyMatches,
            activityIds = activityIds,
            fastQuery = fastQuery
        )
    }

    private fun extractMatches(node: JsonNode, fieldName: String): List<String> {
        val results = mutableListOf<String>()
        val matches = node.get(fieldName)
        if (matches != null) {
            if (matches.isTextual) results.add(matches.asText())
            else if (matches.isArray) {
                for (match in matches) {
                    if (match.isTextual) results.add(match.asText())
                }
            }
        }
        return results
    }
}
