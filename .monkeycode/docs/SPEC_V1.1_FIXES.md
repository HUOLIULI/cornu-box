# v1.0.0 开发文档缺口补全说明（v1.1.0）

## 代码逻辑梳理与 UI 全方位完善（v1.1.0 第二轮）

### 引擎逻辑缺陷修复
| 引擎 | 缺陷 | 修复 |
|------|------|------|
| `LegadoEngine.search` | 死循环 `(0..3)` 用同一 body 生成 4 条相同行 | `RuleParser.list` 按 `list` 规则提取数组各元素，逐元素套字段规则 |
| `LegadoEngine.getToc` | `(0..50)` 死循环 50 条相同章节 | 同上用 `chapterList` 规则取真实章节列表 |
| `TvBoxEngine.search` | 把整个响应当单对象，只读 1 条 | `parseVideoList` 识别 `list`/`videoList`/`data` 包装，逐条映射 `vod_*` |
| `TvBoxEngine.getDetail` | 只读 `eps` 一种结构 | `parseEpisodes` 同时支持 `eps[]` 与 `episodes{}` |
| `LxMusicEngine` | 全方法写死演示数据 | 经 Rhino 沙箱执行用户导入的 LX 源脚本，失败/超时回退 demo |
| `EpgParser` | `<title>foo</title>` 单行写法丢内容 | 新增「同行开闭」预读分支，单行/多行均正确累积 |

### 数据通路接通 Room
- 三引擎经 `SourceModule` 注入对应 Room DAO（`BookSourceDao` / `VideoSourceDao`+`LiveSourceDao` / `MusicSourceDao`），运行时优先读用户导入源表，空表回退内置 demo。
- 组合式注入，无循环依赖（`core:source` 经 `api(:core:data)` 传递）。
- `TvBoxEngine.resolveLiveUrls` 读 `live_sources` 取 IPTV 地址。

### 日志体系
- 新增 `AppLog` 接口 + `NoOpLog`（core:common），引擎在「源解析失败 / 空结果 / 异常吞没」处按 i/w/e 记诊断。
- `RuleParser` 新增 JSON 字段直取快速路径：元素是 JSON 对象时，裸字段名（`id`/`title`）直接命中。

### UI 全方位升级
- **影视**：搜索框（输入 + 按钮触发）；三 Tab；卡片加封面图（Coil `AsyncImage`，短剧 9:16 + 横版列表）；加载/空态/错误重试。
- **阅读**：搜索框 + 书籍卡片（方形封面 + 作者 + 源名）；空态提示导入书源；重试。
- **音乐**：搜索框 + 歌曲卡片（圆形封面 + 歌手/专辑）；空态/重试。
- **设置**：`Scaffold` 顶部返回导航 + 模块三段选择器；「现有订阅」列表卡片可删除；AI 制源面板；底部留白统一。
- **导航**：三 feature Activity 顶部加 `ArrowBack`；统一 Material3 图标/间距/圆角（14dp 卡片）。

### 本轮构建与验证
- 全模块 `compileDebugKotlin` + `:app:assembleDebug` 通过
- `:core:source:testDebugUnitTest` 4/4 通过（JSONPath / 列表提取 / 正则 / JS 超时）

---

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
