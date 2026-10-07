# 架构设计（落地实现）

## 模块依赖图

```
        :app  (HiltApplication, MainActivity, 导航)
         |
   +-----+----------+-----------+-----------+
   |     |          |           |           |
 :core: :core:data :core:source :core:media :feature:*
   common data      +core:ui    (player,
               (engines,          danmaku,
                sandbox)          epg, ai)
```

## 关键决策（v1.0.0 缺口补全）

1. **引擎按能力拆分**：v1.0.0 用单一 `SourceEngine<T>` 泛型接口无法同时服务阅读/影视/音乐。落地改为 3 个能力接口：
   - `ReaderEngine` — search / getToc / getContent
   - `VideoEngine`  — search / getDetail / getPlayUrl
   - `MusicEngine`  — search / getMusicUrl / getLyric
   具体引擎经 Hilt 注入，Feature 只依赖能力接口。
2. **模块合并策略**：v1.0.0 规划 15 个 Gradle 模块；考虑到沙箱 2 核 7.8G 构建资源与首版交付节奏，合并为 9 个 Gradle 模块（common / data / source / media / ui / 4 feature + app），包名仍按原架构分层，便于后续按域拆分。
3. **沙箱可构建性**：v1.0.0 默认 QuickJS（native）与 Chaquopy（Python）。落地 MVP 默认使用 **Rhino（纯 JVM）** 作为 JS 引擎、**NoOpPythonRuntime** 作为 Python 运行时占位，保证无任何 native 工具链即可编译 APK。QuickJS 与 Chaquopy 通过 `JsSandboxExecutor` / `PythonRuntime` 接口在 `gradle.properties` 开启 `-PenableChaquopy=true` 后注入实现。
4. **AI 助手 Key 边界**：`AiSourceAssistant` 默认离线启发式实现（`HeuristicAssistant`），用户自带 LLM 配置通过设置界面输入、经 `context.appDataStore` 持久化。应用绝不读取构建环境中的任何大模型 Key。
5. **弹幕**：v1.0.0 选 DanmakuFlameMaster（legacy、含 native 依赖）。落地改为 Compose Canvas 实现，功能对等，体积更小。
6. **`ModuleType` 补齐**：v1.0.0 未定义该枚举。落地定义于 `core:common`，取值 `READER / VIDEO / MUSIC / LIVE`，`LIVE` 专门承载 IPTV。

## 导航

底部导航 `影视 → 阅读 → 音乐 → 设置`；影视子页签 `点播 / 短剧 / IPTV`。

## 数据

Room `shell.db`，schema 版本 1，`exportSchema=true` 导出到 `schemas/`。表：`book_sources / bookshelf / video_sources / live_sources / music_sources / epg_channels / epg_programs / play_history / source_subscriptions / source_logs`。

DataStore `shell_prefs` 存储主题、默认音质、LLM 配置（Key 占位）。
