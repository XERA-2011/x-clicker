package dev.xera.xclicker.data

import android.content.Context
import android.util.Log
import dev.xera.xclicker.data.model.AppRuleSet
import dev.xera.xclicker.data.model.PopupRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class RuleManager(private val context: Context) {

    companion object {
        private const val TAG = "RuleManager"
        private const val RULES_DIR = "rules"
    }

    private val rulesDir: File
        get() = File(context.filesDir, RULES_DIR).also { it.mkdirs() }

    private val _rulesFlow = MutableStateFlow<List<AppRuleSet>>(emptyList())
    val rulesFlow: StateFlow<List<AppRuleSet>> = _rulesFlow.asStateFlow()

    init {
        _rulesFlow.value = loadRules()
    }

    fun loadRules(): List<AppRuleSet> {
        val rules = mutableListOf<AppRuleSet>()
        val dir = rulesDir
        if (!dir.exists()) return rules

        dir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            try {
                val json = file.readText()
                val ruleSet = parseAppRuleSet(file.nameWithoutExtension, JSONObject(json))
                rules.add(ruleSet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load rule file: ${file.name}", e)
            }
        }
        return rules
    }

    fun importRules(jsonString: String): Result<Int> {
        return try {
            var importedCount = 0

            // 尝试解析为 GKD 官方格式 (JSONObject)
            try {
                val jsonObj = JSONObject(jsonString)
                if (jsonObj.has("apps")) {
                    val appsArray = jsonObj.getJSONArray("apps")
                    for (i in 0 until appsArray.length()) {
                        val appObj = appsArray.getJSONObject(i)
                        val packageHash = appObj.optString("id") ?: continue
                        val groupsArray = appObj.optJSONArray("groups") ?: continue
                        
                        val popupRules = mutableListOf<PopupRule>()
                        for (j in 0 until groupsArray.length()) {
                            val groupObj = groupsArray.getJSONObject(j)
                            val actionName = groupObj.optString("name", "点击")
                            val delay = groupObj.optLong("actionCd", 0L).coerceAtMost(2000L)
                            
                            val rules = groupObj.opt("rules")
                            if (rules is JSONArray) {
                                for (k in 0 until rules.length()) {
                                    val ruleObj = rules.getJSONObject(k)
                                    val matches = extractMatches(ruleObj)
                                    matches.forEach { match ->
                                        popupRules.add(PopupRule(id = match, action = actionName, delay = delay))
                                    }
                                }
                            } else if (rules is JSONObject) {
                                val matches = extractMatches(rules)
                                matches.forEach { match ->
                                    popupRules.add(PopupRule(id = match, action = actionName, delay = delay))
                                }
                            } else {
                                // GKD 格式中，Group 自身也可以直接定义 matches，此时它就是一个单规则
                                val matches = extractMatches(groupObj)
                                matches.forEach { match ->
                                    popupRules.add(PopupRule(id = match, action = actionName, delay = delay))
                                }
                            }
                        }
                        
                        if (popupRules.isNotEmpty()) {
                            val ruleSet = AppRuleSet(packageHash, popupRules, true, 1)
                            saveRuleSet(ruleSet)
                            importedCount++
                        }
                    }
                    _rulesFlow.value = loadRules()
                    return Result.success(importedCount)
                }
            } catch (e: Exception) {
                // Not a valid GKD JSON or standard JSON object, fallback to LeeJump
            }

            // 兜底：尝试解析为李跳跳格式 (JSONArray)
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val element = jsonArray.getJSONObject(i)
                val keys = element.keys()

                while (keys.hasNext()) {
                    val packageHash = keys.next()
                    val value = element.get(packageHash)

                    val ruleObject: JSONObject = when (value) {
                        is JSONObject -> value
                        is String -> JSONObject(value)
                        else -> continue
                    }

                    val ruleSet = parseAppRuleSet(packageHash, ruleObject)
                    saveRuleSet(ruleSet)
                    importedCount++
                }
            }

            _rulesFlow.value = loadRules()
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import rules", e)
            Result.failure(e)
        }
    }

    private fun extractMatches(ruleObj: JSONObject): List<String> {
        val results = mutableListOf<String>()
        val matches = ruleObj.opt("matches")
        if (matches is String) results.add(matches)
        else if (matches is JSONArray) {
            for (i in 0 until matches.length()) results.add(matches.getString(i))
        }
        
        val anyMatches = ruleObj.opt("anyMatches")
        if (anyMatches is String) results.add(anyMatches)
        else if (anyMatches is JSONArray) {
            for (i in 0 until anyMatches.length()) results.add(anyMatches.getString(i))
        }
        return results
    }

    fun exportRules(): String {
        val jsonArray = JSONArray()
        val rules = loadRules()

        for (ruleSet in rules) {
            val wrapper = JSONObject()
            val ruleObject = JSONObject().apply {
                put("ltt_service", ruleSet.lttService)
                put("click_way", ruleSet.clickWay)
                val popupArray = JSONArray()
                for (rule in ruleSet.popupRules) {
                    popupArray.put(JSONObject().apply {
                        put("id", rule.id)
                        put("action", rule.action)
                        put("delay", rule.delay)
                        put("times", rule.times)
                    })
                }
                put("popup_rules", popupArray)
            }
            wrapper.put(ruleSet.packageHash, ruleObject)
            jsonArray.put(wrapper)
        }

        return jsonArray.toString(2)
    }

    fun clearRules() {
        rulesDir.listFiles()?.forEach { it.delete() }
        _rulesFlow.value = emptyList()
    }

    private fun saveRuleSet(ruleSet: AppRuleSet) {
        val file = File(rulesDir, "${ruleSet.packageHash}.json")
        val json = JSONObject().apply {
            put("ltt_service", ruleSet.lttService)
            put("click_way", ruleSet.clickWay)
            val popupArray = JSONArray()
            for (rule in ruleSet.popupRules) {
                popupArray.put(JSONObject().apply {
                    put("id", rule.id)
                    put("action", rule.action)
                    put("delay", rule.delay)
                    put("times", rule.times)
                })
            }
            put("popup_rules", popupArray)
        }
        file.writeText(json.toString(2))
    }

    private fun parseAppRuleSet(packageHash: String, json: JSONObject): AppRuleSet {
        val lttService = json.optBoolean("ltt_service", true)
        val clickWay = json.optInt("click_way", 1)
        val popupRules = mutableListOf<PopupRule>()

        val popupArray = json.optJSONArray("popup_rules")
        if (popupArray != null) {
            for (i in 0 until popupArray.length()) {
                val ruleJson = popupArray.getJSONObject(i)
                popupRules.add(
                    PopupRule(
                        id = ruleJson.optString("id", UUID.randomUUID().toString()),
                        action = ruleJson.optString("action", ""),
                        delay = ruleJson.optLong("delay", 0L),
                        times = ruleJson.optInt("times", 1)
                    )
                )
            }
        }

        return AppRuleSet(
            packageHash = packageHash,
            popupRules = popupRules,
            lttService = lttService,
            clickWay = clickWay
        )
    }
}
