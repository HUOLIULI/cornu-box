# 功能聚合分析（DsPlayer / PeekPro / Legado）

本次把 3 个 APK 的能力吸收到 MediaShell 壳子架构里。三者定位：

| APK | 包名 | 技术栈 | 核心能力 |
|-----|------|--------|----------|
| DsPlayer 0.8.5 | `com.dsplayer.ds_player` | Flutter | 视频播放 + 后台播放 + **源脚本引擎（Python/QJS 插件）** |
| PeekPro 1.5.8 | `com.example.peekpro` | Flutter + Media3 + FFmpeg | 短剧/影视聚合 + **T4 代理** + **桌面歌词** + ISO/HDR/DASH/HLS 多解码 |
| Legado 阅读 3.2608 | `com.legado.app.release` | 原生 + Rhino | 书源/搜索/书架/**听书(TTS)/RSS/自动化/漫画/二维码** |

## 与 MediaShell 现状的对齐

MediaShell 已有：三引擎（Legado / TVBox / LX Music）+ Room 源表 + `RhinoJsExecutor`（JS 沙盒）
+ `PlayerCore`（Media3 ExoPlayer）+ `MediaPlaybackService`（后台播放前台服务）+ 弹幕 + EPG
+ `LrcParser`（LRC 歌词）+ 可配置云端 LLM（`LlmDelegatingAssistant`）。

## 聚合落点（本次新增）

1. **单文件书源脚本（对标 3 者的"源脚本引擎"）**
   - LegadoEngine 支持 `mainJs` / `format=js` 单文件书源：Rhino 沙盒内执行脚本，
     脚本经 `http` 桥发起请求、返回书籍 JSON；执行受指令上限与超时保护。
   - 与 LxMusic 的 `lx` 桥一致，复用同一套 `JsSandboxExecutor`，不新增 native。

2. **TTS 听书（对标 Legado `TTSReadAloudService` / `AudioPlayService`）**
   - `core:ai` 新增 `TtsAssistant` + `CloudTtsAssistant`（OpenAI 兼容 `audio/speech`），
     `TtsDelegatingAssistant` 按 DataStore 动态切云端 / 离线；未配置回退离线空实现。
   - `TtsConfigKeys` 抽到 `core:common`，与 `LlmConfigKeys` 同款；端点仅存本机。
   - 阅读模块 `ReaderViewModel.speak()` 把当前章节送 TTS，经 `PlayerCore` 播放，
     `ReaderActivity` 加「TTS 朗读」按钮。

## 未吸纳 / 说明

- **T4 代理 / JAR 爬虫隔离**：涉及爬虫与代理转发，超出壳子范围，未实现。
- **ISO 解码 / HDR 渲染 / 桌面歌词之外的 PeekPro 解码矩阵**：ISO/HDR 依赖 native（FFmpeg/MDK），
  MediaShell 为纯 Kotlin 壳子（ABI 仅依赖 androidx 自带 `.so`），未对标。
  **桌面歌词浮层已吸纳**（见下方新增）。
- **RSS / 漫画 / 自动化**：Legado 专有重模块，非本次壳子聚合目标。

## 桌面歌词浮层（吸纳 PeekPro `DesktopLyricsService`）

- `core:media` 新增 `LyricsOverlayService`：`WindowManager` 桌面悬浮歌词，
  随 `PlayerCore.positionMs` + `LrcParser.lineAt` 逐行刷新；需 `SYSTEM_ALERT_WINDOW`
  权限（已加 manifest + `dataSync` foregroundServiceType），未授权时静默降级。
- `MusicViewModel.toggleDesktopLyrics()`：开关浮层，同进程经 `LyricsOverlayService.instance`
  直推当前行；`MusicActivity` 播放页加「桌面歌词」开关芯片。

## UI 布局优化

- `MainActivity` 各 Tab 由「空壳 Card + 进入按钮」升级为「能力 Hero + 能力芯片网格」：
  每模块顶部展示对标定位（点播/短剧/IPTV/桌面歌词/TTS 等），下方以 `AssistChip` 列出
  该模块吸纳的具体能力，替代原先无信息量的 BridgePanel。

所有新增保持"壳子"约束：不内置任何源 / TTS 凭据，端点与 Key 仅存本机 DataStore。

## 完全吸纳（本轮追加）

用户确认「完全吸纳、UI 全面改 PeekPro 黑金风格」后，补齐 4 项能力（纯 Kotlin 可实现部分全移植，
不可移植项按壳子硬边界说明不纳入）：

### 1. 影视播放韧性（对标 PeekPro 播放层）
- `core:media` 新增 `PlaybackResilience`（直播窗口识别 / 网页误返 `looksLikeHtml` / DASH 回退判定）、
  `SslBypassClient`（证书不受信源，纯 JVM SSL，非 native）、`LineRetryPolicy`（指数退避，纯逻辑可单测）。
- `PlayerCore`：按线路退避重试（直播窗口 800ms 快回退）；HTTP 数据源注入自定义请求头
  （Referer/UA/Cookie，`DefaultHttpDataSource`）；新增 `playbackEnded` 事件与 `sessionPlayer()`。
- `VideoViewModel.fallbackToNextLine()`：播放失败自动回退其它线路同集，`triedLines` 防死循环，
  全线路失败才提示。

### 2. 音乐媒体通知 / 音频焦点（对标 PeekPro 锁屏媒体控制 + DsPlayer 后台音频）
- `MediaPlaybackService`：前台通知带「播放/暂停」「停止」媒体按钮，驱动共享 `PlayerCore`。
- `AudioFocusManager`：绑定 ExoPlayer，失焦（LOSS/TRANSIENT）暂停并记忆、复焦（GAIN）自动续播；
  由 `PlayerCore` 在播放/释放时请求/放弃焦点。单播放器设计避免双实例双重音输出。

### 3. 阅读 TTS 缓存 + 连续朗读（对标 Legado 听书）
- `CloudTtsAssistant` 按 SHA-1(`model|voice|text`) 缓存 MP3，LRU 上限 64 条，命中不重合成；
  新增 `TtsConfigKeys.MODEL`，`SettingsActivity` 增「TTS 模型」输入。
- `ReaderViewModel` 订阅 `PlayerCore.playbackEnded`，章节读完自动 `loadChapter` 下一章并连续朗读；
  `stopSpeaking()` 停止；正文/目录分离 `loadChapter` 消除竞态。

### 4. 黑金 PeekPro 风格 UI
- `core:ui` 新增 `PeekColors`（`PeekBlack`/`PeekGold`/`PeekRadius` 18dp）与 `PeekComponents`
  （`PeekMediaCard`/`CardPeek`/`PeekHeader`/`CoilCover`）。
- `MainActivity` 入口 Hero + 能力卡片改 `CardPeek` 黑金观感；全 App 统一暗色基准。

## 未吸纳 / 边界说明（壳子硬约束，非本轮遗漏）

- **T4 代理 / JAR 爬虫隔离**：涉及爬虫与代理转发，guardrail 禁止，未实现。
- **ISO / HDR 解码矩阵**：依赖 native（FFmpeg/MDK）与 Firebase/cronet，超出纯 Kotlin 壳子范围，未对标。
  PeekPro 体积大（99MB）主因即这些 native 解码与第三方引擎；MediaShell 保持 4.85MB 级体积。
- **RSS / 漫画 / 自动化**：Legado 专有重模块，非壳子聚合目标。
