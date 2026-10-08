package com.aggregator.shell.core.media.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream

data class EpgChannel(
    val id: String,
    val displayName: String,
    val iconUrl: String?,
    val programs: List<EpgProgram>
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String?,
    val iconUrl: String?
)

/**
 * XMLTV (DVB) EPG parser。支持明文与 gzip 源，解析频道（含 icon）与节目单。
 *
 * XMLTV 时间格式：`20261008120000 +0800`（本地时间 + 时区偏移），
 * 统一转换为 UTC epoch millis。
 */
class EpgParser(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun load(url: String): List<EpgChannel> = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            okhttp3.Request.Builder().url(url).build()
        ).execute()
        if (!resp.isSuccessful) throw Exception("EPG HTTP ${resp.code}")
        val raw = resp.body?.bytes() ?: return@withContext emptyList()
        val text: String = run {
            if (raw.size > 2 && raw[0].toInt() == 0x1f && (raw[1].toInt() and 0xff) == 0x8b) {
                GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }.decodeToString()
            } else raw.decodeToString()
        }
        parse(text)
    }

    fun parse(xml: String): List<EpgChannel> {
        val channels = mutableListOf<EpgChannel>()
        val programs = LinkedHashMap<String, MutableList<EpgProgram>>()

        var inChannel = false
        var inProgramme = false
        var inTitle = false
        var inDesc = false
        var channelId = ""
        var displayName = ""
        var channelIcon: String? = null
        var progChannel = ""
        var start = 0L
        var end = 0L
        var title = ""
        var desc: String? = null
        var progIcon: String? = null

        fun flushProgramme() {
            if (progChannel.isNotBlank()) {
                programs.getOrPut(progChannel) { mutableListOf() }.add(
                    EpgProgram(progChannel, title.trim(), start, end, desc?.trim(), progIcon)
                )
            }
            title = ""
            desc = null
            progIcon = null
        }

        fun flushChannel() {
            if (channelId.isNotBlank()) {
                channels.add(EpgChannel(channelId, displayName, channelIcon, programs[channelId] ?: emptyList()))
            }
        }

        for (line in xml.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("<channel") -> {
                    inChannel = true
                    channelId = attr(t, "id").orEmpty()
                    displayName = ""
                    channelIcon = null
                }
                t.startsWith("</channel") -> { inChannel = false; flushChannel() }
                inChannel && t.startsWith("<display-name") ->
                    displayName = inlineText(t).ifEmpty { displayName }
                inChannel && t.startsWith("<icon") -> channelIcon = attr(t, "src")
                t.startsWith("<programme") -> {
                    inProgramme = true
                    progChannel = attr(t, "channel").orEmpty()
                    start = parseTs(attr(t, "start"))
                    end = parseTs(attr(t, "stop"))
                }
                t.startsWith("</programme") -> { inProgramme = false; flushProgramme() }
                inProgramme && t.startsWith("<title") -> inTitle = true
                inProgramme && t.startsWith("</title") -> inTitle = false
                inProgramme && t.startsWith("<desc") -> inDesc = true
                inProgramme && t.startsWith("</desc") -> inDesc = false
                inProgramme && t.startsWith("<icon") -> progIcon = attr(t, "src")
                inTitle -> title += inlineText(t)
                inDesc -> desc = (desc ?: "") + inlineText(t)
            }
        }
        return channels
    }

    private fun attr(line: String, name: String): String? =
        Regex("""$name="([^"]*)"""").find(line)?.groupValues?.get(1)

    private fun inlineText(line: String): String =
        if (line.contains("</")) line.substringAfter(">").substringBefore("</").trim()
        else if (line.contains(">")) line.substringAfter(">").trim()
        else ""

    /**
     * 解析 XMLTV 时间：`20261008120000 +0800`。
     * 支持纯 14 位（视为 UTC）与带 `±HHMM` 偏移（本地时间转 UTC）。
     */
    private fun parseTs(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val full = Regex("""(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\s*([+-])(\d{2})(\d{2})""")
            .find(s.trim())
        if (full != null) {
            val g = full.groupValues
            val utc = toUtcMillis(g[1], g[2], g[3], g[4], g[5], g[6])
            val offsetMin = g[8].toInt() * 60 + g[9].toInt()
            val sign = if (g[7] == "+") -1 else 1
            return utc + offsetMin * 60_000L * sign
        }
        val plain = Regex("""(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})""").find(s.trim())
        if (plain != null) {
            val g = plain.groupValues
            return toUtcMillis(g[1], g[2], g[3], g[4], g[5], g[6])
        }
        return 0L
    }

    private fun toUtcMillis(y: String, mo: String, d: String, h: String, mi: String, s: String): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(
            y.toInt(), mo.toInt() - 1, d.toInt(),
            h.toInt(), mi.toInt(), s.toInt()
        )
        return cal.timeInMillis
    }
}
