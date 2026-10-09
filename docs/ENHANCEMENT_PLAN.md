# MediaShell 增强功能规划

基于 5 个同类项目的功能调研（PeekPili、UZN、Cinetry、AVBox、DsPlayer），为 MediaShell（com.aggregator.shell，Kotlin + Compose + Hilt + Room + ExoPlayer 多源聚合壳）制定功能增强规划。

## 1. 各项目值得借鉴的特性

### 1.1 PeekPili（基于 PiliPlus 二开的多平台空壳，TVBox 玩法）

| 特性 | 借鉴价值 |
|------|----------|
| 接口配置中心 + 多接口管理与导入导出（切接口后可导出/恢复配置） | 多源聚合壳的核心工作流，MediaShell 目前只有单点源导入 |
| 源助手：对 CatJS / Python 源在线测试（选测试方法、填参数、看结果） | 直接对应 MediaShell 的 `RuleParser` + `RhinoJsExecutor` 沙箱，可把沙箱能力做成用户可感的调试工具 |
| 播放地址嗅探（`"parse": 1` 格式） | 解析失败时的兜底能力，对 TVBox/CatVod 源兼容性好 |
| 短剧模式：竖屏全屏 + 上下滑切集 + 播完自动跳下一集 + 长按倍速快进（倍速 1.5x~5x 可自定义） | MediaShell 已有短剧 VerticalPager，缺「自动跳集 + 长按倍速」 |
| 智能预加载：相邻集数自动预加载 | 降低短剧/剧集切换卡顿 |
| 历史记录（浏览记录 / 观看记录 + 续播集数）与收藏（双击收藏、一键续播、左滑删除、一键清空） | 收藏闭环比 MediaShell 当前只有删除更完整 |
| 换源：底部导航快捷切换源 | 与多接口管理配套 |
| 详情页超链接支持 | TVBox 详情页内嵌跳转 |
| 横屏搜索结果布局左右切换 | 大屏/Pad 体验 |
| 本地弹幕 API 自建 + 多端同步 | MediaShell 已有 RemoteDanmakuSource 配置，可扩展 |

### 1.2 UZN / UZ 影视（全平台空壳，v1.8 主打聚合直播 + 4K 秒播 + WebDAV 同步）

| 特性 | 借鉴价值 |
|------|----------|
| 聚合虎牙/抖音/快手 M3U 直播 | 直播源不止 M3U8，扩展聚合直播入口 |
| 4K 秒播（解码/缓冲优化） | ExoPlayer 侧的起播性能优化 |
| WebDAV 同步观看记录与设置 | 跨设备同步，MediaShell 纯本机 |
| 免登录 | 与 MediaShell 壳子定位一致，作为卖点保持 |
| 智能画质调节：按网络状况自动切档 + 手动锁定画质 | 播放体验 |
| 离线下载：批量下载整季、仅 WiFi 自动下载、多任务并行、下载管理 | MediaShell 完全没有下载能力 |
| 手势控制：亮度 / 音量 / 进度 | 播放器交互 |
| 自动连播下一集 | 剧集播放闭环 |
| 手势 + 倍速（0.5x~2x） | 播放控制基础项 |
| 搜索模糊匹配 | 检索体验 |
| 儿童专区 / 老年模式 | 家庭场景（低优先级） |

### 1.3 Cinetry（开源跨平台媒体聚合播放器）

| 特性 | 借鉴价值 |
|------|----------|
| 多服务器/多源聚合搜索：一次搜索查所有源，结果按相关性排序 + 按类型过滤 | MediaShell 当前单源搜索，聚合搜索是最大体验缺口 |
| 同一影片多播放来源：播放界面直接切换 | 与「换源」呼应，放在播放器内 |
| 多服务器配置保存 + 一键切换 + 实时连接状态检查 | 源健康度可见 |
| 自定义弹幕服务（配置 API 规范的弹幕服务器） | MediaShell 已有 RemoteDanmakuSource，可对齐其 API 规范 |
| 元数据规则自定义：统一不同平台资源格式 | 多源结果归一化 |
| 离线缓存 | 同 UZN 下载能力 |
| 家长控制：按内容类型/评级限制 + 密码保护 | 家庭场景 |
| 纯本地、不扫库、隐私优先 | 与 MediaShell 壳子定位一致，写进卖点 |
| HDR / Dolby Vision / Dolby Atmos 适配 | 高端播放体验 |

