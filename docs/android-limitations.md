# Android 与硬件限制

- 无 Root、隐藏 API、驱动私有指标；没有噪声底噪、精确空口占用、真实设备 MAC 或物理拓扑推断。
- 当前读取使用 WifiInfo/NetworkCapabilities/LinkProperties，设备厂商与权限脱敏会影响可见值。API29 保留 connectionInfo 兼容路径，后续 Android 应迁移至带位置信息标志的回调。
- Wi-Fi 链路速率不是互联网速度。INTERNET capability 表示网络声明可上网，VALIDATED 才是系统验证结果，也不保证所有网站可访问。
- 当前安全 API 的 PSK 不能准确区分 WPA/WPA2；本版如实标为 Personal PSK（WPA/WPA2，API 未区分）。不根据 SSID 猜加密。
- IPv4/IPv6 地址和 DNS 都列出；自动网关探测当前仅选择 IPv4 默认网关，IPv6-only 显示无法获取。
- MLO 仅尝试读取 AP link ID，不表示所有关联链路或射频能力均可获得。DHCP 租期、完整能力集合尚未提供。
- TCP 拒绝不意味着设备离线；成功建连延迟受目标服务影响。系统ICMP仅在设备的/system/bin/ping可执行时对已授权私有网关尝试；未响应不等于故障。没有traceroute或TLS/HTTP分阶段计时。0.2.0 提供单连接 HTTPS 下载参考测速，尚无上传或多连接吞吐测量。
- DNS 为系统 Wi-Fi 绑定解析，Private DNS 状态可见不代表应用已经验证 DNS 加密；不做 DNS 劫持结论。
- 模拟器的 AndroidWifi 等结果来自虚拟网络。真实手机权限、Wi-Fi关闭、移动网络、断网、限频、API37局域网受限仍需验收矩阵。
- NURO、Buffalo 路由器无专属 API 依赖，不识别固件/WPS/UPnP/远程管理配置。


0.3.0：通用检测选择ConnectivityManager.activeNetwork，而不是固定寻找Wi-Fi。VPN/以太网/移动网络的具体能力由Android和运营商决定；不声称所有网络、所有指标都可用。Wi-FiRSSI不适用于蜂窝网络；未实现蜂窝RSRP/RSRQ/SINR或基站定位。VPN默认网络可能拒绝绑定套接字，应用显示失败且不主动改走底层网络。IPv6域名TCP测试会按系统返回地址尝试最多4个候选，不只测试第一个地址。网关仍只支持已授权私有IPv4路线。
系统定位仅前台请求，可能因室内/无提供者/权限不足失败；拒绝过期结果，不虚构经纬度，不借GPS推断房间。若系统将位置报告为模拟位置，则明确显示该标记；模拟器坐标本身不能代表真实定位。
