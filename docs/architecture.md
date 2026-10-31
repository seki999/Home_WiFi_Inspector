# 架构

Activity / Compose → Hilt ViewModel → WifiSource / NetworkTester → Android 系统与 Socket/DnsResolver。

`domain/Models.kt` 定义不依赖 Android 的采集与检测接口、可空指标；`domain/Analysis.kt` 为可测试的纯 Kotlin 规则、信道转换及统计。`data/AndroidWifiSource.kt` 封装系统读取、NetworkCallback、扫描广播；`SocketNetworkTester.kt` 封装绑定当前默认网络的 DNS 和 TCP。`data/Storage.kt` 封装 Room 与 DataStore；Hilt SingletonComponent 注入。当前 ViewModel 直接调用 DAO，属于基础版的简化，尚未扩展为每功能单独 repository/usecase。

生命周期 ON_START 注册采集，ON_STOP 注销回调、停止前台采样与测试。callbackFlow awaitClose 释放资源；重复启动先取消旧 job。可暂停采样；历史流仅订阅时收集。采样最多 300 条，事件最多 50 条，AP 不主动轮询扫描。Room 查询最近 200 条供显示和导出，删除会清除全部数据库记录。

DNS 使用 Android 29+ DnsResolver 和 CancellationSignal，5 秒超时；返回系统 A/AAAA 合并地址，不声称是指定 DNS 或 DoH。TCP 先解析目标，DNS 不计入 TCP 时间；Socket 单地址连接超时 1.2 秒，每个逻辑样本最多4个地址候选，共5个样本，样本间隔300ms。数值 IP 直接解析，不发 DNS 查询。取消直接关闭当前socket，阻止后续探测。网络 I/O 位于 IO dispatcher。选定默认网络变化时停止当前检测，不擅自切换网络继续传输。

RSSI 参考分在 −90..−50 dBm 线性映射至 0..100。评分函数预留 TCP 延迟、失败率、波动因素，权重 .55/.20/.15/.10，缺测项完全移除并重新归一化。目前 UI 仅使用 RSSI 并明确标注，未实现含实测吞吐的完整综合评分。

P95 使用 nearest rank；中位数偶数取中间均值；Jitter 为相邻成功探测绝对差均值，跨失败样本不连接；失败率包括超时、拒绝和 I/O 失败，不称为 ICMP 丢包。

拥挤指数：同频段 AP 的频宽矩形交集/候选宽度 × 10^((RSSI+90)/20) 求和；仅相对比较，无单位。当前图表为主频柱形分布，不是频谱分析仪。80+80、隐藏 AP、扫描缓存与实际流量均限制可信度。规则建议显示实际可见证据，完整持续阈值规则引擎未实现。

未来设备发现、安全审计、热力图与报告应继续通过独立接口加入；本版没有用假页面表示已实现这些能力。


0.2.0：新增 DownloadTester 接口、HttpsDownloadTester（OkHttp4.12）与纯Kotlin NetworkOverview 解释器。下载采用OkHttp后台执行器并通过suspendCancellableCoroutine对接协程取消，Wi-Fi socketFactory/DNS固定到同一Network。最多读取100,000,000字节，读取循环目标10秒（最多4个顺序请求，每次请求默认25MB），单次read超时2秒、协程整体超时18秒。计时从请求开始至读取结束，包含DNS/TCP/TLS等开销；Mbps=应用读取字节×8/总秒数/10^6。低于2秒或1MB的采样显示证据不足。该结果不替代完整专业带宽测试，也不与RSSI参考分混合。


0.3.0：ActiveNetworkProvider选择当前默认网络并在测试中检查是否变化。AndroidWifiSource虽保留类名，已读取Wi-Fi/移动/以太网/VPN默认链路；Wi-Fi敏感字段按权限和定位服务再次过滤。NetworkCallback仅在网络ID、传输、验证、门户或计费状态改变时产生事件，不记录每次RSSI引起的能力回调。HTTPS HEAD使用严格TLS与固定443，不跟随重定向；总响应时间不称为TCP耗时。TCP最多使用4个IPv4/IPv6地址候选，可取消关闭socket。
网关检测仅由明确授权入口调用，限制私有IPv4网关、3次ICMP和2个预定义端口、30秒间隔。系统ping直接ProcessBuilder参数调用，无shell拼接；5秒上限、取消销毁进程。GPS/系统定位通过GeoLocator接口/LocationManagerCompat前台单次获取，15秒超时，坐标年龄/模拟标记保留。Room v2→v3添加网络类型与可空定位字段，v1可顺序迁移到v3。导出继续只包括原快照，不包含定位表。
