package com.donut.mixfile.util.file

/**
 * 从文件名里认剧名 / 季 / 集。
 * 纯本地纯 Kotlin，不依赖 Android，也不联网。
 *
 * 支持：
 *   三体.S01E07.2160p.WEB-DL.mkv        -> 三体, S1, E7
 *   庆余年 第03集.mp4                    -> 庆余年, S1, E3
 *   漫长的季节.EP05.1080p.mp4            -> 漫长的季节, S1, E5
 *   狂飙【12】.mp4                       -> 狂飙, S1, E12
 *   [小组] 三体 第二季 08.mp4             -> 三体, S2, E8
 * 认不出（纯数字文件名如 03.mp4）返回 null，不做任何改动。
 */

private val CN_NUM = mapOf(
    '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
    '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
)

/** 要把名字里清掉的画质/压制/字幕标记 */
private val NOISE_TOKENS = listOf(
    "2160p", "1080p", "720p", "480p", "4k", "8k", "uhd", "hdr10", "hdr", "dolby", "dv",
    "web-dl", "webdl", "webrip", "bluray", "blu-ray", "bdrip", "hdtv", "remux", "hdrip",
    "x264", "x265", "h264", "h265", "hevc", "avc", "10bit", "8bit", "60fps", "repack", "proper",
    "aac", "ac3", "dts", "atmos", "flac",
    "国语", "国配", "粤语", "中字", "中英", "双语", "简繁", "繁中", "简中",
    "内封", "内嵌", "外挂", "字幕", "无水印", "高清", "超清", "蓝光", "完整版", "未删减"
)

private val RE_SE = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,2})[\\s._-]*(?:e|ep)(\\d{1,4})(?![0-9])")
private val RE_SEASON_ONLY = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,2})(?![0-9a-z])")
private val RE_CN_EP = Regex("第\\s*(\\d{1,4})\\s*[集话話回期]")
private val RE_CN_SEASON = Regex("第\\s*([一二三四五六七八九十\\d]{1,2})\\s*季")
private val RE_EP = Regex("(?i)(?:^|[^a-z0-9])(?:ep|e|episode)[\\s._-]*(\\d{1,4})(?![0-9])")
private val RE_BRACKET_NUM = Regex("[\\[【(（]\\s*(\\d{1,4})\\s*[\\]】)）]")
private val RE_SEP_NUM = Regex("(?:^|[\\s._-])(\\d{1,4})(?:[\\s._-]|$)")
private val RE_BRACKETS = Regex("[\\[\\]【】()（）{}<>《》]")
private val RE_SEPS = Regex("[._\\-+~,;:!?|/\\\\、，。]+")
private val RE_SPACES = Regex("\\s+")

data class ParsedEpisode(
    val seriesName: String,
    val season: Int,
    val episode: Int,
)

private fun cnToInt(text: String): Int? {
    if (text.all { it.isDigit() }) return text.toIntOrNull()
    if (text.length == 1) return CN_NUM[text[0]]
    if (text.length == 2) {
        val a = CN_NUM[text[0]] ?: return null
        val b = CN_NUM[text[1]] ?: return null
        if (text[0] == '十') return 10 + b
        if (text[1] == '十') return a * 10
        return null
    }
    return null
}

private fun cleanName(raw: String): String {
    var s = raw
    NOISE_TOKENS.forEach { token ->
        s = s.replace(token, " ", ignoreCase = true)
    }
    s = RE_BRACKETS.replace(s, " ")
    s = RE_SEPS.replace(s, " ")
    s = RE_SPACES.replace(s, " ")
    s = s.trim()
    while (s.isNotEmpty() && s.first() in "-_. ()【】[]") s = s.drop(1).trim()
    while (s.isNotEmpty() && s.last() in "-_. ()【】[]") s = s.dropLast(1).trim()
    // 「剧名」太长时截断，避免一个奇葩文件名撑爆组名
    return s.take(40)
}

private fun seasonOf(base: String): Int {
    val cn = RE_CN_SEASON.find(base)
    if (cn != null) {
        val n = cnToInt(cn.groupValues[1])
        if (n != null) return n.coerceIn(1, 99)
    }
    val m = RE_SEASON_ONLY.find(base) ?: return 1
    return m.groupValues[1].toIntOrNull() ?: 1
}

/** 从文件名解析剧集信息，认不出返回 null */
fun parseEpisode(fileName: String): ParsedEpisode? {
    val base = fileName.substringBeforeLast('.', fileName).trim()
    if (base.isEmpty()) return null

    // 1. S01E07 / S01.E07 / s1e7
    RE_SE.find(base)?.let { m ->
        val ep = m.groupValues[2].toIntOrNull()
        if (ep != null && ep in 1..3000) {
            val name = cleanName(base.replace(m.value, " "))
            if (name.isNotEmpty()) {
                val season = m.groupValues[1].toIntOrNull() ?: 1
                return ParsedEpisode(name, season.coerceIn(1, 99), ep)
            }
        }
    }

    // 2. 第03集 / 第3话
    RE_CN_EP.find(base)?.let { m ->
        val ep = m.groupValues[1].toIntOrNull()
        if (ep != null && ep in 1..3000) {
            val name = cleanName(base.replace(m.value, " "))
            if (name.isNotEmpty()) {
                return ParsedEpisode(name, seasonOf(base).coerceIn(1, 99), ep)
            }
        }
    }

    // 3. EP05 / E05 / episode 5
    RE_EP.find(base)?.let { m ->
        val ep = m.groupValues[1].toIntOrNull()
        if (ep != null && ep in 1..3000) {
            val name = cleanName(base.replace(m.value, " "))
            if (name.isNotEmpty()) {
                return ParsedEpisode(name, seasonOf(base).coerceIn(1, 99), ep)
            }
        }
    }

    // 4. 狂飙【12】/ 狂飙(12)
    RE_BRACKET_NUM.find(base)?.let { m ->
        val ep = m.groupValues[1].toIntOrNull()
        if (ep != null && ep in 1..3000) {
            val name = cleanName(base.replace(m.value, " "))
            if (name.isNotEmpty()) {
                return ParsedEpisode(name, seasonOf(base).coerceIn(1, 99), ep)
            }
        }
    }

    // 5. 末尾独立数字：三体 08 / 三体-08
    RE_SEP_NUM.findAll(base).toList().lastOrNull()?.let { m ->
        val ep = m.groupValues[1].toIntOrNull()
        if (ep != null && ep in 1..3000) {
            val name = cleanName(base.replace(m.value, " "))
            if (name.isNotEmpty()) {
                return ParsedEpisode(name, seasonOf(base).coerceIn(1, 99), ep)
            }
        }
    }

    return null
}

/** 集号显示：E07 */
fun episodeLabel(episode: Int): String = "E" + episode.toString().padStart(2, '0')
