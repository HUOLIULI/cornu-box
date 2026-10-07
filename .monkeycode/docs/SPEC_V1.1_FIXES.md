# v1.0.0 开发文档缺口补全说明（v1.1.0）

针对评审版 v1.0.0 的落地修正，逐项说明：

## 已修正的缺口

1. **引擎接口过于泛化**
   - v1.0.0：`SourceEngine<T>` 一个接口含 `search / getDetail / getChapters / getContent / getPlayUrl`。音乐无章节、影视无 getChapters、阅读无 getPlayUrl。
   - v1.1.0：拆为 `ReaderEngine / VideoEngine / MusicEngine` 三个能力接口；`ModuleType.LIVE` 单独承载 IPTV。

2. **引擎注册表与动态选源脱节**
   - v1.0.0：`SourceEngineRegistry.resolve(module)` 返回单例引擎，但引擎内部不知道"当前选中哪个源"。
   - v1.1.0：引擎无状态（每次调用读取 Repository 中的启用源列表）；动态选源由 `VideoSourceRepo / ReaderSourceRepo / MusicSourceRepo` 承担，引擎只做解析。

3. **native 依赖未做构建降级**
   - v1.0.0：默认 QuickJS + Chaquopy + IJKPlayer + DanmakuFlameMaster 同时启用。
   - v1.1.0：默认构建只用纯 JVM 依赖（Rhino、Media3、Compose Canvas 弹幕、Jsoup、JsonPath）。QuickJS/Chaquopy/IJKPlayer 通过 `gradle.properties` 开关按需启用。

4. **`ModuleType` 与 `SourceConfig` 未定义**
   - v1.1.0：`ModuleType` 在 `core:common` 定义；`SourceConfig` 及其 4 个子类（`VideoSourceConfig / LiveSourceConfig / BookSourceConfig / MusicSourceConfig`）在 `core:source.model`。

5. **AI 助手 LLM Key 注入方式未声明**
   - v1.1.0：设置界面提供 LLM Base URL + API Key 输入框，值写入 DataStore；应用绝不读取构建环境变量中的大模型 Key。`HeuristicAssistant` 作为无网络兜底。

6. **数据表只列名未建模**
   - v1.1.0：10 个 Room 实体 + 9 个 DAO 已建模，`exportSchema=true`。

7. **ProGuard 包路径错误**
   - v1.0.0 写 `com.app.source.engine.**`；实际包名 `com.aggregator.shell.*`，v1.1.0 已修正 `app/proguard-rules.pro`。

## 未在本 MVP 中实现（记录备查）
- 短剧竖屏上下滑切集、IPTV 频道 EPG 批量预加载（接口已预留，UI 占位）
- 多源去重合并的具体实现（订阅管理器接口已建，去重逻辑 TODO）
- 本地书源仓库规范（`repository.json` 扁平结构）解析
- IJKPlayer / DanmakuFlameMaster / Chaquopy 的启用开关与实现

## 构建与验证状态（v1.1.0 落地结果）

- `gradle :app:assembleDebug` 通过，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 24M，armeabi-v7a + arm64-v8a 双 ABI 自动合并）
- `gradle :core:source:testDebugUnitTest` 3/3 通过（JSONPath / 正则 / JS 超时）
- 单测修复：`org.json` 在 JVM 单测为 Android stub，已加 `testImplementation libs.org.json` 提供真实实现
- Hilt 修复：`AiModule` 用 `@Provides` 提供 `AiSourceAssistant`；`core:ai` 补 Hilt/KSP 插件；`app` 显式依赖 `:core:ai`、`:core:ui`
- Compose 修复：`MainActivity` 底部 `NavigationBarItem` 必须处于 `RowScope` 接收者内（拆出 `RowScope.navBarEntry`）；`PlayerSurface` 弹幕改用 `Canvas.nativeCanvas.drawText`，避免 `drawText` API 版本歧义；`density` 从 `LocalDensity` 取值
- `gradle/libs.versions.toml` 新增 `org-json = "20240303"`；`app` 引入 `material-icons-extended`

## 文件索引（本轮落地）

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
local.properties
app/  (MediaShellApp, MainActivity, Manifest, proguard, strings, VideoActivity 桥)
core/common  (AppException, ModuleType)
core/data    (Room entities/DAOs/DB, DataStore, OkHttp, 源仓库, 订阅管理, Hilt)
core/source  (SourceApi, RuleParser, Sandbox, Engines, Hilt, Tests)
core/media   (PlayerCore, PlayUrlValidator, Danmaku, EpgParser, Hilt)
core/ai      (AiAssistant, HeuristicAssistant)
core/ui      (AppTheme)
feature/video (VideoActivity, PlayerSurface)
feature/reader (ReaderActivity)
feature/music (MusicActivity)
feature/settings (SettingsActivity)
.monkeycode/docs/ (INDEX, ARCHITECTURE, INTERFACES, DEVELOPER_GUIDE, SPEC_V1.1_FIXES)
```
