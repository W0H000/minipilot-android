# System DNS during VPN capture

With **Use system DNS** enabled, live captures on Android 6 and newer select the
IPv4 DNS server from an underlying, non-VPN Internet network. This applies to
captures started in the app and through the control API; no new Intent extras or
MiniPilot configuration are required.

Previously, starting capture while Clash (or another VPN) was active could copy
that VPN's DNS, such as `172.19.0.2`. Android then stopped the old VPN, making the
copied resolver unreachable. In IPv4-only captures, this could leave target apps
without working DNS.

The selection now excludes VPN and IMS-only networks. It prefers the active
physical Internet network, then Android's best-matching non-VPN network
(monitored with the public Android 12+ API), then validated Ethernet/WiFi/cellular
candidates. Private addresses
from an actual network remain valid: a router's `192.168.x.x` DNS must not be
rejected just because it is private.

Network availability, capabilities and link-property changes trigger a new
selection and update the native IPv4 DNS forwarder when its resolver changes.
Monitoring continues through network loss so it can recover on a new connection.
The selection is logged with the network ID, interface and IPv4 DNS address.

If the selected network has no IPv4 DNS, the configured IPv4 DNS is used as a
fallback. Disabling **Use system DNS** continues to use the configured resolver.
IPv6 DNS and Android versions before 6 retain their existing configured behavior;
this change does not introduce a new IPv6 DNS forwarding mechanism or change IP
capture mode. It does not rewrite saved DNS preferences.

## Device verification

1. Enable **Use system DNS**. Use an IPv4-only capture to avoid a working IPv6
   resolver masking an IPv4 DNS failure.
2. Connect mobile data and start Clash's VPN. Start capture through MiniPilot.
3. Confirm `Underlying Internet network ... (ccmni...)` selects the carrier's
   actual IPv4 DNS, not the old VPN's `172.19.0.2` or PCAPdroid's `10.215.173.2`.
   The latter remains the normal virtual DNS advertised inside PCAPdroid's TUN.
4. Check that DNS replies and target-app networking work.
5. While capturing, switch mobile data to WiFi and back, then briefly disconnect
   and reconnect. Confirm resolver changes in logcat and new DNS requests succeed.
6. Disable **Use system DNS**, configure a custom IPv4 DNS and repeat a network
   switch. Confirm capture keeps the custom resolver.

Selection tests: `./gradlew testStandardDebugUnitTest --tests '*UnderlyingNetworkTest'`.
