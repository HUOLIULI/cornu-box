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
- **桌面歌词 / ISO 解码 / HDR**：依赖 native（FFmpeg/MDK），MediaShell 为纯 Kotlin 壳子
  （ABI 仅依赖 androidx 自带 `.so`），暂不对标。
- **RSS / 漫画 / 自动化**：Legado 专有重模块，非本次壳子聚合目标。

所有新增保持"壳子"约束：不内置任何源 / TTS 凭据，端点与 Key 仅存本机 DataStore。
