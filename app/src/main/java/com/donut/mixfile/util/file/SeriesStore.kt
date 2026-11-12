package com.donut.mixfile.util.file

import com.donut.mixfile.server.core.objects.FileDataLog
import com.donut.mixfile.util.cachedMutableOf
import com.donut.mixfile.util.showToast
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 剧集分组 —— 完全是「旁挂索引」，不碰 core 的 FileDataLog、不碰上传协议、
 * 不写进 shareInfoData。清掉这几个 key 就等于回到原版，已上传文件一个都不受影响。
 *
 * key 一律用 shareInfoData（core 里 isSimilar 也只认这个）。
 */

@Serializable
data class SeriesGroup(
    val id: String,
    val name: String,
    val season: Int = 1,
    /** 0 = 未知（缺集只按已传范围里找中间缺的集），>0 = 用户设定的总集数 */
    val total: Int = 0,
    val time: Long = System.currentTimeMillis(),
)

@Serializable
data class SeriesItem(
    val shareInfoData: String,
    val groupId: String,
    val episode: Int,
    val time: Long = System.currentTimeMillis(),
)

var seriesGroups by cachedMutableOf(listOf<SeriesGroup>(), "mix_series_groups")
var seriesItems by cachedMutableOf(listOf<SeriesItem>(), "mix_series_items")

/** 首页「按剧集」开关，默认关闭 = 和原版完全一致 */
var seriesGroupEnabled by cachedMutableOf(false, "mix_series_enabled")

/** 已展开的组 id（默认空 = 全部收起，一眼看到有几部剧、缺多少集） */
var seriesExpandedGroups by cachedMutableOf(setOf<String>(), "mix_series_expanded")

fun newSeriesGroupId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

fun itemOf(log: FileDataLog): SeriesItem? =
    seriesItems.firstOrNull { it.shareInfoData == log.shareInfoData }

fun episodeOf(log: FileDataLog): Int? = itemOf(log)?.episode

fun groupOf(log: FileDataLog): SeriesGroup? {
    val item = itemOf(log) ?: return null
    return seriesGroups.firstOrNull { it.id == item.groupId }
}

fun createSeriesGroup(name: String, season: Int = 1): SeriesGroup {
    val cleanName = name.trim().ifEmpty { "未命名剧集" }.take(40)
    val group = SeriesGroup(
        id = newSeriesGroupId(),
        name = cleanName,
        season = season.coerceIn(1, 99)
    )
    seriesGroups = seriesGroups + group
    return group
}

fun updateSeriesGroup(group: SeriesGroup) {
    seriesGroups = seriesGroups.map { if (it.id == group.id) group else it }
}

fun deleteSeriesGroup(group: SeriesGroup) {
    seriesGroups = seriesGroups.filterNot { it.id == group.id }
    seriesItems = seriesItems.filterNot { it.groupId == group.id }
    seriesExpandedGroups = seriesExpandedGroups - group.id
}

fun toggleSeriesGroupExpanded(groupId: String) {
    seriesExpandedGroups =
        if (seriesExpandedGroups.contains(groupId)) seriesExpandedGroups - groupId
        else seriesExpandedGroups + groupId
}

/** 把文件放进某个组（同一文件只会出现在一个组里） */
fun putFileIntoSeries(log: FileDataLog, groupId: String, episode: Int) {
    seriesItems = seriesItems.filterNot { it.shareInfoData == log.shareInfoData } +
            SeriesItem(log.shareInfoData, groupId, episode)
}

fun removeFileFromSeries(log: FileDataLog) {
    seriesItems = seriesItems.filterNot { it.shareInfoData == log.shareInfoData }
}

/**
 * 上传完成后调用：认文件名 -> 找到同名同季的组（没有就建一个）-> 记下集号。
 * 认不出剧集名（比如 03.mp4、项目资料.zip）直接返回 null，什么都不做。
 */
