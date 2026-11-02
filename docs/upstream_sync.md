# Upstream v2.0.2 alignment

This fork merges the official [v2.0.2 release](https://github.com/emanuele-f/PCAPdroid/releases/tag/v2.0.2)
(`76696f705b3780f9f0951f25bb93b2708f70b98a`) into the existing MiniPilot changes
based on v1.9.1. Changes after that tag on upstream master are not included.

## Features added since v1.9.1

- Capture List shows earlier captures and their sizes on disk.
- App isolation, port mapping exemptions and a configurable connections log limit.
- Settings export/import, with validation and warnings for sensitive imported settings.
- UDP/TCP collector hostnames, while keeping the legacy `collector_ip_address` Intent alias.
- Android 17 support, improved PCAP/Pcapng loading/decryption, and more translations.
- Updated MITM addon integration (v2.4), mitmproxy 12.2.3 and Wireshark 4.7.1 integration.
- Fixes for full-payload PCAP memory usage, leaked connection listeners, HAR binary
  bodies, root firewall/always-on VPN and Intent capture startup.

The full upstream release history is in [CHANGELOG.md](../CHANGELOG.md).

## MiniPilot features retained

- Configurable VPN MTU (1280–10000, default 1500), including inline custom input,
  session-local Intent overrides and a shared MTU for the Android TUN and native stack.
- API-key preflight receiver and the same authentication check for capture control.
- [Underlying-network IPv4 DNS selection](system_dns.md), excluding an old VPN's
  resolver and following mobile/WiFi network changes without rewriting preferences.

The new backup schema includes the custom MTU. Invalid imported MTUs are skipped,
so a restored backup uses the default MTU instead. API keys remain excluded from settings
export/import, following upstream's policy.

## Compatibility and building

PCAPdroid now requires Android 6 (API 23). TLS decryption through the MITM addon
requires Android 7 (API 24); that addon drops x86 and armeabi-v7a support.
The addon is a separate installation; updating this fork does not install it.
Android 17 adds the local-network runtime permission requested by upstream capture startup.

The upstream build uses SDK 37, Gradle 9.3.1, Android Gradle Plugin 9.1.1,
NDK 28.2.13676358 and CMake 3.22.1. Use Java 21, matching upstream CI and the
Android 16 Robolectric tests. Robolectric currently runs SDK 36 because it does
not yet support SDK 37.

```sh
git submodule update --init --recursive
./gradlew test
./gradlew assembleStandardDebug
```

The debug package remains `com.emanuelef.remote_capture.debug`, allowing it to
coexist with the release package. A debug upgrade can retain settings only when
signed with the same debug certificate.
