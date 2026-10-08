# Changelog

MediaShell（com.aggregator.shell）— 多源聚合阅读 / 影视 / 音乐壳子 APK。

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
