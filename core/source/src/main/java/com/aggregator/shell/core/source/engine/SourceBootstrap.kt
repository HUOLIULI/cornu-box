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
            "id": "$.data.books[0].id",
            "name": "$.data.books[0].title",
            "author": "$.data.books[0].writer",
            "coverUrl": "$.data.books[0].cover",
            "bookUrl": "$.data.books[0].id@/novel/{{result}}"
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
}
