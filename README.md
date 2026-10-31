# Home WiFi Inspector

原生 Android 家庭 Wi-Fi 检测工具，Kotlin / Compose Material 3，最低 Android 10。当前交付为 **0.3.0，Phase 1 基础版 + Phase 2/3 部分功能**，并非完整六阶段产品。没有演示热点、假测速或云端账号。

## 0.3.0：默认上网网络与失败诊断改进
- 默认测试目标已填 `www.cloudflare.com`，端口443；推荐先执行HTTPS上网连通性检测。收到401/403/重定向等真实状态会解释，不会统一当成断网。
- 检测当前默认上网网络：Wi-Fi、移动网络、以太网、VPN或其他Android可见网络。DNS/TCP/HTTPS/下载使用同一选定默认网络，切换时停止；不主动绕过VPN。VPN是否允许指定网络套接字受系统/服务影响，失败会显示实际错误。
- 非Wi-Fi网络不显示虚构RSSI/SSID/链路速率；移动网络/计费Wi-Fi下载前提示流量。信号不适用时仍可记录位置与网速。
- 自有网关检查改为授权后的有限系统ICMP与80/443连接，不将443关闭等同断网；只支持私有IPv4默认网关，间隔至少30秒。
- Wi-Fi页增加权限与系统定位入口；无扫描数据时不显示0.0拥挤指数，扫描等待15秒超时有说明；去除重复网络能力事件。
- 可选本机系统定位，显示坐标/精度/时间/模拟标记；15秒超时，无后台定位，只将2分钟内定位附加到新位置记录。室内房间仍手动命名。数据库升级到v3并保留旧记录。
- [日本使用边界与隐私说明](docs/japan-use-boundaries.md)。软件不实施密码猜测、认证绕过、漏洞利用或未经授权的设备扫描；使用者仍须遵守所在网络和服务规则。

## 0.2.0：位置记录与简明网络情况
可命名当前位置（位置1、客厅、二楼卧室等）并添加备注。首页「只记录这个位置的信号」保存当前真实RSSI；「测网速并记录这个位置」成功后自动保存测速开始时的信号、频段、SSID及实测下载结果。请在该位置停留直至完成。未测速的记录明确显示未测量；没有以链路速率填充下载速度。

进入「历史」查看位置记录，可修改每条记录的位置名称和备注，保留原始信号、速度与时间。可用同名位置多次记录对比条件变化。Room数据库1→2升级保留已有检测快照。位置记录目前不包含在原有JSON/CSV快照导出中；清除历史会同时清除位置记录。

## 一眼看懂网络情况
首页新增信号强弱与下载体验说明，点击「测一下上网下载速度」，确认服务器和流量后执行真实单连接 HTTPS 下载。默认 Cloudflare，也可填写自己的专用 HTTPS 测速下载端点。最多读取 100 MB，读取约10秒，总超时18秒；显示 Mbps、MB/s、时长、下载流量、服务器及时间。可取消，切后台或网络变化会停止；0.3.0改为仅使用确认时的当前默认网络，移动网络会提示流量。有效采样至少2秒且1MB，否则不判断快慢。普通网页/JSON、压缩数据和重定向被拒绝。结果受服务端和线路影响，不等于套餐峰值；未测上传速度。协议开销和系统缓冲可能使实际流量略多。

体验分级仅为粗略参考：下载≥50 Mbps「较快」，10–50「适中」，低于10「较慢」；不推断游戏延迟或多人同时使用。信号强弱单独根据实测RSSI描述。保存按钮将当前测速详情写入原有本机历史，默认脱敏导出会移除服务器信息。

端点方法参考：https://github.com/cloudflare/speedtest 。本应用仅使用下载端点，不运行其 JavaScript SDK，也不向日志接口发送采集信息。

## 功能
- 当前 SSID/BSSID、RSSI 分级与参考分、2.4/5/6 GHz、信道、Tx/Rx、协议、安全类型（系统可见时）。
- IP/前缀、IPv4 默认网关、DNS、Private DNS 可见状态、互联网能力/系统验证/认证门户、MLO link ID（支持时）。
- 前台 1/2/5 秒采样，最近 300 条 RSSI 图，暂停/继续、连接能力变化与 BSSID 切换事件。
- 手动附近 AP 扫描、系统广播更新、缓存年龄、同 SSID 分组、分频段信道分布、频宽及重叠拥挤估计。
- 用户主动确认后进行当前默认网络绑定的 TCP/HTTPS 建连测试、系统 DNS 查询，支持取消，显示统计及方法局限。
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
- [日本使用边界](docs/japan-use-boundaries.md)
- [阶段完成情况](docs/implementation-status.md)
- [测试与构建结果](docs/testing.md)

所有记录仅保存在应用私有目录；导出前默认隐藏网络标识与可能包含目标地址的详细文本。卸载会删除本地数据。未实现的设备发现、完整安全审计、上传/多连接吞吐测速、家庭热力图、PDF、历史对比见状态文档。模拟器无线结果不能代表真实 NURO/Buffalo 家庭网络。
