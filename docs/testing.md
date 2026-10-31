# 测试

## 自动化
纯 Kotlin JUnit 覆盖 RSSI 阈值、缺测分数、已测失败影响、2.4/5/6GHz/4.9GHz频率信道转换、延迟中位数/P95/Jitter、全部失败、重叠拥挤估计及证据规则。Android instrumentation 测试使用真实 Room 内存数据库验证写入/读取/删除，不向产品展示测试数据。

## 真机验收矩阵
- Android10/12/13/15/16/17：拒绝/撤销精确定位、只允许大致定位、拒绝附近设备、定位服务关闭。
- Wi-Fi关闭、移动网络、无互联网Wi-Fi、认证门户、IPv6-only、切换AP/SSID、前后台和横竖屏。
- 连续请求扫描：本应用30秒限制、系统拒绝、缓存年龄、隐藏SSID、多频段AP。
- 自有网关开放/拒绝/超时端口；目标域名失败，DNS超时；点击取消和切后台不继续新探测。
- API36 opt-in局域网保护与API37 target37实际权限（当前SDK35构建未覆盖）。
- 保存、重启后历史、JSON/CSV脱敏不泄露SSID/BSSID/IP/目标，撤销导出、清除历史确认。
- NURO + Buffalo真实手机：一楼/二楼RSSI差异及2.4/5/6GHz读取，链路速率不能用作互联网速度。

## 0.1.0 执行结果
构建、单元、Lint 与模拟器结果如下。没有连接实体手机，硬件射频与API37验证未执行。

实际执行日期：2026-10-31。

| 检查 | 结果 |
|---|---|
| assembleDebug | BUILD SUCCESSFUL，生成调试 APK |
| testDebugUnitTest | 9 tests，0 failures / errors / skipped |
| lintDebug | 0 errors，12 warnings（11 项依赖更新提示 + Kapt 可迁移 KSP 提示） |
| connectedDebugAndroidTest | Android 15 / API35 独立模拟器，3 tests，0 failures / errors / skipped |
| 最终 APK 安装/启动 | adb install Success；am start Status ok，冷启动约 1.44 秒；无 AndroidRuntime 崩溃日志 |
| 权限降级 | 未授予定位时 SSID 显示无法获取，状态提示需要精确位置权限及定位服务 |
| Wi-Fi关闭 | 系统关闭 Wi-Fi 后显示 Wi-Fi已关闭，RSSI/频段/信道改为无法获取 |
| 系统授权后 | 读取模拟器 AndroidWifi 的 BSSID、RSSI、IP、网关与 DNS，收到扫描更新，出现 1 个系统 AP |
| TCP 实际探测 | 独立本地测试监听器，经模拟器 10.0.2.2:18765：5次中2次建连成功、3次超时；按原始结果显示60%失败率，无虚构延迟，跨失败不计算Jitter |

TCP 测试是虚拟网络和本地服务的冒烟验证，不能代表实际家庭链路质量或带宽。临时监听器已停止。模拟器 RSSI 等由 Android 虚拟硬件提供，应用不包含这些静态演示值。

最终执行命令：
```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# 集成测试已另行执行通过
.\gradlew.bat :app:connectedDebugAndroidTest
```

报告在 `app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/reports/androidTests/connected/debug/index.html` 和 `app/build/reports/lint-results-debug.html`。截图：[首页](screenshots/dashboard-api35.png)、[实际TCP检测](screenshots/diagnostics-api35.png)。

实体 Android 手机、NURO/Buffalo设备、多楼层覆盖、API29/36/37、系统扫描拒绝/局域网权限受限仍未验证。集成测试覆盖数据库和导出，不覆盖完整 UI 和无线硬件矩阵。

0.1.0 APK SHA256：`BED48EA92EE4D3C6A956844F8E4ECEAF80B5979562DA7B472FCE4D2AC8E6A99B`。大小：25,516,944 字节。


