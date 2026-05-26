# x-clicker

基于 Android 无障碍服务（AccessibilityService）的轻量级自动化点击引擎。

## 📋 简介

x-clicker 现已重构并集成 [Android-Selector](https://github.com/gkd-kit/selector) 引擎，全面兼容 GKD 的高级语法与订阅格式。它通过底层的原生穿透查找（Native Fast-Find）和层级回溯机制，能够轻松应对复杂的广告 SDK。

作为一个中立的"规则执行器"，源码及发行版中**不内置、不提供、也不维护**任何针对特定第三方 App 的规则。

## 🏗️ 技术栈

| 项目 | 选型 |
|------|------|
| 语言 | 100% Kotlin |
| 匹配引擎 | li.songe.selector (GKD Selector) |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 (Android 15) |

## 🚀 构建

1. 使用 Android Studio 打开本项目
2. 等待 Gradle 同步完成
3. Build → Run 安装到设备

```bash
./gradlew assembleDebug
```

## 📝 规则格式

本项目全面兼容 **GKD (全局快捷点击)** 的订阅格式与底层语法，支持 CD 冷却、次数限制及多节点层级匹配。

### 高级特性支持
* **Native Fast-Find**: 利用 Android 系统底层 API 穿透异常 `childCount` 的广告 SDK。
* **层级回溯**: 完美支持 `@View > TextView[text="跳过"]` 等带目标偏移的选择器。
* **全量属性**: 支持 `depth`、`checkable` 等所有的标准节点属性。

## ⚖️ 免责声明

本项目仅提供自动化点击的底层框架能力，所有自动化行为均由用户自行编写或导入的规则驱动。
项目不对任何用户自定义规则的合法性、合规性负责。

## 📄 License

MIT License