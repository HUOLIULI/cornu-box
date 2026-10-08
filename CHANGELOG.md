# MediaShell 更新日志

## v1.0.0 (2026-10-07)

首个版本，多源聚合壳子 APK。

### 模块结构
- 10 个 Gradle 模块：`app` / `core(common, data, source, media, ai, ui)` / `feature(video, reader, music, settings)`
- 引擎拆分：`ReaderEngine` / `VideoEngine` / `MusicEngine`，无状态动态选源
- 纯 JVM 依赖（Rhino / Media3 / Jsoup / JsonPath），QuickJS 与 Chaquopy 经 `gradle.properties` 开关

### 核心能力
- 阅读：JSON 源（Legado 风格）+ CSS / 正则 / JS 规则解析
- 影视：点播 / 短剧 / IPTV 三 Tab，弹幕（Compose Canvas），EPG 占位
- 音乐：搜索 + 播放
- 设置：接口管理（源订阅 / 本地导入）、AI 制源助手配置
- AI 制源助手：离线启发式兜底（`HeuristicAssistant`），LLM 配置经用户自行填入，应用不读取构建环境 Key
- 数据层：10 个 Room 实体 + 9 个 DAO，Hilt 提供引擎、AI 助手与数据仓库

### 构建与测试
- `gradle :app:assembleDebug` 通过，产物 `app-debug.apk`（24M，armeabi-v7a + arm64-v8a）
- `:core:source:testDebugUnitTest` 3/3 通过（JSONPath / 正则 / JS 超时）

### 技术栈
- Kotlin 2.0.21 · Compose BOM 2024.10.01 · Hilt 2.52 (KSP 2.0.21-1.0.28)
- Room 2.6.1 · Media3 1.4.1 · OkHttp 4.12.0 · Moshi 1.15.1 · Jsoup 1.18.1
- JsonPath 2.9.0 · Rhino 1.7.14 · Coil 2.7.0 · Kotest 5.9.1

### 已知限制
- IPTV EPG 时间戳解析（`EpgParser.parseTs`）未实装，默认返回 0
- 短剧竖屏上下滑切集 UI 未实装，接口已预留
- 多源去重合并逻辑 TODO（订阅管理器接口已建）
- IJKPlayer / DanmakuFlameMaster / Chaquopy 未启用（经 `gradle.properties` 开关按需开启）