### 1.4 AVBox（XiaochangXu/AVBox，TVBox 手机版，Jetpack Compose 重写 UI，基于 TVBoxOS + FongMi）

| 特性 | 借鉴价值 |
|------|----------|
| 与 MediaShell 技术栈最接近（Compose 空壳 + 自配源），可直接对照 | 架构对齐参照 |
| 首页 / 播放 / 历史 / 设置 四页 + 宽屏首页适配 | 页面结构基准 |
| 宽屏（Pad/折叠屏）布局 | 响应式布局 |
| 智能搜索 + 换源（多源结果聚合展示） | 同 Cinetry 聚合搜索 |
| 历史页独立 | 与 MediaShell 影视 Tab 内历史子 Tab 对照 |
| 纯空壳定位：不内置内容、不提供下载 | 定位一致 |

### 1.5 DsPlayer / DPlayer（HTML5 弹幕播放器）

| 特性 | 借鉴价值 |
|------|----------|
| 弹幕：密度/速度/透明度/分层可调 + 屏蔽过滤 + 按时间轴过滤分组 + 置顶/底部嵌入 | 弹幕引擎增强（MediaShell 已有 Canvas 弹幕，缺这些参数） |
| 弹幕与进度精确对齐（时间戳驱动） | 短剧/剧集弹幕同步 |
| 播放控制：画质切换、缩略图预览、视频截图、字幕文件加载、Markdown 字幕 | 播放器周边能力 |
| 快捷键/手势与触屏交互一致 | 交互基准 |
| 模块化：弹幕/控制栏可开关 | 播放页组件化 |

## 2. 增强功能清单（按优先级）

### P0 — 高价值低门槛，直接改善核心链路

| # | 功能 | 来源 | 一句话描述 | 实现位置 |
|---|------|------|-----------|----------|
| P0-1 | 多源聚合搜索 | Cinetry、AVBox | 一次搜索并发查询所有已启用源，结果按相关性/类型归一合并展示 | `core:source`（引擎聚合层）+ `feature:video` 列表页 |
| P0-2 | 播放页内换源（多线路切换） | Cinetry、PeekPili、AVBox | 同一影片多来源在播放页一键切换，保留当前集数/进度位置 | `core:media`（`PlayerCore.switchUrl` 扩展）+ `feature:video` `EpisodeSelector` |
| P0-3 | 自动连播下一集 | UZN、PeekPili | 剧集播完自动切下一集，短剧竖滑模式自动跳集 | `feature:video` `VideoViewModel` + `PlayerSurface` 播放完成回调 |
| P0-4 | 收藏闭环（续播/取消收藏/左滑删除/一键清空） | PeekPili | 收藏页支持一键跳转续播、左滑删除、一键清空 | `core:data`（`FavoritesDao` 扩展）+ `feature:video` 收藏 Tab |
| P0-5 | 观看进度持久化与续播 | PeekPili、UZN | 播放历史精确到秒，历史/收藏页可直接续播 | `core:data` `play_history` 已有，补自动保存定时点 + 续播入口 |
| P0-6 | 弹幕参数可调（密度/速度/透明度/分层 + 屏蔽过滤） | DsPlayer | 弹幕开关旁提供密度、速度、透明度、滚动/置顶/嵌入分层与关键词屏蔽 | `core:media` 弹幕 Canvas 渲染层 + `feature:settings` |
| P0-7 | 播放地址嗅探兜底 | PeekPili | 常规解析失败时嗅探页面内真实流地址（支持 `"parse": 1` 格式） | `core:source` `RuleParser` + `TvBoxEngine` |
| P0-8 | 倍速 0.5x~2x + 长按倍速快进（1.5x~5x 可配） | UZN、PeekPili | 播放器倍速档位扩展，短剧长按快进倍速在设置中可调 | `core:media`（`setPlaybackSpeed` 档位）+ `feature:video` 手势 |

### P1 — 体验完整度与多端同步