fun autoAssignSeries(log: FileDataLog): Pair<SeriesGroup, Int>? {
    val parsed = parseEpisode(log.name) ?: return null
    val existed = seriesGroups.firstOrNull {
        it.name == parsed.seriesName && it.season == parsed.season
    }
    val group = existed ?: createSeriesGroup(parsed.seriesName, parsed.season)
    putFileIntoSeries(log, group.id, parsed.episode)
    return group to parsed.episode
}

/** 一个组展开后要显示的东西 */
data class SeriesContent(
    val group: SeriesGroup,
    /** 集号 -> 文件，按集号升序 */
    val episodes: Map<Int, FileDataLog>,
    /** 缺的集号 */
    val missing: List<Int>,
) {
    val have: Int get() = episodes.size

    /** 已传集数里最大的集号 */
    val maxEpisode: Int get() = episodes.keys.maxOrNull() ?: 0

    /** 已知总数：用户设过就用他设的，否则用已传的最大集号 */
    val totalCount: Int get() = if (group.total > 0) group.total else maxEpisode

    val title: String
        get() = buildString {
            append(group.name)
            if (group.season > 1) append(" · 第").append(group.season).append("季")
        }

    /** 组头右侧那行小字：18/20 集 · 缺 E04 E11 */
    fun summary(): String {
        val total = totalCount
        val sb = StringBuilder()
        if (total > 0) {
            sb.append(have).append('/').append(total).append(" 集")
        } else {
            sb.append(have).append(" 集")
        }
        if (missing.isNotEmpty()) {
            sb.append(" · 缺 ")
            sb.append(missing.take(4).joinToString(" ") { episodeLabel(it) })
            if (missing.size > 4) sb.append(" 等").append(missing.size).append("集")
        } else if (total > 0 && have >= total) {
            sb.append(" · 已完整")
        }
        return sb.toString()
    }
}

fun seriesContent(group: SeriesGroup): SeriesContent {
    val fileMap = uploadLogs.associateBy { it.shareInfoData }
    val episodes = sortedMapOf<Int, FileDataLog>()
    seriesItems.filter { it.groupId == group.id }.forEach { item ->
        val log = fileMap[item.shareInfoData]
        if (log != null && !episodes.containsKey(item.episode)) {
            episodes[item.episode] = log
        }
    }
    // 没设总集数时不去猜「缺哪几集」—— 只传了 E07/E09 却被报一堆「缺」会很吵，
    // 用户在手长按组头填上总集数后才开始算缺集。
    val missing =
        if (group.total > 0) (1..group.total).filter { !episodes.containsKey(it) } else emptyList()
    return SeriesContent(group, episodes, missing)
}

/** 有内容（至少一个文件还在上传历史里）的组，按最近上传时间倒序 */
fun activeSeriesGroups(): List<SeriesGroup> {
    val fileKeys = uploadLogs.map { it.shareInfoData }.toSet()
    val usable = seriesItems.filter { fileKeys.contains(it.shareInfoData) }
    return seriesGroups
        .filter { group -> usable.any { it.groupId == group.id } }
        .sortedByDescending { group ->
            usable.filter { it.groupId == group.id }.maxOfOrNull { it.time } ?: group.time
        }
}

/** 没归到任何组的文件 */
fun ungroupedLogs(): List<FileDataLog> {
    val used = seriesItems.map { it.shareInfoData }.toSet()
    return uploadLogs.filterNot { used.contains(it.shareInfoData) }
}

private var lastSeriesHintAt = 0L

/**
 * 上传完成后的静默归组。批量传 20 集时只弹一次提示，不刷屏。
 */
fun autoAssignSeriesWithHint(log: FileDataLog) {
    val assigned = autoAssignSeries(log) ?: return
    val now = System.currentTimeMillis()
    if (now - lastSeriesHintAt > 1500) {
        lastSeriesHintAt = now
        showToast("已归入《${assigned.first.name}》${episodeLabel(assigned.second)}")
    }
}
