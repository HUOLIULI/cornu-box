# 接口定义（落地实现）

## 源引擎能力接口（`com.aggregator.shell.core.source.api`）

```kotlin
interface ReaderEngine {
    suspend fun search(keyword: String, page: Int = 1): List<BookResult>
    suspend fun getToc(bookId: String): List<Chapter>
    suspend fun getContent(chapterId: String): String
}
interface VideoEngine {
    suspend fun search(keyword: String, page: Int = 1): List<VideoResult>
    suspend fun getDetail(id: String): VideoDetail
    suspend fun getPlayUrl(id: String, flag: String): PlayResult
}
interface MusicEngine {
    suspend fun search(keyword: String): List<MusicResult>
    suspend fun getMusicUrl(song: MusicResult, quality: String): String
    suspend fun getLyric(song: MusicResult): String
}
```

引擎实现：`LegadoEngine` / `TvBoxEngine` / `LxMusicEngine`，经 `SourceModule` 提供。

## 沙箱（`core.source.sandbox`）

- `JsSandboxExecutor.execute(script, bindings, timeoutMillis): JsResult`
  - 默认实现 `RhinoJsExecutor`（纯 JVM），`JsResult` 为 `Success/Failure/Timeout`。
- `PythonRuntime.invoke(spiderScript, method, vararg args)`
  - 默认 `NoOpPythonRuntime`（`isAvailable=false`）。

## AI 助手（`core.ai`）

```kotlin
interface AiSourceAssistant {
    suspend fun generateSource(targetUrl: String, module: ModuleType, sampleHtml: String?): SourceGenerationResult
    suspend fun repairSource(original: String, logs: List<SourceLogEntity>, module: ModuleType): SourceRepairResult
    suspend fun analyzeFailure(logs: List<SourceLogEntity>): FailureAnalysis
}
```

## 播放器（`core.media.player`）

```kotlin
interface PlayerCore {
    val state: StateFlow<PlayerState>
    fun initialize(context: Context)
    suspend fun prepare(item: PlayMediaItem)
    fun switchUrl(item: PlayMediaItem)
    fun pause(); fun resume(); fun release()
}
```
默认 `ExoPlayerCore`，断流指数退避（1/2/4/8/15s，最多 5 次），播放 URL scheme 白名单校验。

## EPG（`core.media.epg`）

`EpgParser.load(url): List<EpgChannel>`，支持 XMLTV 明文与 gzip。