| # | 功能 | 来源 | 一句话描述 | 实现位置 |
|---|------|------|-----------|----------|
| P1-1 | 离线下载管理（批量整季、仅 WiFi、多任务并行、下载管理页） | UZN、Cinetry | 剧集/短剧本地缓存，下载中心管理任务 | 新增 `feature:download` + `core:data` 下载任务表 |
| P1-2 | 智能画质自适应（按网络切档 + 手动锁定） | UZN | 弱网自动降档，用户可锁定目标画质 | `core:media` `PlayerCore` |
| P1-3 | 手势控制（亮度/音量/进度） | UZN、DsPlayer | 左右滑动调亮度音量，双指/单指精细控进度 | `feature:video` `PlayerSurface` |
| P1-4 | 源健康度与连接状态检查 | Cinetry | 已配源批量探活，失效源标记并支持一键恢复/停用 | `core:source` 探活任务 + `feature:settings` |
| P1-5 | 接口配置中心（多接口管理 + 导入导出 + 切换） | PeekPili、AVBox | 多套源配置并存，一键导出/恢复，切接口不动数据 | `core:data` 配置实体 + `feature:settings` |
| P1-6 | WebDAV 同步（观看记录/设置/配置） | UZN、Cinetry | 记录与配置同步到用户自建 WebDAV | 新增 `core:sync` |
| P1-7 | 智能预加载（相邻集数） | PeekPili | 当前集播放时预加载下一集流地址，切集秒开 | `core:media` 预加载器 |
| P1-8 | 源助手（JS/Python 源在线测试） | PeekPili | 对已配 CatJS/Python 源选方法填参数试跑，结果回显 | `core:source` 沙箱 + `feature:settings` |
| P1-9 | 弹幕服务自定义 API 对齐 | Cinetry | 弹幕配置支持标准弹幕 API 规范的服务端 | `core:media` `RemoteDanmakuSource` |
| P1-10 | 短视频推荐流（刷新重推 + 浏览记录） | PeekPili | 短剧 Tab 推荐区支持下拉刷新随机重推，浏览自动入历史 | `feature:video` |

### P2 — 场景扩展与差异化

| # | 功能 | 来源 | 一句话描述 | 实现位置 |
|---|------|------|-----------|----------|
| P2-1 | 宽屏/Pad 响应式布局 | AVBox、Cinetry | 搜索结果左右布局切换、宽屏首页、双栏列表 | `core:ui` + `feature:video` |
| P2-2 | 字幕与截图（字幕文件加载、Markdown 字幕、视频截图） | DsPlayer | 播放页外挂字幕、精彩帧截图保存 | `core:media` |
| P2-3 | 弹幕特效（表情/彩条/置顶固定） | DsPlayer | 本地弹幕支持表情与彩条样式 | `core:media` 弹幕渲染 |
| P2-4 | 家长控制（类型/评级限制 + 密码） | Cinetry | 按内容类型限制 + PIN 锁 | `feature:settings` + `core:data` |
| P2-5 | 聚合直播入口（M3U 多平台直播聚合） | UZN | 直播 Tab 支持多 M3U 源聚合与分组 | `feature:video` IPTV 扩展 |
| P2-6 | 儿童模式 / 老年模式 | UZN | 内容分区 + 大字体低操作界面 | `feature:*` + `core:ui` |
| P2-7 | 高清高规格解码（HDR/Dolby 适配） | Cinetry、UZN | ExoPlayer 侧开启 HDR/Dolby 解码路径 | `core:media` |
| P2-8 | 多端配置同步与云收藏 | PeekPili、UZN | 收藏/历史/配置跨设备同步 | 依赖 P1-6 |

## 3. 规划说明

1. **P0 全部落在已有模块内**，无新增 Gradle 模块，改动集中在 `core:source`（聚合搜索/嗅探）、`core:media`（换源/预加载/弹幕参数）、`feature:video`（连播/收藏闭环/手势倍速）、`core:data`（收藏/历史补齐）。
2. **P1-1 下载管理** 与 **P1-6 WebDAV 同步** 需新增 `feature:download` 与 `core:sync` 两个模块，是 P1 中工作量最大的两项，建议拆分迭代。
3. 与 MediaShell「壳子」定位冲突的项（内置内容、内置账号体系）全部排除；多端同步仅限用户自带 WebDAV/自建服务，不做官方云。
4. 源助手（P1-8）是差异化亮点：MediaShell 已内置 `RhinoJsExecutor` 沙箱与 `AiSourceAssistant` 制源助手，补一个「在线测试回显」界面即可，成本最低。
