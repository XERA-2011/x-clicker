# xClicker

[![Build-Apk](https://github.com/XERA-2011/x-clicker/actions/workflows/Build-Apk.yml/badge.svg)](https://github.com/XERA-2011/x-clicker/actions/workflows/Build-Apk.yml)
[![Sponsor](https://img.shields.io/badge/Sponsor-❤️-ff69b4.svg)](https://github.com/XERA-2011/sponsor)

xClicker 是一款基于 Jetpack Compose + MVVM 架构的 Android 现代无障碍（Accessibility）自动化应用框架模板。

它为您提供了一套完整的本地化、响应式、且高度可定制的 UI 界面，以及健壮的底层无障碍服务调用逻辑，非常适合作为您开发个人专属自动化辅助工具（如模拟点击、特定场景的屏幕交互）的起点。

## ❤️ 赞助与支持

如果您觉得本项目对您的学习或工作有帮助，欢迎 [点击这里赞助支持](https://github.com/XERA-2011/sponsor)，感谢您的鼓励！


## ⚠️ 免责与合规声明（重要）

1. **研究与学习目的**：本项目完全开源，仅供 Android 开发者进行无障碍服务（Accessibility API）及 Compose UI 开发技术的**学习、研究与交流**使用。
2. **无恶意行为**：本仓库作为基础框架，**不包含任何预设的恶意脚本、破解规则或云端规则分发功能**。应用内不包含任何侵犯他人隐私或破坏其他软件正常运行的代码。
3. **合法合规使用**：请使用者在二次开发或日常使用中，务必遵守当地法律法规以及目标应用（第三方 App）的用户协议。**切勿将本框架用于任何非法、作弊、刷量或损害第三方利益的商业场景。**
4. **责任豁免**：因使用者滥用本框架或其衍生版本所引发的一切法律纠纷、账号封禁或经济损失，均由使用者本人承担，原作者与本项目贡献者概不负责。

## 🤝 致谢与原项目参考

本项目的底层无障碍交互核心逻辑与部分架构设计，**参考并脱胎于优秀的开源项目：[GKD (gkd-kit/gkd)](https://github.com/gkd-kit/gkd)**。
我们对 GKD 原作者及所有社区贡献者的开源精神与杰出工作表示最诚挚的感谢！xClicker 在其基础上进行了大量精简与重构，专注于打造一个纯净的个人开发框架。

## 🏗️ 技术栈

本项目采用了现代 Android 开发的最新标准：

| 组件分类 | 技术选型 |
|------|------|
| **语言** | 100% Kotlin |
| **UI 框架** | Jetpack Compose + Material Design 3 |
| **架构模式** | MVVM (Model-View-ViewModel) |
| **构建系统** | Gradle Kotlin DSL (.kts) |
| **系统要求** | 最低 SDK: API 26 (Android 8.0) <br> 目标 SDK: API 35 (Android 15) |

## 🚀 构建与运行

1. 使用 **Android Studio (推荐最新版)** 打开本项目。
2. 等待 Gradle 同步及依赖下载完成。
3. 点击顶部工具栏的运行按钮（Run 'app'）安装到您的 Android 设备或模拟器。

或者使用命令行进行编译：
```bash
./gradlew assembleDebug
```

## 📄 License

[MIT License](LICENSE)