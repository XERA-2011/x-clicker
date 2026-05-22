# x-clicker

基于 Android 无障碍服务（AccessibilityService）的轻量级自动化点击引擎。

## 📋 简介

x-clicker 从零重构并复刻"李跳跳"的核心节点解析与执行逻辑，完美兼容其原有的自定义规则语法。
作为一个中立的"规则执行器"，源码及发行版中**不内置、不提供、也不维护**任何针对特定第三方 App 的规则。

## 🏗️ 技术栈

| 项目 | 选型 |
|------|------|
| 语言 | 100% Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM（手动 DI） |
| 存储 | DataStore + JSON 文件 |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 (Android 15) |

## 🚀 构建

1. 使用 Android Studio 打开本项目
2. 等待 Gradle 同步完成（IDE 会自动生成 Gradle Wrapper）
3. Build → Run 安装到设备

```bash
./gradlew assembleDebug
```

## 📝 规则格式

兼容李跳跳 JSON 规则格式：

```json
[
  {
    "package_hash": {
      "popup_rules": [
        {
          "id": "跳过",
          "action": "跳过",
          "delay": 200,
          "times": 1
        }
      ],
      "ltt_service": true
    }
  }
]
```

### 匹配语法

| 前缀 | 模式 | 示例 |
|------|------|------|
| 无 | 模糊匹配 | `"id": "跳过"` |
| `+` | 前缀匹配 | `"id": "+检测"` |
| `-` | 后缀匹配 | `"id": "-体验"` |
| `=` | 精确匹配 | `"id": "=以后再说"` |
| `&` | 多条件 | `"id": "+检测&-体验"` |

## ⚖️ 免责声明

本项目仅提供自动化点击的底层框架能力，所有自动化行为均由用户自行编写或导入的规则驱动。
项目不对任何用户自定义规则的合法性、合规性负责。

## 📄 License

MIT License