package com.donut.mixfile.ui.routes.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donut.mixfile.server.core.objects.FileDataLog
import com.donut.mixfile.ui.component.common.MixDialogBuilder
import com.donut.mixfile.util.file.FileCard
import com.donut.mixfile.util.file.SeriesContent
import com.donut.mixfile.util.file.SeriesGroup
import com.donut.mixfile.util.file.activeSeriesGroups
import com.donut.mixfile.util.file.deleteSeriesGroup
import com.donut.mixfile.util.file.deleteUploadLog
import com.donut.mixfile.util.file.groupOf
import com.donut.mixfile.util.file.removeFileFromSeries
import com.donut.mixfile.util.file.seriesContent
import com.donut.mixfile.util.file.seriesExpandedGroups
import com.donut.mixfile.util.file.seriesGroupEnabled
import com.donut.mixfile.util.file.showFileInfoDialog
import com.donut.mixfile.util.file.toggleSeriesGroupExpanded
import com.donut.mixfile.util.file.ungroupedLogs
import com.donut.mixfile.util.file.updateSeriesGroup
import com.donut.mixfile.util.showToast

private const val UNGROUPED_ID = "__ungrouped__"

/**
 * 「上传历史」标题行 —— 右边那个「按剧集」小开关。
 * 默认关闭：关着的时候下面渲染的就是原版 FileCardList，逐像素和以前一样。
 */
@Composable
fun SeriesHistoryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "上传历史",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )
        Row(modifier = Modifier.weight(1f)) {}
        TextButton(onClick = { seriesGroupEnabled = !seriesGroupEnabled }) {
            Text(
                text = if (seriesGroupEnabled) "按剧集 ✓" else "按剧集",
                fontSize = 12.sp,
                color = if (seriesGroupEnabled) colorScheme.primary else colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 开启「按剧集」后的列表：一组一行，点一下就地展开/收起 */
@Composable
fun SeriesGroupedHistory() {
    val groups = activeSeriesGroups()
    val rest = ungroupedLogs()
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        groups.forEach { group ->
            val content = seriesContent(group)
            if (content.have == 0) return@forEach
            val expanded = seriesExpandedGroups.contains(group.id)
            SeriesGroupRow(
                content = content,
                expanded = expanded,
                onToggle = { toggleSeriesGroupExpanded(group.id) },
                onSettings = { showSeriesSettings(group) }
            )
            if (expanded) {
                content.episodes.forEach { (_, log) ->
                    Box2 {
                        FileCard(
                            fileDataLog = log,
                            onClick = { showFileInfoDialog(it) },
                            longClick = { showSeriesFileMenu(it) }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
        if (rest.isNotEmpty()) {
            val expanded = seriesExpandedGroups.contains(UNGROUPED_ID)
            SeriesSimpleRow(
                title = "未分组",
                summary = "${rest.size} 个文件",
                expanded = expanded,
                muted = true,
                onToggle = { toggleSeriesGroupExpanded(UNGROUPED_ID) }
            )
            if (expanded) {
                rest.asReversed().forEach { log ->
                    Box2 {
                        FileCard(
                            fileDataLog = log,
                            onClick = { showFileInfoDialog(it) },
                            longClick = { deleteUploadLog(it) }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** 组头（带集数/缺集统计） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesGroupRow(
    content: SeriesContent,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primary.copy(alpha = 0.05f))
            .combinedClickable(onClick = onToggle, onLongClick = onSettings)
            .padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "收起" else "展开",
            modifier = Modifier.size(18.dp),
            tint = colorScheme.onSurfaceVariant
        )
        Text(
            text = content.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        )
        Text(
            text = content.summary(),
            fontSize = 11.sp,
            color = if (content.missing.isNotEmpty()) colorScheme.error else colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 「未分组」这种简化组头 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesSimpleRow(
    title: String,
    summary: String,
    expanded: Boolean,
    muted: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primary.copy(alpha = 0.03f))
            .combinedClickable(onClick = onToggle, onLongClick = {})
            .padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "收起" else "展开",
            modifier = Modifier.size(18.dp),
            tint = colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            fontSize = 14.sp,
            color = if (muted) colorScheme.onSurfaceVariant else colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        )
        Text(text = summary, fontSize = 11.sp, color = colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 组内文件左右缩进一点点，视觉上归在组下面 */
@Composable
private fun Box2(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(start = 6.dp, end = 0.dp)) {
        content()
    }
}

/** 长按组头：改名 / 设总集数 / 解散（文件一个都不删） */
fun showSeriesSettings(group: SeriesGroup) {
    MixDialogBuilder("剧集设置", "改名字或填上总集数就能算出缺哪几集").apply {
        var name by mutableStateOf(group.name)
        var total by mutableStateOf(if (group.total > 0) group.total.toString() else "")
        setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "剧集名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(text = "总集数（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        setNeutralButton("解散分组") {
            deleteSeriesGroup(group)
            showToast("已解散，文件都还在「未分组」里")
            closeDialog()
        }
        setPositiveButton("保存") {
            updateSeriesGroup(
                group.copy(
                    name = name.trim().ifEmpty { group.name }.take(40),
                    total = total.toIntOrNull() ?: 0
                )
            )
            showToast("已保存")
            closeDialog()
        }
        setDefaultNegative()
        show()
    }
}

/** 组内长按单个文件：移出剧集 / 删除记录 */
fun showSeriesFileMenu(log: FileDataLog) {
    val groupName = groupOf(log)?.name ?: "未分组"
    MixDialogBuilder("文件操作", log.name).apply {
        setContent {
            Text(text = "所属剧集: $groupName", fontSize = 13.sp)
        }
        setNeutralButton("移出剧集") {
            removeFileFromSeries(log)
            showToast("已移出剧集")
            closeDialog()
        }
        setPositiveButton("删除记录") {
            deleteUploadLog(log)
            closeDialog()
        }
        setDefaultNegative()
        show()
    }
}
