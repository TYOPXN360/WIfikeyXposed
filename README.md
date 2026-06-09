# WiFi万能钥匙 Xposed 模块

WiFi万能钥匙 5.2.18 的 Xposed 模块，提供本地 SVIP 永久解锁、全模块去广告、底栏/工具栏精简、WiFi 防护等功能。

[![Latest Release](https://img.shields.io/badge/最新版本-v1.5-blue)](https://github.com/TYOPXN360/WIfikeyXposed/releases/latest)
[![Downloads](https://img.shields.io/badge/平台-Android_11%2B-green)](https://github.com/TYOPXN360/WIfikeyXposed/releases)

## ⚠️ 代码生成说明

> **本模块的核心代码（MainHook.java、SettingsActivity.kt）由 AI 代码助手生成**，并在真机上验证功能可用性。

## 功能特性

### 核心功能
- **本地 SVIP 永久解锁**：无需订阅，自动解锁所有 SVIP 功能
- **全模块去广告**：移除开屏广告、列表广告、视频广告
- **云控去除**：移除云控配置对功能的影响
- **青少年模式增强**：优化青少年模式体验
- **免 Root 重启**：设置界面一键重启目标应用，无需 Root 权限

### 底栏精简
可隐藏底部导航栏中的标签页：
- 附近、视频、福利、消息、网页、守护、我的
- DeepSeek、商城、公交、电影、AI连接、逛逛

### 工具栏精简
支持独立控制以下首页工具栏组件的显示/隐藏：
- 垃圾清理、手机加速、手机降温、网络测速
- 网络加速、安全检测、快看、免费小说
- 游戏中心、更多、VIP 入口、用户反馈
- 赋能中心、动态卡片、目标30、地区工具

### 快捷设置绕过
- **跳过 QS 磁贴引导**：连接 WiFi 时跳过「添加快速设置磁贴」引导弹窗
- **跳过悬浮窗权限引导**：连接 WiFi 时跳过「授予悬浮窗权限」引导弹窗

### WiFi 防护（⚠️ 实验性功能，可能不生效）
- **阻止连接前清除网络**：拦截连接 WiFi 前静默删除 App 自建网络配置的行为
- **阻止先删后加模式**：拦截连接时先删除旧配置再添加新配置的 useDeleteModel 行为
- **阻止失败后清理配置**：拦截连接失败后静默清理 WiFi 网络配置的行为

## 已验证功能

- ✅ 底栏精简功能正常工作
- ✅ 工具栏精简功能正常工作
- ✅ SVIP 解锁功能正常
- ✅ 去广告功能正常
- ✅ 模块设置界面响应流畅
- ✅ 快捷设置绕过正常工作
- ✅ 免 Root 重启正常工作

## 技术信息

- **目标应用**：WiFi万能钥匙 5.2.18 (com.snda.wifilocating)
- **最低系统**：Android 11 (API 30)
- **Xposed API**：libxposed API 101
- **开发框架**：Android Gradle + Kotlin + Jetpack Compose
- **UI 设计**：Material 3 Dynamic Color

## 安装说明

1. 从 [Releases](https://github.com/TYOPXN360/WIfikeyXposed/releases/latest) 下载最新 APK
2. 确保设备已安装 LSPosed 框架
3. 安装 APK 并在 LSPosed 管理器中激活本模块
4. 勾选目标应用（WiFi万能钥匙）
5. 强制停止并重新打开目标应用

## 使用方法

1. 打开模块设置界面（在 LSPosed 中点击模块图标，或在桌面找到「无能的钥匙」）
2. 根据需求开启/关闭各项功能开关
3. 某些功能需要重启目标应用才能生效（点击底部「重启应用」按钮）

## 项目结构

```
WIfikeyXposed/
├── app/
│   └── src/main/java/com/ty/wifikeyxposed/
│       ├── MainHook.java          # 核心 Hook 逻辑
│       └── SettingsActivity.kt     # Jetpack Compose 设置界面
├── build.gradle.kts                # Gradle 构建配置
└── settings.gradle.kts             # 项目设置
```

## 注意事项

- 本模块仅供学习研究使用，请勿用于商业目的
- 部分功能可能会因为应用更新而失效
- WiFi 防护功能为实验性功能，可能不完全生效
