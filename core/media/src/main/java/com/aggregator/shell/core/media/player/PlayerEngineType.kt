package com.aggregator.shell.core.media.player

/**
 * 播放器引擎选择（聚合自 PeekPro 的 Exo/mpv 双引擎切换）。
 *
 * EXO   —— 默认 ExoPlayer（media3），当前 [ExoPlayerCore] 已实现。
 * MPV   —— 预留 mpv/MediaKit 后端，待 native 库接入后由工厂返回对应实现；
 *          未接入时回落到 EXO，保证 APK 始终可编译。
 */
enum class PlayerEngineType { EXO, MPV }

/** 播放器后端选择策略：用户在切换播放器菜单里选，持久化在设置里。 */
object PlayerEngineSelector {
    @Volatile
    var current: PlayerEngineType = PlayerEngineType.EXO
        private set

    fun select(type: PlayerEngineType) { current = type }

    /**
     * 真正构造后端的入口。当前只有 Exo 实现，MPV 返回 false 表示回落。
     */
    fun isNativeBackendAvailable(type: PlayerEngineType): Boolean = when (type) {
        PlayerEngineType.EXO -> true
        PlayerEngineType.MPV -> false // 待 libmpv 接入
    }
}
