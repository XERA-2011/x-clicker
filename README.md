# xClicker

[![Build-Apk](https://github.com/XERA-2011/x-clicker/actions/workflows/Build-Apk.yml/badge.svg)](https://github.com/XERA-2011/x-clicker/actions/workflows/Build-Apk.yml)
[![Sponsor](https://img.shields.io/badge/Sponsor-❤️-ff69b4.svg)](https://github.com/XERA-2011/sponsor)

xClicker 是一款基于 Android 无障碍服务的规则自动化点击工具，使用 Jetpack Compose 构建界面。

它支持管理应用规则与全局规则、使用本地或在线订阅，并按规则执行自动化操作。项目以个人设备上的轻量自动化为目标。


## ⚠️ 免责与合规声明（重要）

1. **研究与学习目的**：本项目完全开源，仅供 Android 开发者进行无障碍服务（Accessibility API）及 Compose UI 开发技术的**学习、研究与交流**使用。
2. **规则来源**：本仓库不预置面向特定第三方应用的规则。应用支持用户自行添加本地或在线规则订阅；请在导入前核验订阅来源及内容。项目不托管或保证第三方规则的安全性、有效性。
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
| **系统要求** | 最低 SDK: API 26 (Android 8.0) <br> 目标 SDK: API 37 |

## 📱 使用说明

首次使用时，请在 Android 系统设置中手动启用 xClicker 的无障碍服务，并按需授予应用列表访问权限。随后可在应用内添加或导入规则订阅、配置规则范围。无障碍服务的可用性和后台运行行为可能受设备厂商的系统设置影响。

## 🚀 构建与运行

1. 使用 **Android Studio (推荐最新版)** 打开本项目。
2. 等待 Gradle 同步及依赖下载完成。
3. 点击顶部工具栏的运行按钮（Run 'app'）安装到您的 Android 设备或模拟器。

或者使用命令行构建默认的 xclicker 调试版本：

```bash
./gradlew :app:assembleXclickerDebug
```

## ❤️ 赞助与支持

如果您觉得本项目对您的学习或工作有帮助，欢迎 [点击这里赞助支持](https://github.com/XERA-2011/sponsor)，感谢您的鼓励！

## 📄 License

[MIT License](LICENSE)
