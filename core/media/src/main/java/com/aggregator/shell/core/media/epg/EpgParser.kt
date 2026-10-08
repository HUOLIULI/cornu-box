package com.aggregator.shell.core.media.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
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
 * Minimal XMLTV parser. Sufficient for the shell MVP: extracts channels and
 * each channel's programmes. Supports plain XML and gzip.
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
        val channelIds = LinkedHashMap<String, String>() // id -> displayName
        val programs = LinkedHashMap<String, MutableList<EpgProgram>>()

        var inChannel = false
        var inProgramme = false
        var inTitle = false
        var inDesc = false
        var channelId = ""
        var displayName = ""
        var progChannel = ""
        var start = 0L
        var end = 0L
        var title = ""
        var desc: String? = null
        var icon: String? = null

        fun flushProgramme() {
            if (progChannel.isNotBlank()) {
                programs.getOrPut(progChannel) { mutableListOf() }.add(
                    EpgProgram(progChannel, title.trim(), start, end, desc?.trim(), icon)
                )
            }
            title = ""; desc = null; icon = null
        }

        fun flushChannel() {
            if (channelId.isNotBlank()) {
                channels.add(EpgChannel(channelId, displayName, null, programs[channelId] ?: emptyList()))
            }
        }

        for (line in xml.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("<channel") -> {
                    inChannel = true
                    channelId = attr(t, "id").orEmpty()
                }
                t.startsWith("</channel") -> { inChannel = false; flushChannel() }
                inChannel && t.startsWith("<display-name") ->
                    displayName = attr(t, "lang")?.let { displayName } ?: (t.substringAfter(">").substringBefore("</").trim()).ifEmpty { displayName }
                t.startsWith("<programme") -> {
                    inProgramme = true
                    progChannel = attr(t, "channel").orEmpty()
                    start = parseTs(attr(t, "start"))
                    end = parseTs(attr(t, "stop"))
                }
                t.startsWith("</programme") -> { inProgramme = false; flushProgramme() }
                inProgramme && t.startsWith("<title") -> inTitle = true
                inProgramme && t.startsWith("</title") -> { inTitle = false; title += " " }
                inProgramme && t.startsWith("<desc") -> inDesc = true
                inProgramme && t.startsWith("</desc") -> inDesc = false
                inProgramme && t.startsWith("<icon") -> icon = attr(t, "src")
                inTitle -> title += t.substringAfter(">").substringBefore("<")
                inDesc -> desc = (desc ?: "") + t.substringAfter(">").substringBefore("<")
            }
        }
        return channels
    }

    private fun attr(line: String, name: String): String? =
        Regex("""$name="([^"]*)"""").find(line)?.groupValues?.get(1)

    private fun parseTs(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val m = Regex("""(\d{8})(\d{4})(\d{2})\s*(?:([+-]\d{2}):?(\d{2})|Z)?""").find(s) ?: return 0L
        val date = m.groupValues[1]
        val time = m.groupValues[2]
        val year = date.substring(0, 4).toIntOrNull() ?: return 0L
        val month = date.substring(4, 6).toIntOrNull() ?: return 0L
        val day = date.substring(6, 8).toIntOrNull() ?: return 0L
        val hour = time.substring(0, 2).toIntOrNull() ?: return 0L
        val minute = time.substring(2, 4).toIntOrNull() ?: return 0L
        val second = m.groupValues[3].toIntOrNull() ?: 0
        val tzSign = m.groupValues[4]
        val tzHours = m.groupValues[5].toIntOrNull() ?: 0
        val tzMinutes = m.groupValues[6].toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        var epochMs = cal.timeInMillis
        if (tzSign != null) {
            val offsetSec = (tzHours * 3600 + tzMinutes * 60) * if (tzSign == "-") -1 else 1
            epochMs -= offsetSec * 1000L
        }
        return epochMs
    }
}
