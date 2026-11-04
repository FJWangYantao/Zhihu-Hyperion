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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.icons.AppIcon
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.reading.ReadingQueueSourceRegistry
import com.github.zly2006.zhihu.reading.ReadingStartRequest
import com.github.zly2006.zhihu.reading.TtsState
import com.github.zly2006.zhihu.reading.articleSpeechText
import com.github.zly2006.zhihu.reading.hasReadableFields
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.reading.loadReadingPlaybackSpeed
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.rememberArticleSpeechToggler
import com.github.zly2006.zhihu.reading.rememberArticleTtsState
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.ShareAction
import com.github.zly2006.zhihu.ui.components.rememberShareActionExecutor
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.materialkolor.ktx.harmonize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val VoteUpNeutralContent = Color(0xFF3671EE)
private val VoteUpNeutralContentDark = Color(0xFF628DF7)

@Composable
internal fun voteUpNeutralContent() = if (ThemeManager.isDarkTheme()) VoteUpNeutralContentDark else VoteUpNeutralContent

@Composable
internal fun voteUpNeutralContentDuo3() = if (ThemeManager.isDarkTheme()) {
    VoteUpNeutralContentDark.harmonize(MaterialTheme.colorScheme.primary)
} else {
    VoteUpNeutralContent.harmonize(MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleActionsMenu(
    article: Article,
    viewModel: ArticleViewModel,
    answerQueueFallbackProvider: (suspend (limit: Int) -> List<Article>)? = null,
    showMenu: Boolean,
    onDismissRequest: () -> Unit,
    onSummaryRequest: () -> Unit,
    /** 平台没有阅读助手运行时（例如移动端）时传 null，入口不显示。 */
    onAssistantRequest: (() -> Unit)? = null,
    onAigcFlagRequest: () -> Unit,
    onExportRequest: () -> Unit,
    onSetImmersiveDoubleTap: () -> Unit = {},
) {
    val ttsState = rememberArticleTtsState()
    val toggleSpeech = rememberArticleSpeechToggler()
    val readingPlayer = rememberReadingPlayerController()
    val readingPlayerState by readingPlayer.state
    val readingSettings = koinInject<SettingsStore>()
    val openArticleInBrowser = rememberArticleBrowserOpener()
    val executeShareAction = rememberShareActionExecutor()
    val coroutineScope = rememberCoroutineScope()
    val readingQueueSources = koinInject<ReadingQueueSourceRegistry>()
    val readingItem = ReadingQueueItem(
        contentType = when (article.type) {
            ArticleType.Answer -> ReadingContentType.Answer
            ArticleType.Article -> ReadingContentType.Article
        },
        id = article.id,
        title = viewModel.title,
        author = viewModel.authorName,
        questionId = viewModel.questionId.takeIf { it > 0 },
        bodyHtml = viewModel.content.takeIf(String::isNotBlank),
        publishedAt = viewModel.createdAt,
        updatedAt = viewModel.updatedAt,
        voteUpCount = viewModel.voteUpCount,
        commentCount = viewModel.commentCount,
    )
    val readingPreferences = loadReadingPreferences(readingSettings)
    val readingPlaybackSpeed = loadReadingPlaybackSpeed(readingSettings)
    val hasReadingSession = readingPlayerState.hasSession
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    @Composable
    fun MenuActionButton(
        icon: @Composable () -> Unit,
        text: String,
        enabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick: () -> Unit,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onClick() },
            shape = RoundedCornerShape(12.dp),
            color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(24.dp)) {
                    icon()
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                )
            }
        }
    }

    @Composable
    fun MenuActionButton(
        icon: AppIcon,
        text: String,
        enabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick: () -> Unit,
    ) {
        MenuActionButton(
            icon = {
                Icon(
                    icon = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
            },
            text = text,
            enabled = enabled,
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            onClick = onClick,
        )
    }

    @Composable
    fun Content() {
        MenuActionButton(
            icon = {
                if (isReadingPlayerSupported && hasReadingSession) {
                    Icon(
                        AppIcons.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    when (ttsState) {
                        TtsState.Initializing, TtsState.Uninitialized -> CircularProgressIndicator(
                            modifier = Modifier.height(24.dp),
                            strokeWidth = 2.dp,
                        )

                        else -> Icon(
                            if (!isReadingPlayerSupported && ttsState.isSpeaking) {
                                AppIcons.VolumeOff
                            } else {
                                AppIcons.VolumeUp
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            text = if (isReadingPlayerSupported) {
                if (hasReadingSession) "停止朗读" else "开始连续朗读"
            } else if (ttsState.isSpeaking) {
                "停止朗读"
            } else {
                "开始朗读"
            },
            enabled = if (isReadingPlayerSupported) {
                hasReadingSession || readingItem.hasReadableFields(readingPreferences)
            } else {
                ttsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing)
            },
            onClick = {
                onDismissRequest()
                if (isReadingPlayerSupported) {
                    if (hasReadingSession) {
                        readingPlayer.stop()
                    } else {
                        coroutineScope.launch {
                            // Home feed mixes unrelated content; the question navigator owns answer order.
                            val useQuestionAnswerOrder = article.type == ArticleType.Answer &&
                                article.readingQueueSourceId?.startsWith("home:") == true
                            val originQueue = readingQueueSources.queueStartingAt(
                                current = readingItem,
                                sourceId = article.readingQueueSourceId.takeUnless { useQuestionAnswerOrder },
                                limit = readingPreferences.queueLimit,
                            )
                            val queue = if (
                                article.type == ArticleType.Answer &&
                                (useQuestionAnswerOrder || originQueue.size < readingPreferences.queueLimit) &&
                                readingPreferences.queueLimit > 1
                            ) {
                                val fallbackAfterCurrent = try {
                                    val fallbackArticles = answerQueueFallbackProvider
                                        ?.invoke(readingPreferences.queueLimit - 1)
                                        ?: viewModel.answerNextIds.map { answerId ->
                                            Article(
                                                type = ArticleType.Answer,
                                                id = answerId,
                                                title = viewModel.title,
                                            )
                                        }
                                    fallbackArticles.map { fallback ->
                                        ReadingQueueItem(
                                            contentType = ReadingContentType.Answer,
                                            id = fallback.id,
                                            title = fallback.title.ifBlank { viewModel.title },
                                            author = fallback.authorName,
                                            questionId = viewModel.questionId.takeIf { it > 0 },
                                        )
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    Log.w("ArticleActionsMenu", "Failed to load the remaining reading queue", error)
                                    emptyList()
                                }
                                readingQueueSources.queueStartingAt(
                                    current = readingItem,
                                    sourceId = article.readingQueueSourceId.takeUnless { useQuestionAnswerOrder },
                                    limit = readingPreferences.queueLimit,
                                    fallbackAfterCurrent = fallbackAfterCurrent,
                                )
                            } else {
                                originQueue
                            }
                            readingPlayer.start(
                                ReadingStartRequest(
                                    queue = queue,
                                    preferences = readingPreferences,
                                    sourceId = article.readingQueueSourceId,
                                    playbackSpeed = readingPlaybackSpeed,
                                ),
                            )
                        }
                    }
                } else if (ttsState.isSpeaking) {
                    toggleSpeech(viewModel.title, viewModel.content)
                } else if (ttsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing)) {
                    viewModel.viewModelScope.launch {
                        try {
                            withContext(Dispatchers.Default) {
                                val textToRead = articleSpeechText(viewModel.title, viewModel.content)
                                withContext(Dispatchers.Main) {
                                    if (textToRead.isNotBlank()) {
                                        toggleSpeech(viewModel.title, viewModel.content)
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("ArticleActionsMenu", "Failed to prepare the text to read aloud", e)
                        }
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.Share,
            text = "分享",
            onClick = {
                onDismissRequest()
                executeShareAction(
                    ShareAction.Share,
                    article,
                    articleActionText(article, viewModel.questionId, viewModel.title, viewModel.authorName),
                )
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.Comment,
            text = "总结本文",
            onClick = {
                onDismissRequest()
                onSummaryRequest()
            },
        )

        if (onAssistantRequest != null) {
            Spacer(modifier = Modifier.height(12.dp))
            MenuActionButton(
                icon = AppIcons.AutoAwesome,
                text = "问 AI",
                onClick = {
                    onDismissRequest()
                    onAssistantRequest()
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.Flag,
            text = "标记疑似 AIGC",
            onClick = {
                onDismissRequest()
                onAigcFlagRequest()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.ContentCopy,
            text = "复制链接",
            onClick = {
                onDismissRequest()
                executeShareAction(
                    ShareAction.CopyLink,
                    article,
                    articleActionText(article, viewModel.questionId, viewModel.title, viewModel.authorName),
                )
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.FilterCenterFocus,
            text = "进入沉浸式",
            onClick = {
                onDismissRequest()
                onSetImmersiveDoubleTap()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.Download,
            text = listOfNotNull(
                "Markdown",
                "图片".takeIf { isArticleImageExportSupported },
                "HTML".takeIf { isArticleHtmlExportSupported },
            ).joinToString("、", prefix = "导出文章（", postfix = "）"),
            onClick = {
                onDismissRequest()
                onExportRequest()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.Share,
            text = "分享 Markdown 正文",
            onClick = {
                onDismissRequest()
                executeShareAction(ShareAction.DirectShare, article, viewModel.convertToMarkdown())
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = AppIcons.DesktopWindows,
            text = "在电脑中打开（我计划使用浏览器插件实现，还在写，点击后请手动前往收藏夹打开）",
            onClick = {
                coroutineScope.launch {
                    openArticleInBrowser(article)
                    onDismissRequest()
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showMenu) {
        MyModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Content()
            }
        }
    }
}
