package com.aggregator.shell.core.source.engine

/**
 * Provides default sample source payloads so the shell APK runs out of the box
 * without a network source. In production these come from Room.
 */
object SourceBootstrap {

    fun defaultLegadoSourceJson(): String = """
        {
          "bookSourceName": "内置演示书源",
          "bookSourceUrl": "https://example.com",
          "searchUrl": "https://example.com/search/{{key}}?page={{page}}",
          "ruleSearch": {
            "bookList": "${'$'}.data.books",
            "id": "${'$'}.id",
            "name": "${'$'}.title",
            "author": "${'$'}.writer",
            "coverUrl": "${'$'}.cover",
            "bookUrl": "${'$'}.id@/novel/{{result}}"
          },
          "ruleToc": {
            "chapterList": "//div#list dd a",
            "chapterName": "text",
            "chapterUrl": "href@https://example.com/read/{{bookId}}/{{result}}"
          },
          "ruleContent": {
            "content": "//div#content text"
          }
        }
    """.trimIndent()

    fun defaultTvBoxJson(): String = """
        {
          "sites": [
            { "key": "demo", "name": "演示", "type": 1, "api": "https://example.com/api/v1" }
          ],
          "lives": [
            { "name": "演示直播", "type": 0, "url": "https://example.com/live/demo.m3u8" }
          ],
          "flags": ["demo"]
        }
    """.trimIndent()

    fun defaultLxMusicJs(): String = """
        const { EVENT_NAMES, request, on, send } = globalThis.lx;
        on(EVENT_NAMES.request, ({ source, action, info }) => {
            if (action === 'musicUrl') {
                send({ url: 'https://example.com/audio/demo.mp3', quality: 'standard' });
            }
        });
    """.trimIndent()

    // ---------- 内置演示数据（example.com 兜底，保证壳子开箱有内容） ----------

    fun demoBookSearchBody(): String = """
        {"data":{"books":[
          {"id":"demo-1","title":"斗破苍穹","writer":"天蚕土豆","cover":"https://picsum.photos/seed/book1/300"},
          {"id":"demo-2","title":"完美世界","writer":"辰东","cover":"https://picsum.photos/seed/book2/300"},
          {"id":"demo-3","title":"雪中悍刀行","writer":"烽火戏诸侯","cover":"https://picsum.photos/seed/book3/300"},
          {"id":"demo-4","title":"诡秘之主","writer":"爱潜水的乌贼","cover":"https://picsum.photos/seed/book4/300"}
        ]}}
    """.trimIndent()

    fun demoBookTocBody(): String = """
        <html><body><div id="list">
          <dd><a href="/read/demo-1/1.html">第一章 陨落的天才</a></dd>
          <dd><a href="/read/demo-1/2.html">第二章 斗气大陆</a></dd>
          <dd><a href="/read/demo-1/3.html">第三章 强者之路</a></dd>
          <dd><a href="/read/demo-1/4.html">第四章 炼药术士</a></dd>
          <dd><a href="/read/demo-1/5.html">第五章 家族危机</a></dd>
        </div></body></html>
    """.trimIndent()

    fun demoBookContentBody(chapterUrl: String): String =
        """
        <html><body><div id="content">
        （内置演示正文 · $chapterUrl）
        斗破苍穹，三十年河东，三十年河西，莫欺少年穷！
        此处为演示章节正文内容，接入真实书源后由 ruleContent 规则提取正文。
        </div></body></html>
        """.trimIndent()

    fun demoVideoSearchBody(): String = """
        {"list":[
          {"vod_id":"demo-1","vod_name":"演示剧集 · 都市传说","vod_pic":"https://picsum.photos/seed/v1/300","type_name":"都市","vod_remarks":"更新至12集"},
          {"vod_id":"demo-2","vod_name":"演示剧集 · 星际穿越","vod_pic":"https://picsum.photos/seed/v2/300","type_name":"科幻","vod_remarks":"全24集"},
          {"vod_id":"demo-3","vod_name":"演示剧集 · 山海奇谈","vod_pic":"https://picsum.photos/seed/v3/300","type_name":"古装","vod_remarks":"更新至8集"},
          {"vod_id":"demo-4","vod_name":"演示剧集 · 悬案笔记","vod_pic":"https://picsum.photos/seed/v4/300","type_name":"悬疑","vod_remarks":"全16集"}
        ]}
    """.trimIndent()

    fun demoVideoDetailBody(): String = """
        {"list":[{
          "vod_id":"demo-1",
          "vod_name":"演示剧集 · 都市传说",
          "vod_content":"内置演示影视源。接入真实 TVBox/CatVod 接口后由 vod_play_url 解析剧集列表。",
          "vod_play_url":"线路一${'$'}https://example.com/media/demo-1/ep01.mp4#https://example.com/media/demo-1/ep02.mp4#https://example.com/media/demo-1/ep03.mp4${'$'}${'$'}${'$'}线路二${'$'}https://example.com/media/demo-1/ep01.m3u8#https://example.com/media/demo-1/ep02.m3u8"
        }]}
    """.trimIndent()
}
