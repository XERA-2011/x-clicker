package dev.xera.xclicker

import dev.xera.xclicker.data.gkd.Rule
import dev.xera.xclicker.data.gkd.RuleGroup
import dev.xera.xclicker.data.gkd.Subscription
import dev.xera.xclicker.service.RuleExecutionTracker
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class TestRuleExecutionTracker {
    @Before
    fun setup() {
        RuleExecutionTracker.clear()
    }

    @Test
    fun testCooldownAndMaxLimits() {
        val rule = Rule(key = 10, actionCd = 1000L, actionMaximum = 2)
        val groupKey = "com.test.app-1"
        val ruleKey = "$groupKey-10"
        val now = 10000L

        // First check should pass
        Assert.assertFalse(RuleExecutionTracker.isRuleCooldown(ruleKey, 1000L, now))
        Assert.assertFalse(RuleExecutionTracker.isRuleMaxReached(ruleKey, 2))

        // Record execution
        RuleExecutionTracker.recordRuleTrigger(ruleKey, 10, groupKey, now)

        // Second check (immediate) should fail due to cooldown
        Assert.assertTrue(RuleExecutionTracker.isRuleCooldown(ruleKey, 1000L, now + 500L))

        // Check after CD passes should succeed
        Assert.assertFalse(RuleExecutionTracker.isRuleCooldown(ruleKey, 1000L, now + 1500L))

        // Record second trigger
        RuleExecutionTracker.recordRuleTrigger(ruleKey, 10, groupKey, now + 1500L)

        // Third check should fail due to maximum limit
        Assert.assertTrue(RuleExecutionTracker.isRuleMaxReached(ruleKey, 2))
    }
}
