# MediaShell 更新日志

## v1.1.0 (2026-10-08)

代码逻辑梳理 + UI 全方位完善，引擎接真实数据通路，三 feature 页面统一体验。

### 引擎逻辑修复
- **LegadoEngine 列表提取**：原 `search` 死循环生成 4 条相同行、`getToc` 死循环 50 条相同章节。改为 `RuleParser.list` 按 `list` 规则提取数组各元素，逐元素套字段规则，去重彻底。
- **TvBoxEngine 列表解析**：原 `search` 把整个响应当单对象只读 1 条。改为 `parseVideoList` 识别 `list` / `videoList` / `data` 多种包装，逐条映射 `vod_*` 字段；`parseEpisodes` 同时支持 `eps[]` 与 `episodes{}` 两种集数结构。
- **LxMusicEngine 去硬编码**：原 `search`/`getMusicUrl`/`getLyric` 全部写死演示数据。改为经 Rhino 沙箱执行用户导入的 LX 源脚本，失败/超时无缝回退内置 demo。
- **EpgParser 单行标题**：原状态机对 `<title>foo</title>` 单行写法丢失内容。新增「同行开闭」预读分支，单行/多行均正确累积。

### 数据通路接通 Room
- 三引擎经 `SourceModule` 注入对应 Room DAO（`BookSourceDao` / `VideoSourceDao`+`LiveSourceDao` / `MusicSourceDao`），运行时优先读用户导入源表，空表回退内置 demo，无循环依赖（组合式注入）。
- `TvBoxEngine.resolveLiveUrls` 读 `live_sources` 表取 IPTV 地址。

### 日志体系
- 新增 `AppLog` 抽象 + `NoOpLog`（core:common），引擎在「源解析失败 / 空结果 / 异常吞没」处按级（i/w/e）记诊断，便于线上定位而不强依赖 `android.util.Log`。
- `RuleParser` 新增 JSON 字段直取快速路径：数组元素是 JSON 对象时，裸字段名（`id`/`title`）直接命中，避免误走 CSS 解析。

### UI 全方位升级
- **影视**：顶部搜索框（输入 + 回车/按钮触发）；点播/短剧/IPTV 三 Tab；卡片加封面图（Coil `AsyncImage`，9:16 短剧 + 横版列表）；加载/空态/错误重试三态齐备。
- **阅读**：搜索框 + 书籍卡片（方形封面 + 作者 + 源名）；空态提示导入书源；错误重试。
- **音乐**：搜索框 + 歌曲卡片（圆形封面 + 歌手/专辑）；空态/重试。
- **设置**：`Scaffold` 顶部返回导航 + 模块三段选择器（影视/阅读/音乐）；「现有订阅」列表卡片支持删除；AI 制源面板；底部留白统一。
- **导航**：三个 feature Activity 顶部加 `ArrowBack` 返回图标，统一 Material3 图标/间距/圆角（14dp 卡片）。

### 构建与验证
- 全模块 `compileDebugKotlin` + `:app:assembleDebug` 通过
- `:core:source:testDebugUnitTest` 4/4 通过（JSONPath / 列表提取 / 正则 / JS 超时）

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
