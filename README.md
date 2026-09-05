<h1 align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="RPDev Feed Icon" width="128" height="128"/>
  <br>
  RPDev Feed
</h1>

<p align="center"><strong>Modern Privacy-First Context Hub &amp; Discover Feed Companion for Android Launchers</strong></p>

<div align="center">

[![Project Wiki](https://img.shields.io/badge/Wiki-wiki.iamrp.dev-6366f1?style=flat&logo=bookstack&logoColor=white)](https://wiki.iamrp.dev/projects/rpdev-feed)
[![Documentation](https://img.shields.io/badge/Docs-feed.launcher.iamrp.dev-0ea5e9?style=flat&logo=gitbook&logoColor=white)](https://feed.launcher.iamrp.dev)
[![Module Catalog](https://img.shields.io/badge/Catalog-repo.launcher.iamrp.dev-10b981?style=flat&logo=buffer&logoColor=white)](https://repo.launcher.iamrp.dev)
[![Latest Release](https://img.shields.io/github/v/release/RPDevs-Builds/RPDev-Feed?style=flat&labelColor=1a1a2e&color=4e54c8)](https://github.com/RPDevs-Builds/RPDev-Feed/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/RPDevs-Builds/RPDev-Feed/android.yml?style=flat&labelColor=1a1a2e&color=4e54c8)](https://github.com/RPDevs-Builds/RPDev-Feed/actions/workflows/android.yml)
[![GitHub License](https://img.shields.io/github/license/RPDevs-Builds/RPDev-Feed?style=flat&labelColor=1a1a2e&color=4e54c8)](https://github.com/RPDevs-Builds/RPDev-Feed/blob/main/LICENSE)
[![GitHub Downloads](https://img.shields.io/github/downloads/RPDevs-Builds/RPDev-Feed/total.svg?style=flat&labelColor=1a1a2e&color=4e54c8)](https://github.com/RPDevs-Builds/RPDev-Feed/releases/)

</div>

---

## 📖 Overview

**RPDev Feed** is a privacy-first, lightweight companion feed and context hub engine for **RPDev Launcher**, Lawnchair, and other modern Android launchers. It decouples resource-intensive context intelligence (weather, agenda, sensors, push hooks, and news) from the core launcher, keeping the home screen ultra-fast.

---

## ✨ Key Features

### 🌦️ Zero-Telemetry Privacy Weather (Open-Meteo)
- **Zero API Keys Required**: Fetches live temperature, weather condition codes (WMO), apparent temperature, humidity, wind speeds, and hourly forecast tracks directly from Open-Meteo.
- **Privacy-First**: No location tracking or telemetry sent to third parties.

### 📅 On-Device Calendar Agenda Engine
- **Next 24-Hour Agenda**: Queries local Android `CalendarContract` provider to display upcoming meetings, locations, and time chips.
- **One-Tap Event Action**: Tap any event card to open your device calendar app directly.

### 🔋 Real-Time Hardware & Battery Telemetry
- **Battery Health & Charge Power**: Live wattage, charging state, battery voltage, temperature, and current flow.
- **System Memory & Storage Gauges**: Real-time internal storage utilization and available RAM pressure indicators.

### 📡 Broadcast Push & Custom REST Ingestion
- **Live Broadcast Receiver**: Send intents (`iamrp.dev.feed.ACTION_POST_CARD`) from Tasker, Termux, or Automate to inject cards dynamically into your feed overlay.
- **Custom REST Polling**: Integrate Home Assistant, Uptime Kuma, and Gotify alerts into custom feed cards.

### 🔌 Modular Plugin System & GitHub Pulse
- **`HubPlugin` SPI Engine**: Extensible plugin architecture with decoupled lifecycle hooks, custom preference sheets, and card generators.
- **GitHub Pulse Module**: Real-time GitHub commit feeds, workflow dispatch status indicators, star tracking, and repository health metrics.
- **Card Archetypes**: Standardized Material 3 card renderers (Metric, Timeline, Progress, Action, Composite).
- **Plugin Manager UI**: Toggle, customize, and arrange active plugins on the fly.

### 📰 RSS, Atom & JSON Feed Reader
- **Multi-Format Ingestion**: Ingest RSS, Atom, and JSON Feed v1.1 channels with offline caching and read-it-later bookmarking.
- **Adaptive Layouts**: Full support for phones, foldable tablets, and landscape desktop mode.

---

## 📱 Screenshots (Android 16 - DevPixel16)

| <img src="docs/screenshots/feed_overlay_devpixel16.png" alt="Live Feed Overlay" width="280"/> | <img src="docs/screenshots/feed_main_devpixel16.png" alt="Standalone Feed App" width="280"/> | <img src="docs/screenshots/feed_plugins_devpixel16.png" alt="Hub Plugins Manager" width="280"/> |
|:---:|:---:|:---:|
| **Live -1 Screen Overlay** | **Feed App &amp; Radar View** | **Hub Plugins Manager** |

| <img src="docs/screenshots/feed_catalog_devpixel16.png" alt="Module Catalog" width="280"/> | <img src="docs/screenshots/feed_datasources_devpixel16.png" alt="Data Sources" width="280"/> | <img src="docs/screenshots/feed_settings_devpixel16.png" alt="Display &amp; Layout Settings" width="280"/> |
|:---:|:---:|:---:|
| **Module Repository Catalog** | **Data Sources &amp; Channels** | **Feed Display &amp; Settings** |

---

## 📱 Supported Launchers

- **RPDev Launcher (Recommended)**
- Neo Launcher
- Lawnchair
- Shade Launcher / Smart Launcher (via Feed Bridge)

## Community :speech_balloon:

You can join either our [Telegram](https://t.me/neo_launcher) or [Matrix](https://matrix.to/#/#neo-launcher:matrix.org) groups to make suggestions, ask questions, receive news, install test builds, or just chat.

<p align="center">
<a href="https://t.me/neo_launcher"><img src="https://upload.wikimedia.org/wikipedia/commons/8/82/Telegram_logo.svg" alt="Join Telegram Channel" width="11%" align="center"></a>
<a href="https://matrix.to/#/#neo-launcher:matrix.org"><img src="https://docs.cloudron.io/img/element-logo.png" alt="Join Matrix Channel" width="11%" align="center" /></a>
</p>

## Translation :left_speech_bubble: 
[<img align="right" src="https://hosted.weblate.org/widgets/neo-feed/-/287x66-white.png" alt="Translation stats" width="40%" />](https://hosted.weblate.org/engage/neo-feed/?utm_source=widget)

Contribute your translations to Neo Feed on [Hosted Weblate](https://hosted.weblate.org/engage/neo-feed/). <br> Adding new languages is always accepted and supported.

[![Translation stats](https://hosted.weblate.org/widgets/neo-feed/-/multi-auto.svg)](https://hosted.weblate.org/engage/neo-feed/?utm_source=widget)

## Special Thanks :heart:

[iTaysonLab](https://github.com/iTaysonLab) as the project is a fork of his HomeFeeder.

[DrawerOverlayService](https://github.com/FabianTerhorst/DrawerOverlayService) as base for overlay service.

[Helena Zheng](https://helenazhang.com/) & [Tobias Fried](https://tobiasfried.com/) for the great [Phosphor Icons](https://phosphoricons.com/), we gladly use.

### Contributors :handshake:

<a href="https://github.com/NeoApplications/Neo-Feed/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=NeoApplications/Neo-Feed"  alt="Icons of contributors to Neo Store"/>
</a>

## Copylefted Libre License :scroll:

Licensed under the [GPLv3+](/LICENSE).

Copyright © 2025 [Saul Henriquez](https://github.com/machiav3lli) & [Antonios Hazim](https://github.com/machiav3lli)

![Star History Chart](https://api.star-history.com/svg?repos=NeoApplications/Neo-Feed&type=Date)
