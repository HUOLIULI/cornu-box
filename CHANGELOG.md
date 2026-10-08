# MediaShell 更新日志

## v1.0.1 (2026-10-08)

修复 APK 安装闪退，补全核心功能链路。

### 修复
- **闪退根因**：`CoreDataModule` 未提供 `VideoSourceDao` / `LiveSourceDao` / `BookSourceDao` / `MusicSourceDao` / `SourceLogDao` / `BookshelfDao` / `EpgDao` / `PlayHistoryDao`，`@AndroidEntryPoint` Activity 创建时 Hilt 注入 `@Inject` 构造器中的 DAO 抛 `DaggerError`，首启即崩。已在 `CoreDataModule` 补全全部 DAO 的 `@Provides`。
- `MediaShellApp` 加全局 `Thread.UncaughtExceptionHandler`，崩溃写 `filesDir/crash-*.log`，便于后续定位。

### 补全功能
- **多源订阅去重**：`SubscriptionManagerImpl.update` 实装按 `api`/`url` 哈希稳定 ID 去重合并；`SubscriptionDao.byId` 查询；`listSubscriptions` 接口。
- **EPG 时间戳解析**：`EpgParser.parseTs` 解析 XMLTV `YYYYMMDDHHmmSS +TZ` 格式，支持 `+HH:MM` 时区偏移与 `Z`（UTC）。
- **AI 制源入口**：`SettingsActivity` 接入 `SubscriptionManager`（订阅/去重）+ `AiSourceAssistant`（生成候选源规则），离线启发式兜底。
- **短剧滑切**：`VideoActivity` Tab 切到「短剧」时走 `ShortDramaSwipe`（`LazyRow` 横滑 + 9:16 竖屏卡片占位），点播/IPTV 走纵向 `LazyColumn`。

### 技术栈（不变）
- Kotlin 2.0.21 · Compose BOM 2024.10.01 · Hilt 2.52 (KSP 2.0.21-1.0.28)
- Room 2.6.1 · Media3 1.4.1 · OkHttp 4.12.0 · Moshi 1.15.1 · Jsoup 1.18.1
- JsonPath 2.9.0 · Rhino 1.7.14 · Coil 2.7.0 · Kotest 5.9.1

### 构建与验证
- `gradle :app:assembleDebug` 通过，产物 `app-debug.apk`（24M，armeabi-v7a + arm64-v8a）
- `:core:source:testDebugUnitTest` 3/3 通过（JSONPath / 正则 / JS 超时）
- 全模块 `compileDebugKotlin` 通过

## v1.0.0 (2026-10-07)

首个版本，多源聚合壳子 APK。

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
