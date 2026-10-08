# MediaShell

多源聚合阅读 / 影视 / 音乐壳子 APK。`com.aggregator.shell`，Android（minSdk 26 / target 35）。

- **壳子定位**：不内置任何内容源 / 弹幕 / LLM 凭据；所有外部端点由用户在「设置」配置，仅存本机。
- **三模块独立解析**：阅读（Legado）、影视（TVBox/T4/CatVod）、音乐（LX Music）。
- **影视**：点播 → 播放、短剧（竖屏上下滑全屏）、IPTV 直播 + EPG 节目单；视频挂弹幕。

## 模块

| 模块 | 说明 |
|------|------|
| `app` | 壳子入口，Home Tab 切换（影视 / 阅读 / 音乐 / 设置） |
| `core:common` | 通用工具、`ModuleType` |
| `core:data` | Room（订阅 / 各源 / EPG / 播放历史）+ DataStore + `appDataStore` |
| `core:source` | 引擎（`LegadoEngine` / `TvBoxEngine` / `LxMusicEngine`）+ `RuleParser` + `RhinoJsExecutor` |
| `core:media` | `PlayerCore`（Media3 ExoPlayer）+ 弹幕（`DanmakuSource`）+ EPG（`EpgParser`/`EpgProvider`） |
| `core:ui` | Compose 通用组件 |
| `core:ai` | 制源助手（离线启发式 / 云端 LLM） |
| `feature:video` / `reader` / `music` / `settings` | 各功能 Activity + ViewModel |

## 构建

```bash
# 环境
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk

# 编译 debug APK
/opt/gradle-8.9/bin/gradle :app:assembleDebug --console=plain

# 单测
/opt/gradle-8.9/bin/gradle :core:source:testDebugUnitTest --rerun-tasks
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（**不入 Git**，`.gitignore` 已排除）。

## 技术栈

Kotlin 2.0.21 · Compose BOM 2024.10.01 · Hilt 2.52（KSP）· Room 2.6.1 · Media3 1.4.1 · OkHttp 4.12.0 · Rhino 1.7.14 · Coil

## 版本

`versionName 1.5.1`（`versionCode 6`）。完整演进见 [CHANGELOG.md](./CHANGELOG.md)。

## 配置

源 / 弹幕 / LLM 端点配置见 [docs/SOURCE_CONFIG.md](./docs/SOURCE_CONFIG.md)。
