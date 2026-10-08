# 源配置说明

MediaShell（`com.aggregator.shell`）是壳子 APK，**不内置任何内容源 / 弹幕 / LLM 凭据**。
所有外部端点与鉴权由用户在「设置」页配置，仅保存在本机 DataStore（`shell_prefs`），
应用不会读取构建环境或任何平台 Key。

## 配置入口

「设置」Tab（`SettingsActivity`），四个配置区：

| 区域 | 数据键 | 说明 |
|------|--------|------|
| 接口管理 | —（写入 Room `subscriptions` 表） | 影视 JSON / 书源 JSON / 音乐脚本的订阅 URL |
| AI 制源助手 | `llm_base_url` / `llm_api_key` | 云端 LLM 制源端点（可选，默认离线启发式） |
| 弹幕源 | `danmaku_base_url` / `danmaku_api_key` | 真实弹幕 API（可选，默认演示弹幕） |
| 关于 | — | 版本信息 |

## 各源类型

### 影视 / 短剧 / IPTV（TVBox 风格 JSON）
- 在「接口管理」选「影视」，填源订阅 URL（TVBox JSON）→「导入/订阅」
- 本地写入 Room `video_sources` 表，按 `type` 区分 `voddraft` / `drama` / `live`
- `LiveChannel.epg` 字段填 XMLTV EPG 源 URL（明文或 gzip）；留空走内置演示 EPG
- 拉取不可达时回退内置演示数据（开箱可用）

### 阅读（Legado 风格书源 JSON）
- 「接口管理」选「阅读」，填书源 JSON URL → 导入
- 本地 `book_sources` 表；引擎 `LegadoEngine` 内置 demo 兜底

### 音乐（LX Music 风格脚本）
- 「接口管理」选「音乐」，填音乐脚本 URL → 导入
- 本地 `music_sources` 表；引擎 `LxMusicEngine` 接 `JsSandboxExecutor` 注入 `lx` 桥

### 弹幕源（`RemoteDanmakuSource`）
- 设置「弹幕源」填 Base URL + 可选 API Key
- 约定接口（DanDanPlay 风格）：
  - 查集：`GET {base}/api/av1/danmaku?search={title}&episode={n}` → `[{"epsid":"..."}]`
  - 取弹幕：`GET {base}/api/av1/danmaku/{epsid}` → `[{"time":1.5,"text":"...","color":...}]`
- 未配置 / 失败 / 无结果 → 自动回退 `LocalDanmakuSource` 演示弹幕

### LLM 制源助手
- 设置「AI 制源助手」填 Base URL + API Key（OpenAI 兼容端点）
- 未配置时用离线 `HeuristicAssistant`（无需网络）

## 安全边界

- 所有端点 / Key 仅存本机 DataStore，**不上传、不内置、不读构建环境 Key**
- 弹幕 / LLM 为可选增强项，留空走演示 / 离线兜底，应用可完全离线运行
- 源订阅 URL 由用户自备，应用只负责拉取解析，不代为发现 / 抓取未知源
