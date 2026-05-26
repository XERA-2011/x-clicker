# Phase 0: Core Engine Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix the 5 core engine bugs in `XClickerService` (Thread.sleep blocking, early node recycle, missing GKD rule fields, resetMatch not implemented, and action not parsed) using TDD.

**Architecture:** 
1. Extend `GkdModels.kt` to support all necessary GKD rule fields.
2. Update Jackson parser in `RuleManager.kt` to correctly parse these fields.
3. Refactor `XClickerService.kt` to retrieve `rootInActiveWindow` inside the background coroutine, perform package matching retries asynchronously, and implement activity/match/app reset levels.

**Tech Stack:** Kotlin, Jackson, Coroutines, JUnit, Android Accessibility Service.

---

### Task 1: Extend GkdModels.kt with GKD Rule Fields

**Files:**
- Modify: `app/src/main/java/dev/xera/xclicker/data/gkd/GkdModels.kt`
- Test: `app/src/test/java/dev/xera/xclicker/TestParsing.kt`

- [x] **Step 1: Write a failing test in TestParsing.kt**
  We will write a test checking the parsing of the new fields like `matchDelay`, `matchTime`, `forcedTime`, `resetMatch`, `action`, `position`, `order`.
  Since the models don't have these fields yet, this test will fail to compile.

- [x] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestParsing"`
  Expected: Compilation failure.

- [x] **Step 3: Write minimal implementation in GkdModels.kt**
  Update `Rule` and `RuleGroup` data classes in `GkdModels.kt` to include these fields:
  ```kotlin
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
      
      // New GKD fields
      val matchDelay: Long? = null,
      val matchTime: Long? = null,
      val forcedTime: Long? = null,
      val resetMatch: String? = null, // "activity", "match", "app"
      val action: String? = null,     // "click", "clickNode", "clickCenter", "longClick", "longClickNode", "longClickCenter", "back", "none", "swipe"
      val order: Int? = null
  )
  ```

- [x] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest`
  Expected: Compilation succeeds, tests pass.

- [x] **Step 5: Commit**
  Run: `git add app/src/main/java/dev/xera/xclicker/data/gkd/GkdModels.kt`
  Run: `git commit -m "feat: extend GkdModels with new GKD fields"`

---

### Task 2: Parse new fields in RuleManager.kt

**Files:**
- Modify: `app/src/main/java/dev/xera/xclicker/data/RuleManager.kt`
- Test: `app/src/test/java/dev/xera/xclicker/TestParsing.kt`

- [x] **Step 1: Write a failing test in TestParsing.kt**
  Write a test case asserting that a JSON string containing `matchDelay`, `matchTime`, `forcedTime`, `resetMatch`, `action`, `order` is parsed correctly.
  ```kotlin
  @Test
  fun testParseNewGkdFields() {
      val json = """
      {
        "id": 1,
        "name": "Test",
        "apps": [
          {
            "id": "com.test.app",
            "groups": [
              {
                "key": 1,
                "name": "Group 1",
                "rules": [
                  {
                    "key": 10,
                    "matches": ["Button"],
                    "matchDelay": 500,
                    "matchTime": 10000,
                    "forcedTime": 5000,
                    "resetMatch": "activity",
                    "action": "clickCenter",
                    "order": 5
                  }
                ]
              }
            ]
          }
        ]
      }
      """.trimIndent()
      
      val ruleManager = RuleManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())
      val result = ruleManager.importRules(json)
      assert(result.isSuccess)
      val sub = ruleManager.subscriptionFlow.value!!
      val rule = sub.apps[0].groups[0].rules[0]
      org.junit.Assert.assertEquals(500L, rule.matchDelay)
      org.junit.Assert.assertEquals(10000L, rule.matchTime)
      org.junit.Assert.assertEquals(5000L, rule.forcedTime)
      org.junit.Assert.assertEquals("activity", rule.resetMatch)
      org.junit.Assert.assertEquals("clickCenter", rule.action)
      org.junit.Assert.assertEquals(5, rule.order)
  }
  ```

- [x] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestParsing.testParseNewGkdFields"`
  Expected: Fails because the parsing is not implemented in `RuleManager.kt`.

- [x] **Step 3: Write minimal implementation in RuleManager.kt**
  Modify `parseRule` in `RuleManager.kt` to extract these fields:
  ```kotlin
          val matchDelay = readLong(node, "matchDelay")
          val matchTime = readLong(node, "matchTime")
          val forcedTime = readLong(node, "forcedTime")
          val resetMatch = node.path("resetMatch").asText(null)
          val action = node.path("action").asText(null)
          val order = readInt(node, "order")
  ```

- [x] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestParsing.testParseNewGkdFields"`
  Expected: PASS.

- [x] **Step 5: Commit**
  Run: `git add app/src/main/java/dev/xera/xclicker/data/RuleManager.kt app/src/test/java/dev/xera/xclicker/TestParsing.kt`
  Run: `git commit -m "feat: parse new GKD fields in RuleManager"`

---

### Task 3: Refactor XClickerService.kt (Bugs 1 & 2)

**Files:**
- Modify: `app/src/main/java/dev/xera/xclicker/service/XClickerService.kt`

- [x] **Step 1: Write a failing unit test or design verification**
  Since XClickerService is an Android Service and requires standard system context, we will refactor the node-access lifecycle to be asynchronous and completely off the main thread. We will verify compile and build.

- [x] **Step 2: Remove Thread.sleep from main thread**
  Remove `Thread.sleep` retry loop from `startQueryJob`. Immediately launch coroutine on `queryDispatcher` inside `startQueryJob` and offload all `rootInActiveWindow` retrieval to the background thread.

- [x] **Step 3: Fix queryAndAct node recycling**
  Update `queryAndAct` signature and implementation to handle `activeWindowNode` properly and recycle it safely inside the coroutine context.

- [x] **Step 4: Run build and verify tests**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest`
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**
  Run: `git add app/src/main/java/dev/xera/xclicker/service/XClickerService.kt`
  Run: `git commit -m "fix: refactor XClickerService to query nodes asynchronously off main thread"`
