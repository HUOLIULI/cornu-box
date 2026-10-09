# Changelog

MediaShell（com.aggregator.shell）— 多源聚合阅读 / 影视 / 音乐壳子 APK。

## 1.6.0

### 新增
- AI 真实 LLM：`LlmDelegatingAssistant` 按本机 DataStore 配置动态切换云端 / 离线；
  `CloudLlmAssistant` 走 OpenAI 兼容 `/chat/completions`（OkHttp + `Dispatchers.IO`），
  未配置时回退离线规则助手；LLM 配置键抽到 `core:common`（`LlmConfigKeys`）跨模块复用
- 前台播放服务：`MediaPlaybackService`（`@AndroidEntryPoint`）只读播放状态更新通知，
  与 `PlayerCore` 解耦；视频 / 音乐进入播放启服务、退出停止（`EXTRA_STOP_FOREGROUND`）
- 「我的」聚合页：`feature:my` + `MyPageActivity` / `MyViewModel`，聚合收藏、播放历史、
  搜索历史、书架、EPG 五类 Room 数据；首页新增「我的」导航项
- 聚合搜索：`core:search`（`SearchAggregator` 并发检索 + 去重 + 记搜索历史）
- 三引擎接 Room 源表：`SourceProvider` / `RoomSourceProvider` + `FallbackSourceProvider`，
  订阅导入 → 引擎回流
- Room Migration(1→2)：新增 `favorites` / `search_history` 表；`MigrationTest` 覆盖
- 音乐播放页 `MusicActivity`（LRC 逐行高亮）+ `LrcParser`；阅读全链路 `ReaderViewModel`

### 优化 / 修复
- `PlayUrlValidator` 语义修正：未知 / 空 scheme 直接拒绝，仅放行 http/https/rtmp/rtsp/mms
- targetSdk 35 edge-to-edge：`MainActivity.enableEdgeToEdge()`
- `PlayerCore` release 幂等 + `switchUrl/pause/resume` 空守卫；播放器 release 竞态修复
- 前台服务停止改 `stopForeground(STOP_FOREGROUND_REMOVE) + stopSelf`
- 去阻塞：`CloudLlmAssistant` / `LlmDelegatingAssistant` 的 `runBlocking` 改 `withContext(Dispatchers.IO)`；
  LLM 输出剥离 ```json 围栏
- HiltViewModel 改 `@Inject constructor(... Application)` 构造注入（替换不可靠的 `@Inject lateinit var`）
- 代码质量复查：清理死代码 / 重复 import、`!!` 改安全调用、搜索历史独立 `SearchRow`、
  EPG channelId 用频道名、修正短剧方向切换 `DisposableEffect` 语义
- R8 keep 补全 Media3（exoplayer.*/session.*）
- 发布签名：release build 支持 `keystore.properties`，缺失回退 debug keystore，产出可安装包

## 1.5.1

- 单测对齐 RuleParser v1.1.1 严格 JSONPath 语义（数组通配 `[*]`、字段需 `$.` 前缀）
- 归档整理：移除过期 `修复说明_v1.1.md` / `SPEC_V1.1_FIXES.md`，文档收敛到
  `README` + `CHANGELOG` + `docs/SOURCE_CONFIG.md` + `.monkeycode/docs/`

## 1.5.0

- 弹幕源配置化：新增 RemoteDanmakuSource，按「设置 → 弹幕源」本机 DataStore 配置
  的真实弹幕 API 拉取弹幕；未配置/失败自动回退 LocalDanmakuSource 演示弹幕
- 设置页新增「弹幕源」配置区（Base URL + 可选 API Key，仅保存在本机）
- 版本升至 v1.5.0

## 1.4.0

- EPG 真实源：EpgProvider 优先走 EpgParser 拉取 XMLTV（明文/gzip），结果经 EpgDao
  落 Room（clear/upsert），不可达回退内置演示；新增 EpgSnapshot（正在播/即将播）
- 短剧全屏：VerticalVideo 占位 9:16 区改挂 PlayerSurface（真实 ExoPlayer 全屏渲染 + 弹幕）
- core:media 补 implementation(core:data)；MediaModule 注入 EpgDao

## 1.3.0

- 短剧改 VerticalPager 竖滑 + 进 Tab 切 PORTRAIT 竖屏
- 新增 EpgProvider（演示 EPG）与 EpgPanel；LocalDanmakuSource 演示弹幕

## 1.2.0

- 点播→播放链路：VideoViewModel（列表流 + onItemClicked/switchEpisode/exitPlayback）
- 落地短剧（竖屏上下滑）与 IPTV 直播列表

## 1.1.3

- 修 RuleParser.regex 对齐 Legado（有捕获组取组 1、无组取整段 + regexAll 多值）
- ExoPlayerCore 重试由 GlobalScope 改受管协程 + release() cancel()
- 弹幕循环加 Job.isActive 存活检查；LxMusicEngine 接通 JsSandboxExecutor 注入 lx 桥

## 1.1.2

- CoreDataModule 补全 9 个 Room DAO @Provides（修 Hilt MissingBinding 首启崩溃）
- SettingsActivity 的 appDataStore import 改 .data.di

## 1.1.1

- 引擎重写：LegadoEngine / TvBoxEngine / LxMusicEngine 内置 demo，example.com 不可达
  回退本地演示数据；动态选源由 Repositories 承担

## 1.1.0

- 三模块（阅读 Legado / 影视 TVBox / 音乐 LX）独立解析，首页 Tab 切换

## 1.0.0 / 1.0.1

- 壳子 APK 骨架：Compose + Hilt + Room + Media3，ABI v7a + arm64
