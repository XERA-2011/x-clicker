# Phase 5: Service Name Disguise & Keep-Alive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Disguise the accessibility service name as Google's official `SelectToSpeakService` under the `com.google.android.accessibility.selecttospeak` package to bypass ROM background-killing.

**Architecture:** 
1. Create new package structures and migrate `XClickerService.kt` to `SelectToSpeakService.kt` under package `com.google.android.accessibility.selecttospeak`, deleting the original file.
2. Update service definition in `AndroidManifest.xml` to use the fully qualified class name.
3. Update imports and references across UI modules (`MainViewModel`, `HomeScreen`) to ensure flawless compilation.

**Tech Stack:** Kotlin, Android Manifest.

---

### Task 1: Create and Relocate SelectToSpeakService

**Files:**
- Create: `app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt`
- Delete: `app/src/main/java/dev/xera/xclicker/service/XClickerService.kt`

- [ ] **Step 1: Write SelectToSpeakService.kt with updated package & class name**
  We will create the new file and place the entire code from our previous optimized service into it, renaming the class name, imports, tags, and type definitions from `XClickerService` to `SelectToSpeakService`.
  Make sure to import:
  ```kotlin
  import dev.xera.xclicker.service.ActionLog
  import dev.xera.xclicker.service.ActionExecutor
  import dev.xera.xclicker.service.RuleExecutionTracker
  ```

- [ ] **Step 2: Delete dev/xera/xclicker/service/XClickerService.kt**
  Remove the old file completely to avoid duplicate class compilation errors.

---

### Task 2: Update AndroidManifest.xml Registration

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Update service registration to the absolute package path**
  Change the service name from `.service.XClickerService` to `com.google.android.accessibility.selecttospeak.SelectToSpeakService`.
  ```xml
          <service
              android:name="com.google.android.accessibility.selecttospeak.SelectToSpeakService"
              android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
              android:exported="true"
              android:label="@string/accessibility_service_label">
              <intent-filter>
                  <action android:name="android.accessibilityservice.AccessibilityService" />
              </intent-filter>
              <meta-data
                  android:name="android.accessibilityservice"
                  android:resource="@xml/accessibility_config" />
          </service>
  ```

---

### Task 3: Refactor UI References & Verify Compile

**Files:**
- Modify: `app/src/main/java/dev/xera/xclicker/ui/MainViewModel.kt`
- Modify: `app/src/main/java/dev/xera/xclicker/ui/screen/HomeScreen.kt`

- [ ] **Step 1: Refactor MainViewModel.kt imports & state flow references**
  Replace `dev.xera.xclicker.service.XClickerService` import with:
  ```kotlin
  import com.google.android.accessibility.selecttospeak.SelectToSpeakService
  ```
  And update reference `XClickerService.isRunning` to `SelectToSpeakService.isRunning`.

- [ ] **Step 2: Refactor HomeScreen.kt imports & class references**
  Replace `dev.xera.xclicker.service.XClickerService` import with:
  ```kotlin
  import com.google.android.accessibility.selecttospeak.SelectToSpeakService
  ```
  And update reference `XClickerService::class.java` to `SelectToSpeakService::class.java`.

- [ ] **Step 3: Run full verification build**
  Run: `JAVA_HOME=/Users/xera/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew test assembleDebug`
  Expected: BUILD SUCCESSFUL.
