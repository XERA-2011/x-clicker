# Phase 1: Core Engine Features (clickCenter & Loop Prevention) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implement robust, coordinate-based clicking (`clickCenter`), route GKD actions, and enforce anti-loop limits (`actionCd` / `actionMaximum` / `resetMatch`) via a highly testable tracker module in `XClickerService`.

**Architecture:** 
1. Create a decoupled helper module `RuleExecutionTracker` that monitors cooldowns and trigger counts, allowing clean unit-level TDD testing.
2. Update `ActionExecutor` to support full action routing (e.g. `clickCenter`, `clickNode`, `back`, `none`).
3. Refactor `XClickerService` to delegate all state tracking and action routing to these optimized modules.

**Tech Stack:** Kotlin, Android Accessibility API, JUnit.

---

### Task 1: Create and Test RuleExecutionTracker (TDD)

**Files:**
- Create: `app/src/main/java/dev/xera/xclicker/service/RuleExecutionTracker.kt`
- Create: `app/src/test/java/dev/xera/xclicker/TestRuleExecutionTracker.kt`

- [x] **Step 1: Write a failing unit test in TestRuleExecutionTracker.kt**
  Verify the cooldown checks, maximum execution count limits, and resetting functionality in isolation.
  ```kotlin
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
  ```

- [x] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestRuleExecutionTracker"`
  Expected: Compile / execution error due to missing `RuleExecutionTracker`.

- [x] **Step 3: Implement minimal RuleExecutionTracker.kt**
  Create the tracker containing logic to monitor and reset execution constraints.
  ```kotlin
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
          val appRule = sub.apps.find { it.id == packageName } ?: return
          for (group in appRule.groups) {
              val groupKey = "$packageName-${appRule.id}-${group.key}"
              for (rule in group.rules) {
                  if (rule.resetMatch == "activity") {
                      val ruleKey = "$groupKey-${rule.key ?: group.rules.indexOf(rule)}"
                      triggerTimes.remove(ruleKey)
                      triggerCounts.remove(ruleKey)
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
  ```

- [x] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestRuleExecutionTracker"`
  Expected: PASS.

- [x] **Step 5: Commit**
  Run: `git add app/src/main/java/dev/xera/xclicker/service/RuleExecutionTracker.kt app/src/test/java/dev/xera/xclicker/TestRuleExecutionTracker.kt`
  Run: `git commit -m "feat: implement RuleExecutionTracker with unit tests"`

---

### Task 2: Update ActionExecutor for Action Routing

**Files:**
- Modify: `app/src/main/java/dev/xera/xclicker/service/ActionExecutor.kt`

- [x] **Step 1: Implement Action Routing in ActionExecutor.kt**
  Add the `performAction` routing function supporting GKD actions (`click`, `clickCenter`, `clickNode`, `back`, `none`).
  ```kotlin
      /**
       * Execute GKD-specified actions.
       */
      fun performAction(service: AccessibilityService, node: AccessibilityNodeInfo, action: String?) {
          when (action) {
              "clickCenter" -> {
                  dispatchClickAtNodeCenter(service, node)
              }
              "clickNode" -> {
                  val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                  Log.i(TAG, "ACTION_CLICK (clickNode) result: $result")
              }
              "none" -> {
                  Log.i(TAG, "No-op action (none) triggered")
              }
              "back" -> {
                  val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                  Log.i(TAG, "GLOBAL_ACTION_BACK result: $result")
              }
              else -> {
                  // Default behavior: "click" (node action with center fallback)
                  performClick(service, node)
              }
          }
      }
  ```

- [x] **Step 2: Verify Compilation & Build**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**
  Run: `git add app/src/main/java/dev/xera/xclicker/service/ActionExecutor.kt`
  Run: `git commit -m "feat: add GKD action routing support to ActionExecutor"`

---

### Task 3: Refactor XClickerService to Delegate Core State & Routing

**Files:**
- Modify: `app/src/main/java/dev/xera/xclicker/service/XClickerService.kt`

- [x] **Step 1: Clean up internal maps and delegate triggers to RuleExecutionTracker**
  Modify `XClickerService.kt` to:
  1. Remove old maps: `groupTriggerTimes`, `ruleTriggerTimes`, `ruleTriggerCounts`, `lastTriggeredRuleKeys`.
  2. In `resetPackageRuleState(packageName: String)`, delegate:
     ```kotlin
     RuleExecutionTracker.resetPackage(packageName)
     ```
  3. In `resetActivityRuleState(packageName: String)`, delegate:
     ```kotlin
     RuleExecutionTracker.resetActivity(packageName, subscription)
     ```
  4. In `resetAllRuleState()`, delegate:
     ```kotlin
     RuleExecutionTracker.clear()
     ```
  5. In `queryAndAct()`, replace the cooldown and limit-checks with delegate calls to `RuleExecutionTracker`:
     - Group cd check:
       ```kotlin
       if (RuleExecutionTracker.isGroupCooldown(groupCdKey, group.actionCd, now)) {
           continue
       }
       ```
     - Group max check:
       ```kotlin
       if (RuleExecutionTracker.isGroupMaxReached(groupCdKey, group.actionMaximum)) {
           continue
       }
       ```
     - PreKeys check:
       ```kotlin
       if (rule.preKeys.isNotEmpty()) {
           val lastRuleKey = RuleExecutionTracker.getLastTriggeredRuleKey(groupCdKey)
           if (lastRuleKey == null || !rule.preKeys.contains(lastRuleKey)) {
               continue
           }
       }
       ```
     - Rule cd check:
       ```kotlin
       if (RuleExecutionTracker.isRuleCooldown(ruleKey, ruleCd, now)) {
           continue
       }
       ```
     - Rule max check:
       ```kotlin
       if (RuleExecutionTracker.isRuleMaxReached(ruleKey, ruleMaximum)) {
           continue
       }
       ```
     - Update action dispatch:
       ```kotlin
       ActionExecutor.performAction(this@XClickerService, safeTarget, targetRule.action)
       ```
     - Update records post-click:
       ```kotlin
       RuleExecutionTracker.recordGroupTrigger(groupCdKey, triggerTime)
       RuleExecutionTracker.recordRuleTrigger(ruleKey, targetRule.key ?: targetGroup.rules.indexOf(targetRule), groupCdKey, triggerTime)
       ```

- [x] **Step 2: Run all tests to verify everything passes**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew test`
  Expected: BUILD SUCCESSFUL, all unit tests green.

- [x] **Step 3: Commit final changes**
  Run: `git add app/src/main/java/dev/xera/xclicker/service/XClickerService.kt`
  Run: `git commit -m "refactor: delegate state tracking and action execution in XClickerService"`
