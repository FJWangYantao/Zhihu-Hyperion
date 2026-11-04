/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import org.jetbrains.compose.resources.painterResource
import zhihu.shared.generated.resources.Res
import zhihu.shared.generated.resources.app_icon

internal const val LANDSCAPE_LIST_PANE_RATIO_KEY = "landscape_list_pane_ratio"
internal const val DEFAULT_LIST_PANE_RATIO = 1f / 3f

/** 阅读助手的展开状态；宽度不足三栏时它表示“抽屉是否展开”。 */
internal const val ASSISTANT_PANE_OPEN_KEY = "desktop_assistant_pane_open"

/** 阅读助手栏宽（dp）；只在三栏形态生效，抽屉形态按内容宽度另行计算。 */
internal const val ASSISTANT_PANE_WIDTH_KEY = "desktop_assistant_pane_width"
internal val MIN_LIST_PANE_WIDTH = 320.dp
internal val MIN_DETAIL_PANE_WIDTH = 480.dp
internal val LIST_DETAIL_DIVIDER_WIDTH = 16.dp
internal val MIN_READING_PANE_WIDTH = 560.dp
internal val MIN_ASSISTANT_PANE_WIDTH = 320.dp
internal val MAX_ASSISTANT_PANE_WIDTH = 480.dp
internal val DEFAULT_ASSISTANT_PANE_WIDTH = 360.dp

/** 三栏同时成立的最小窗口内容宽度：列表 320 + 正文 560 + 助手 320 + 两条分隔条。 */
internal val THREE_PANE_MIN_WIDTH = MIN_LIST_PANE_WIDTH + MIN_READING_PANE_WIDTH +
    MIN_ASSISTANT_PANE_WIDTH + LIST_DETAIL_DIVIDER_WIDTH * 2

/**
 * 正文阅读列的最大宽度。
 *
 * 16sp 中文一行约 16dp，720dp 去掉两侧 16dp 内边距后约 43 字，落在舒适行宽内。
 * 可用宽度不足时该上限不生效，窄窗口布局与未限宽时一致。
 */
internal val ReadingContentMaxWidth = 720.dp

internal fun NavDestination.isDetailPaneDestination(): Boolean = when (this) {
    is Article, is Pin -> true
    is CommentHolder -> article.isDetailPaneDestination()
    else -> false
}

internal fun canShowLandscapeListDetail(
    enabled: Boolean,
    deviceSupportsListDetail: Boolean,
    preferenceEnabled: Boolean,
    width: Dp,
    height: Dp,
): Boolean = enabled &&
    deviceSupportsListDetail &&
    preferenceEnabled &&
    width >= 840.dp &&
    width > height

internal fun normalizedListPaneWidth(
    availableWidth: Dp,
    requestedRatio: Float,
): Dp {
    val usableWidth = availableWidth - LIST_DETAIL_DIVIDER_WIDTH
    return (usableWidth * requestedRatio.coerceIn(0f, 1f)).coerceIn(
        minimumValue = MIN_LIST_PANE_WIDTH,
        maximumValue = usableWidth - MIN_DETAIL_PANE_WIDTH,
    )
}

internal data class ThreePaneWidths(
    val listPane: Dp,
    val readingPane: Dp,
    val assistantPane: Dp,
)

/**
 * 计算三栏各自的宽度；窗口放不下三栏时返回 null，调用方退回“双栏 + 抽屉”。
 *
 * 列表宽度沿用用户保存的比例，但必须同时给正文和助手让出最小宽度，所以要在 [normalizedListPaneWidth]
 * 之后再夹一次上界；门槛检查必须放在夹取之前，否则约束冲突会让 `coerceIn` 抛异常。
 */
internal fun threePaneWidths(
    availableWidth: Dp,
    listPaneRatio: Float,
    requestedAssistantWidth: Dp,
): ThreePaneWidths? {
    if (availableWidth < THREE_PANE_MIN_WIDTH) return null
    val dividers = LIST_DETAIL_DIVIDER_WIDTH * 2
    val listPane = normalizedListPaneWidth(availableWidth, listPaneRatio).coerceIn(
        minimumValue = MIN_LIST_PANE_WIDTH,
        maximumValue = availableWidth - dividers - MIN_READING_PANE_WIDTH - MIN_ASSISTANT_PANE_WIDTH,
    )
    val assistantPane = requestedAssistantWidth.coerceIn(
        minimumValue = MIN_ASSISTANT_PANE_WIDTH,
        maximumValue = minOf(
            availableWidth - dividers - MIN_READING_PANE_WIDTH - listPane,
            MAX_ASSISTANT_PANE_WIDTH,
        ),
    )
    return ThreePaneWidths(
        listPane = listPane,
        readingPane = availableWidth - dividers - listPane - assistantPane,
        assistantPane = assistantPane,
    )
}

@Composable
internal fun ListDetailDivider(
    onDrag: (Float) -> Unit,
    onAdjustBy: (Float) -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "list_detail_divider",
    contentDescription: String = "调整列表和详情宽度",
    increaseActionLabel: String = "增加列表宽度",
    decreaseActionLabel: String = "减小列表宽度",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .width(LIST_DETAIL_DIVIDER_WIDTH)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .testTag(testTag)
            .semantics {
                this.contentDescription = contentDescription
                customActions = listOf(
                    CustomAccessibilityAction(increaseActionLabel) {
                        onAdjustBy(0.05f)
                        onDragStopped()
                        true
                    },
                    CustomAccessibilityAction(decreaseActionLabel) {
                        onAdjustBy(-0.05f)
                        onDragStopped()
                        true
                    },
                )
            }.draggable(
                state = rememberDraggableState(onDelta = onDrag),
                orientation = Orientation.Horizontal,
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(if (hovered) 2.dp else 1.dp)
                .background(
                    if (hovered) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )
    }
}

@Composable
internal fun EmptyDetailPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("detail_pane_empty"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp).alpha(0.72f),
            )
            Text(
                text = "选择左侧内容",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
