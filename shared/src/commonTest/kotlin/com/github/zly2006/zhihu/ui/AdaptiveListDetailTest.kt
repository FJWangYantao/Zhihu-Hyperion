/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
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

import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveListDetailTest {
    @Test
    fun defaultRatioMatchesReferenceAndRespectsMinimumWidths() {
        assertEquals((1264f / 3f).dp, normalizedListPaneWidth(1280.dp, DEFAULT_LIST_PANE_RATIO))
        assertEquals(MIN_LIST_PANE_WIDTH, normalizedListPaneWidth(840.dp, 0f))
        assertEquals(344.dp, normalizedListPaneWidth(840.dp, 1f))
    }

    @Test
    fun detailPaneDestinationClassificationExcludesQuestionLists() {
        assertTrue(Article(type = ArticleType.Article, id = 1).isDetailPaneDestination())
        assertTrue(Pin(id = 3).isDetailPaneDestination())
        assertFalse(Question(questionId = 2).isDetailPaneDestination())
        assertFalse(Search().isDetailPaneDestination())
        assertTrue(CommentHolder("comment", Article(type = ArticleType.Answer, id = 1)).isDetailPaneDestination())
        assertFalse(CommentHolder("comment", Question(questionId = 2)).isDetailPaneDestination())
    }

    @Test
    fun landscapeListDetailNeedsEnabledPreferenceAndTabletOrDesktopCapability() {
        assertTrue(
            canShowLandscapeListDetail(
                enabled = true,
                deviceSupportsListDetail = true,
                preferenceEnabled = true,
                width = 1000.dp,
                height = 600.dp,
            ),
        )
        assertFalse(
            canShowLandscapeListDetail(
                enabled = true,
                deviceSupportsListDetail = false,
                preferenceEnabled = true,
                width = 1000.dp,
                height = 600.dp,
            ),
        )
        assertFalse(
            canShowLandscapeListDetail(
                enabled = true,
                deviceSupportsListDetail = true,
                preferenceEnabled = false,
                width = 1000.dp,
                height = 600.dp,
            ),
        )
    }

    @Test
    fun threePaneReturnsNullBelowMinimumBudget() {
        assertNull(threePaneWidths(THREE_PANE_MIN_WIDTH - 1.dp, DEFAULT_LIST_PANE_RATIO, 360.dp))
        assertNotNull(threePaneWidths(THREE_PANE_MIN_WIDTH, DEFAULT_LIST_PANE_RATIO, 360.dp))
    }

    @Test
    fun threePaneKeepsReadingAtLeastMinimumForEveryRatio() {
        for (width in listOf(THREE_PANE_MIN_WIDTH, 1360.dp, 1440.dp, 1920.dp)) {
            for (ratio in listOf(0f, DEFAULT_LIST_PANE_RATIO, 0.6f, 1f)) {
                val widths = assertNotNull(threePaneWidths(width, ratio, 360.dp), "$width / $ratio")
                assertEquals(
                    width.value,
                    (widths.listPane + widths.readingPane + widths.assistantPane + LIST_DETAIL_DIVIDER_WIDTH * 2).value,
                    0.01f,
                )
                assertTrue(
                    widths.readingPane >= MIN_READING_PANE_WIDTH,
                    "$width / $ratio 让正文只剩 ${widths.readingPane}",
                )
                assertTrue(widths.assistantPane >= MIN_ASSISTANT_PANE_WIDTH)
            }
        }
    }

    @Test
    fun threePaneClampsAssistantWidthToBounds() {
        val narrow = assertNotNull(threePaneWidths(1360.dp, DEFAULT_LIST_PANE_RATIO, 360.dp))
        assertEquals(MIN_ASSISTANT_PANE_WIDTH, narrow.assistantPane)
        assertEquals(560.dp, narrow.readingPane)

        val roomy = assertNotNull(threePaneWidths(1440.dp, DEFAULT_LIST_PANE_RATIO, 360.dp))
        assertEquals(360.dp, roomy.assistantPane)

        assertEquals(MIN_ASSISTANT_PANE_WIDTH, assertNotNull(threePaneWidths(1440.dp, DEFAULT_LIST_PANE_RATIO, 0.dp)).assistantPane)
        assertEquals(MAX_ASSISTANT_PANE_WIDTH, assertNotNull(threePaneWidths(1920.dp, DEFAULT_LIST_PANE_RATIO, 900.dp)).assistantPane)
    }

    @Test
    fun threePaneRespectsSavedListRatio() {
        val atDefault = assertNotNull(threePaneWidths(1440.dp, DEFAULT_LIST_PANE_RATIO, 360.dp))
        assertEquals((1440f - LIST_DETAIL_DIVIDER_WIDTH.value) * DEFAULT_LIST_PANE_RATIO, atDefault.listPane.value, 0.01f)

        val atDouble = assertNotNull(threePaneWidths(1440.dp, 2f, 360.dp))
        assertEquals(1440.dp - LIST_DETAIL_DIVIDER_WIDTH * 2 - MIN_READING_PANE_WIDTH - MIN_ASSISTANT_PANE_WIDTH, atDouble.listPane)

        assertEquals(MIN_LIST_PANE_WIDTH, assertNotNull(threePaneWidths(1440.dp, 0f, 360.dp)).listPane)
    }
}
