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
        val file = File("/Users/xera/GitHub/x-clicker/gkd_simplified_rules.json")
        if (!file.exists()) {
            println("Skipping testParse because file does not exist")
            return
        }
        val jsonString = file.readText()
        val factory = JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build()
        val mapper = ObjectMapper(factory)
        val rootNode = mapper.readTree(jsonString)
        println("Successfully parsed simplified rules JSON, root node type: " + rootNode.nodeType)
    }

    @Test
    fun testParseNewGkdFields() {
        // Create a Rule instance with the new fields to verify they exist and compile
        val rule = Rule(
            key = 10,
            matches = listOf("Button"),
            matchDelay = 500L,
            matchTime = 10000L,
            forcedTime = 5000L,
            resetMatch = "activity",
            action = "clickCenter",
            order = 5
        )
        
        org.junit.Assert.assertEquals(10, rule.key)
        org.junit.Assert.assertEquals(500L, rule.matchDelay)
        org.junit.Assert.assertEquals(10000L, rule.matchTime)
        org.junit.Assert.assertEquals(5000L, rule.forcedTime)
        org.junit.Assert.assertEquals("activity", rule.resetMatch)
        org.junit.Assert.assertEquals("clickCenter", rule.action)
        org.junit.Assert.assertEquals(5, rule.order)
    }
}