## 0.2.0 执行结果（2026-10-31）
- assembleDebug、testDebugUnitTest、lintDebug 已通过；15项单元测试（原有9项 + 简明网络解释/速度换算/样本充分性6项），0失败。
- Android15/API35模拟器5项集成测试通过：Room快照CRUD、位置记录改名与测量值保持、v1→v2旧数据迁移、JSON/CSV脱敏及CSV转义。
- 最终APK安装与冷启动正常，未见AndroidRuntime崩溃。手动记录位置的信号、历史显示未测速、修改名称为Room2均完成验证。
- 实际HTTPS下载测试：默认25MB端点，10.2秒读取7.1MB，显示5.5Mbps / 0.7MB/s，保存至位置1；此值是模拟器测试环境结果，不代表用户手机或家庭宽带。
- 曾发现100MB单次请求返回HTTP403，已改为每次默认25MB、最多4个顺序请求，并保留总100MB/约10秒读取预算。失败时不生成网速结果。
- 首页主视图只显示简明结论与位置输入，服务器和方法说明可展开。位置历史截图见screenshots/locations-v02-api35.png。
- 尚未在实体手机验证新下载测速；服务器限制、线路、手机性能会影响结果。位置记录目前不纳入JSON/CSV导出。

0.2.0 最终 APK SHA256：E7EF40B02D5CE75839D0F3D61A1EF896454784C416EB9679C542F2704A3857C7。调试安装包：app/build/outputs/apk/debug/HomeWiFiInspector-v0.2.0-debug.apk。Lint：0 errors / 12 warnings（依赖更新与Kapt迁移提示）。


## 0.3.0 执行结果（2026-10-31）
- assembleDebug / testDebugUnitTest / lintDebug：通过。20项单元测试，0失败/错误；Lint为0错误、12项依赖更新/Kapt提示。
- API35/Android15模拟器：9项集成测试，0失败/错误/跳过。覆盖v1→v3和v2→v3旧数据迁移、位置网络类型/坐标可空性、本机默认网络读取、定位权限拒绝、公共IP网关目标拒绝及原有数据库/导出功能。
- 默认主机无需输入：Wi-Fi下对www.cloudflare.com的HTTPS HEAD返回200，总响应935.28ms；关闭Wi-Fi后改用模拟器移动网络，同目标返回200，总响应255.48ms。均显示实际方法与总响应时间，不称为TCP延迟或带宽。
- 自有模拟器私有网关10.0.2.2：系统ICMP 3发3收，原始ping平均2.976ms；80/443各1次TCP均超时。应用同时保留成功ICMP和失败端口证据，没有将其认定断网或漏洞。
- 前台系统定位：并行GPS/网络/融合提供者成功收到手动注入模拟器的35.00000,139.00000，显示系统报告精度5米与采集时间。该坐标来自测试注入，不是用户的实际位置，也不能证明真实GPS精度。系统模拟标记按Android返回值展示，不以模拟器场景代替真机定位验证。
- 定位附加到移动网络位置记录后，历史可见坐标、采集时间、网络类型；Wi-Fi信号明确不适用，下载值未测时保持空。
- 移动网络下实际下载成功，显示12.6Mbps（1.6MB/s）并保存位置；这只是模拟器通过宿主网络的测试结果，不代表真实运营商移动网络或用户宽带。确认框明确移动/计费流量提示。
- 新版可安装、启动、切换页面，未见AndroidRuntime崩溃。截图在screenshots/*-v03-api35.png。
- 实体手机NURO/Buffalo网关ICMP响应、实际蜂窝网络、VPN、以太网、API29/36/37及厂商定位/权限策略仍待实机验证。关闭的管理端口不能由应用强行变成可访问，系统或路由器禁止的能力会显示限制。

0.3.0 最终 APK：HomeWiFiInspector-v0.3.0-debug.apk，26473699字节，SHA256：4040AE54C023A3F1804403827C65EE7213902231B245DA6F95B9661EF10DB5AB。
