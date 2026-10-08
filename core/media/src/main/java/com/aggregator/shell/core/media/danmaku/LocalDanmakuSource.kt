package com.aggregator.shell.core.media.danmaku

import javax.inject.Inject

/**
 * 内置演示弹幕源：不依赖网络，按剧集标题生成一批滚动弹幕，供播放器开箱即有弹幕。
 * 真实弹幕源（DanDanPlay 等）后续替换本实现，[DanmakuSource] 接口不变。
 */
class LocalDanmakuSource @Inject constructor() : DanmakuSource {

    override suspend fun searchEpisode(title: String, episode: Int): String? =
        "${title}#E${episode}"

    override suspend fun loadDanmaku(episodeId: String): List<DanmakuItem> {
        val seed = episodeId.hashCode()
        val lines = listOf(
            "名场面来了",
            "哈哈哈",
            "剧情反转太快",
            "主角好帅",
            "这集经典",
            "前面埋的坑终于填了"
        )
        // 用 seed 决定取几条，避免每次完全相同（演示用）
        val count = 4 + Math.abs(seed) % 3
        return (0 until count).map { i ->
            DanmakuItem(
                timeMs = i * 1_500L + (Math.abs(seed) % 1000L),
                text = lines[(i + seed) % lines.size],
                color = 0xFFFFFFFF.toInt()
            )
        }
    }
}
