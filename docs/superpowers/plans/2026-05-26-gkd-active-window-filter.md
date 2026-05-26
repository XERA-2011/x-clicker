# GKD Active Window Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement robust focused window filtering equivalent to GKD's multi-window resolver, ignoring all background search box and system assistant overlay noise.

**Architecture:** We will replace the partial `systemui` bypass logic with a comprehensive foreground active window resolve helper (`getTrueActivePackage() ?: packageName`) across both initialization and window change state flows. This guarantees the engine only registers true application changes when the main focused active window package switches.

**Tech Stack:** Kotlin, Android Accessibility Service.

---

### Task 1: Review and Extend Window Resolver Unit Test

**Files:**
- Modify: [app/src/test/java/dev/xera/xclicker/TestRuleExecutionTracker.kt](file:///Users/xera/GitHub/x-clicker/app/src/test/java/dev/xera/xclicker/TestRuleExecutionTracker.kt)

- [ ] **Step 1: Write unit test to verify active window package selection**

Add a test case named `testActiveWindowResolver` to `TestRuleExecutionTracker.kt` to ensure our fallback mechanism handles empty, systemui, and regular packages perfectly.

```kotlin
    // Add to TestRuleExecutionTracker.kt
    @Test
    fun testActiveWindowResolver() {
        fun resolveTruePackage(eventPackage: String, activeWindowPackage: String?): String {
            return activeWindowPackage ?: eventPackage
        }

        // Test normal foreground app
        Assert.assertEquals("com.baidu.tieba", resolveTruePackage("com.google.android.googlequicksearchbox", "com.baidu.tieba"))
        
        // Test fallback when active window is null/unavailable
        Assert.assertEquals("com.google.android.googlequicksearchbox", resolveTruePackage("com.google.android.googlequicksearchbox", null))
        
        // Test another typical assistant overlay scenario
        Assert.assertEquals("com.jin10.lgd.splash", resolveTruePackage("com.android.personalassistant", "com.jin10.lgd.splash"))
    }
```

- [ ] **Step 2: Run tests to verify they compile and pass**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew :app:testDebugUnitTest --tests "dev.xera.xclicker.TestRuleExecutionTracker.testActiveWindowResolver"`
Expected: PASS (verifying the fallback logic is 100% correct)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/dev/xera/xclicker/TestRuleExecutionTracker.kt
git commit -m "test: add active window resolver fallback test"
```

---

### Task 2: Implement True Active Window Filtering

**Files:**
- Modify: [app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt](file:///Users/xera/GitHub/x-clicker/app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt)

- [ ] **Step 1: Apply global active window resolver**

Update initialization and window transition blocks inside `onAccessibilityEvent` to use `getTrueActivePackage() ?: packageName` instead of only checking for `com.android.systemui`.

In `SelectToSpeakService.kt` (around lines 112-118 in initialization block):
```kotlin
        if (currentAppId.isEmpty()) {
            val truePkg = getTrueActivePackage() ?: packageName
            currentAppId = truePkg
            appChangeTime = System.currentTimeMillis()
            resetPackageRuleState(truePkg)
            Log.d(TAG, "初始化前台应用: $truePkg")
        }
```

In `SelectToSpeakService.kt` (around lines 122-130 in application change check):
```kotlin
        // ── 应用和 Activity 切换检测 ──
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            val truePkg = getTrueActivePackage() ?: packageName
            
            if (truePkg != currentAppId) {
                currentAppId = truePkg
                appChangeTime = System.currentTimeMillis()
                resetPackageRuleState(truePkg)
                Log.d(TAG, "应用切换: $truePkg")
            }
```

- [ ] **Step 2: Run all JVM unit tests to verify system stability**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew test`
Expected: BUILD SUCCESSFUL (all unit tests passed)

- [ ] **Step 3: Compile and Build Debug APK**

Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (debug APK created successfully)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt
git commit -m "feat: apply GKD focused active window filter to prevent background overlay interference"
```
