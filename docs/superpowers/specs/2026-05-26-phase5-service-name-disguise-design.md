# Phase 5: Service Name Disguise & Keep-Alive Design

## Overview
This document specifies the design for Phase 5 of x-clicker's optimization path, focusing on disguising the accessibility service name to bypass aggressive background-killing policies implemented by Chinese Android ROMs (HyperOS/MIUI, ColorOS, etc.).

## Background
Domestic custom ROMs maintain system-level whitelists for popular services (e.g., Google Select-to-Speak). By renaming our service to match Google's official class path, the ROMs automatically bypass restrictions, omit security warning timers, and maintain high background process persistence.

## Design Details

### 1. Service Relocation & Renaming
- **Old Path:** `app/src/main/java/dev/xera/xclicker/service/XClickerService.kt`
- **New Path:** `app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt`
- **Class Change:**
  - Rename `XClickerService` to `SelectToSpeakService`.
  - Update package name to `com.google.android.accessibility.selecttospeak`.
  - Update companion object instance name and `TAG` string.

### 2. Manifest Registration
Update `AndroidManifest.xml` to declare the service using its fully qualified name:
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

### 3. Global References Update
The new service class must be referenced in the following files:
- **`app/src/main/java/dev/xera/xclicker/ui/MainViewModel.kt`**:
  - Update import: `import com.google.android.accessibility.selecttospeak.SelectToSpeakService`
  - Monitor running state: `SelectToSpeakService.isRunning`
- **`app/src/main/java/dev/xera/xclicker/ui/screen/HomeScreen.kt`**:
  - Update import: `import com.google.android.accessibility.selecttospeak.SelectToSpeakService`
  - Accessibility check: `SelectToSpeakService::class.java`

## Verification Plan
1. **Compilation**: Ensure `./gradlew assembleDebug` compiles successfully without any package resolution errors.
2. **Unit Tests**: Ensure `./gradlew test` executes and passes all test suites.
3. **Local Deployment**: Deploy to a physical device/emulator and verify that the Accessibility Settings menu correctly displays the service label and launches successfully.
