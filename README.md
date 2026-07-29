# MXboxS

基于 [FongMi/TV](https://github.com/FongMi/TV) 的二次开发项目，专注于 Android 手机端影视应用。

---

## 分支说明

| 分支 | 用途 | 说明 |
|------|------|------|
| `main` | 二次开发主分支 | 活跃开发，所有改动在此提交 |
| `TV` | 原始源码（不动） | FongMi/TV 纯净源码，作为开发环境参考 |
| `mobile` | 手机端专用 | 仅保留 mobile 代码，已移除 leanback 电视端代码 |
| `KF` | KF 分支 | 完整代码副本 |

---

## 项目架构

| 项目 | 值 |
|------|-----|
| 包名 | `com.fongmi.android.tv` |
| 最低 SDK | 24（Android 7.0） |
| 架构 | `arm64-v8a`、`armeabi-v7a` |
| 构建变体 | `leanback`（电视版）、`mobile`（手机版） |

```
├── app/
│   ├── src/main/      共享业务逻辑
│   ├── src/mobile/    手机端 UI
│   └── src/leanback/  电视端 UI（mobile 分支已移除）
├── catvod/            爬虫抽象层
├── quickjs/           QuickJS JavaScript 引擎
├── chaquo/            Chaquopy Python 引擎
├── thunder/           迅雷下载引擎
├── tvbus/             TVBus 直播引擎
├── forcetech/         ForceTech 直播引擎
├── zlive/             ZLive 引擎
├── jianpian/          简片引擎
├── hook/              Chromium WebView Hook
└── docs/              配置文档
```

---

## 文档

| 文件 | 说明 |
|------|------|
| [MOBILE_APP_ANALYSIS.md](MOBILE_APP_ANALYSIS.md) | Mobile App 代码分析（图标、页面、调用方式） |
| [docs/CONFIG.md](docs/CONFIG.md) | Vod / Live 配置说明 |
| [docs/SPIDER.md](docs/SPIDER.md) | Spider 爬虫接口规范 |
| [docs/LOCAL.md](docs/LOCAL.md) | 本地 HTTP API 说明 |
| [docs/LIVE.md](docs/LIVE.md) | 直播源格式说明 |

---

## 功能特性

### 播放器
- 核心：ExoPlayer (Media3) + FFmpeg 软解
- 渲染：SurfaceView / TextureView
- DRM：Widevine、PlayReady、ClearKey
- 弹幕：DanmakuFlameMaster，与时间轴同步
- 字幕：SRT / SSA / ASS 外挂字幕
- 画中画（PiP）、倍速播放、背景音频

### 点播
- 多站点分类浏览，Filter 筛选
- 多站点并行搜索
- 播放失败自动换源
- 观看记录、收藏、无痕模式
- 手势控制（亮度/音量/进度）

### 直播
- 支持 M3U、TXT、JSON 格式直播源
- EPG 节目单（XMLTV，支持 .gz）
- 频道收藏、密码保护

### 爬虫引擎
- Java JAR（DexClassLoader）
- JavaScript（QuickJS）
- Python（Chaquopy）

### 网络
- DoH（DNS over HTTPS）
- HTTP / HTTPS / SOCKS 代理
- 广告拦截
- WebView 嗅探

---

## 原始项目

- [FongMi/TV](https://github.com/FongMi/TV) — 原始开源项目
- [讨论群组](https://t.me/fongmi_official)
- [发布频道](https://t.me/fongmi_release)