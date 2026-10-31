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

## 执行结果
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

最终 APK SHA256：`BED48EA92EE4D3C6A956844F8E4ECEAF80B5979562DA7B472FCE4D2AC8E6A99B`。大小：25,516,944 字节。
