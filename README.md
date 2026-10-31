# Home WiFi Inspector

原生 Android 家庭 Wi-Fi 检测工具，Kotlin / Compose Material 3，最低 Android 10。当前交付为 **Phase 1 基础版 + Phase 2/3 部分功能**，并非完整六阶段产品。没有演示热点、假测速或云端账号。

## 功能
- 当前 SSID/BSSID、RSSI 分级与参考分、2.4/5/6 GHz、信道、Tx/Rx、协议、安全类型（系统可见时）。
- IP/前缀、IPv4 默认网关、DNS、Private DNS 可见状态、互联网能力/系统验证/认证门户、MLO link ID（支持时）。
- 前台 1/2/5 秒采样，最近 300 条 RSSI 图，暂停/继续、连接能力变化与 BSSID 切换事件。
- 手动附近 AP 扫描、系统广播更新、缓存年龄、同 SSID 分组、分频段信道分布、频宽及重叠拥挤估计。
- 用户授权后进行 Wi-Fi 绑定 TCP 建连测试、系统 DNS 查询，支持取消，显示统计及方法局限。
- Room 手动保存快照与最近检测，DataStore 采样设置，JSON/CSV 导出和默认脱敏、清除历史确认。
- 中文界面、跟随系统明暗主题；英日资源目录已预留，完整翻译尚未完成。

## 构建
需要 JDK 17、Android SDK Platform 35 和 SDK Build Tools 34.0.0（AGP 自动选择）、联网下载 Maven 依赖。

```powershell
$env:ANDROID_HOME = 'C:\Users\你的用户名\AppData\Local\Android\Sdk'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# 连接 Android 手机或启动模拟器后
.\gradlew.bat :app:connectedDebugAndroidTest
```

固定工具链：Gradle 8.9 / AGP 8.7.3 / Kotlin + Compose compiler 2.0.21 / Hilt 2.52 / Room 2.6.1。当前 compileSdk/targetSdk 为 35，为本机已安装且此 AGP 官方支持的最高 API；不是声称最新 Android SDK 为 35。升级至 37 需要一起升级 AGP/Gradle、SDK 并完成 Android 17 权限及行为回归。

## 安装
APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。这是开发签名调试版。

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可将 APK 复制到手机，允许文件管理器安装未知应用后安装。进入「设置」授予精确位置/附近设备权限并开启系统定位，连接家庭 Wi-Fi。拒绝权限时仍可使用历史及非敏感状态。网络检测页需确认授权，输入自己选择的目标与端口。网关默认使用输入端口，不假设路由器一定开放 443；可手动改为 80。测试仅建立 TCP 连接，不登录或读取管理配置。

## 文档与限制
- [架构](docs/architecture.md)
- [权限核对与官方来源](docs/permissions.md)
- [Android 限制](docs/android-limitations.md)
- [安全模型](docs/security-model.md)
- [阶段完成情况](docs/implementation-status.md)
- [测试与构建结果](docs/testing.md)

所有记录仅保存在应用私有目录；导出前默认隐藏网络标识与可能包含目标地址的详细文本。卸载会删除本地数据。未实现的设备发现、完整安全审计、吞吐测速、家庭热力图、PDF、历史对比见状态文档。模拟器无线结果不能代表真实 NURO/Buffalo 家庭网络。
