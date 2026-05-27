# x-clicker

一个纯净的 Android 开发基础项目模板（Blank Project Boilerplate）。

## 📋 简介

x-clicker 之前包含了复杂的 GKD 底层引擎与无障碍服务。在最新的一次项目架构清理中，所有业务代码（包括引擎解析、UI 界面、服务逻辑、第三方库依赖）均已被彻底移除。

目前，这是一个 100% 纯净、可以正常编译通过的空白基础框架，随时可以作为新架构或新方向开发的起点。

## 🏗️ 技术栈

| 项目 | 选型 |
|------|------|
| 语言 | 100% Kotlin |
| 构建系统 | Gradle Kotlin DSL (.kts) |
| UI 层 | Jetpack Compose + Material 3 |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 (Android 15) |

## 🚀 构建与运行

1. 使用 Android Studio 打开本项目
2. 等待 Gradle 同步完成
3. 点击顶部工具栏绿色的运行按钮（Run 'app'）安装到设备

命令行编译：
```bash
./gradlew assembleDebug
```

## 📄 License

MIT License