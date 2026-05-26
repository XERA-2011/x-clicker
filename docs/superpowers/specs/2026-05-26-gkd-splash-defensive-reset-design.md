# 2026-05-26 GKD 开屏广告防御性重置设计规格书

## 📋 背景与目标

目前，x-clicker 系统在应用切换时依赖 `truePkg != currentAppId` 条件来重置规则的冷却时间（CD）和最大点击限制（`actionMaximum`）。
然而，在真实的 Android 环境中，用户点击 Home/Back 键退回桌面或者杀掉 App 时，Launcher 切换事件经常被漏掉，导致系统中的 `currentAppId` 未能及时清空。这使得用户在第二次（或后续）打开金十数据、百度贴吧等 App 时，因为 `truePkg == currentAppId` 而**无法触发规则状态重置**，上一次点击的 CD 或点击上限依然保留，导致后续的开屏广告无法被跳过。

本轮设计的目标是：
- 引入**开屏 Activity 特征识别**，自动检测 `SplashActivity`、`LaunchActivity` 等各类启动页面。
- 当用户进入任意开屏 Activity 时，**无条件且安全地重置该 Package 下的所有规则运行状态（CD 与 Max 点击次数）**。
- 确保冷启动、热启动以及频繁退出重进场景下，广告跳过的 100% 成功率与稳定性。

---

## 🛠️ 方案架构与详细设计

### 1. 新增开屏 Activity 识别辅助函数
在 `SelectToSpeakService.kt` 中添加 `isSplashActivity` 私有方法，覆盖市面上绝大多数 Android 应用的开屏/广告类命名：

```kotlin
private fun isSplashActivity(className: String): Boolean {
    val lower = className.lowercase()
    return lower.contains("splash") ||
           lower.contains("advert") ||
           lower.contains("welcome") ||
           lower.contains("guide") ||
           lower.contains("logo") ||
           lower.contains("launch") ||
           lower.contains("transition") ||
           lower.contains("loading") ||
           lower.contains("startup") ||
           lower.contains("openad")
}
```

### 2. 重写 Activity 切换监听块
在 `SelectToSpeakService.kt` 的 `onAccessibilityEvent` 中，当 `isActivity` 判定成功且 Activity 发生实质性切换时，织入重置拦截：

```kotlin
if (isActivity) {
    if (appActivityIds[truePkg] != className) {
        appActivityIds[truePkg] = className
        activityChangeTime = System.currentTimeMillis()
        
        // 当重新进入开屏 Activity 时，强制重置该 Package 的匹配规则状态，打破对包名切换的单一依赖
        if (isSplashActivity(className)) {
            resetPackageRuleState(truePkg)
            Log.d(TAG, "检测到进入开屏Activity: $className，强制重置应用 [$truePkg] 的规则运行状态")
        }
        
        resetActivityRuleState(truePkg)
        Log.d(TAG, "Activity切换: $className")
    }
}
```

---

## 🧪 自动化与验证计划

1. **测试驱动开发 (TDD)**：我们将在 `TestRuleExecutionTracker.kt` 中编写单元测试：
   - 验证记录了某一应用的多次点击并达到 `actionMaximum` 限制后，模拟进入 Splash 页面并触发重置，验证限制状态是否成功清空。
2. **真机集成验证**：实测金十数据、百度贴吧在反复冷启动、热启动、后台挂起再唤醒的场景下，开屏广告是否能被 100% 顺利拦截。
