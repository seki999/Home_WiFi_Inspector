# Android 权限与行为核对

核对日期：2026-10-31。当前 targetSdk=35，minSdk=29。

| 权限 | 用途与限制 |
|---|---|
| ACCESS_WIFI_STATE | 读取 Wi-Fi；敏感信息仍可能被脱敏 |
| CHANGE_WIFI_STATE | 用户主动 startScan；不改变热点配置 |
| ACCESS_NETWORK_STATE | LinkProperties、NetworkCapabilities 与回调 |
| INTERNET | 用户主动 DNS/TCP 检测 |
| ACCESS_COARSE_LOCATION + ACCESS_FINE_LOCATION | Android 12+ 同时申请，必须获得精确位置才能完成扫描；扫描还需开启系统定位 |
| NEARBY_WIFI_DEVICES | Android 13+ 按系统版本申请；不能代替扫描的精确位置权限 |
| ACCESS_LOCAL_NETWORK | manifest 预声明，运行时 API>=37 时按字符串申请；当前 target35 不代表已完成 target37 回归 |

拒绝后继续显示可获取状态；SSID/BSSID 若系统脱敏则显示权限不足或无法获取。设置页可打开应用权限与系统定位设置，不循环弹窗。

Android 10+ 前台扫描一般受每 2 分钟 4 次限制，后台全部应用合计每 30 分钟一次。应用仅提供手动请求且间隔至少 30 秒，不声称这能保证系统接受。startScan=false 或结果广播失败时显示可能原因，系统并不提供精确拒绝理由；缓存逐 AP 显示 monotonic timestamp 年龄。

Android 16 局域网保护为 opt-in，启用时临时使用 NEARBY_WIFI_DEVICES；Android 17 target37+ 强制 ACCESS_LOCAL_NETWORK。当前实现展示权限错误和请求入口，但 API37 SDK/设备没有安装，不能声称完成其运行验证。

官方来源：
- https://developer.android.com/develop/connectivity/wifi/wifi-scan
- https://developer.android.com/develop/connectivity/wifi/wifi-permissions
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/build/releases/agp-8-7-0-release-notes

AGP 8.7 官方最高 API35，要求 Gradle8.9/JDK17；本版固定此已安装兼容链。更高版本迁移属于明确待办。
