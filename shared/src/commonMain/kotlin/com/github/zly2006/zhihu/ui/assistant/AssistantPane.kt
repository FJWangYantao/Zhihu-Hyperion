/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2026, eltavine <me@eltavine.com>
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

package com.github.zly2006.zhihu.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.ai.CustomAiConfigDialog
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.navigation.LocalNavigator

/**
 * 文章页接入助手所需的句柄。
 *
 * 由外壳按当前文章生成；平台不启用助手时 `LocalArticleAssistant` 为 null，文章页的入口和段评问 AI
 * 都不渲染。`publishContent` 只把已加载的正文写进内存，不会触发请求。
 */
internal class ArticleAssistantHandle(
    val key: String,
    val isOpen: Boolean,
    val onToggle: () -> Unit,
    val publishContent: (String) -> Unit,
    val askAbout: (quote: String) -> Unit,
)

internal val LocalArticleAssistant = staticCompositionLocalOf<ArticleAssistantHandle?> { null }

/**
 * AI 助手面板；三栏、右侧抽屉和独立对话页共用同一份内容。
 *
 * @param title 当前文章标题，用于说明提问对象。
 * @param session 与文章一一对应的会话。
 * @param state 会话的所有者，面板只通过它发请求和保存配置。
 * @param onClose 三栏/抽屉里收起面板；独立对话页没有收起按钮，由顶部返回代替。
 */
@Composable
internal fun AssistantPane(
    title: String,
    session: AssistantSession,
    state: ArticleAssistantState,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    var showConfig by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.reloadConfig() }
    val contentReady = state.isContentReady(session.key)
    val listState = rememberLazyListState()
    val messageItemCount = session.turns.size + if (session.streaming) 1 else 0
    val atBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(session.turns.size, session.streamingText) {
        if (atBottom && messageItemCount > 0) {
            listState.animateScrollToItem(messageItemCount - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .imePadding()
            .testTag("assistant_pane"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                AppIcons.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text("AI 助手", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = title.ifBlank { "当前内容" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("assistant_close")) {
                    Icon(AppIcons.Close, contentDescription = "收起 AI 助手")
                }
            }
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (session.turns.isEmpty() && !session.streaming) {
                AssistantEmptyState(
                    contentReady = contentReady,
                    configured = state.configured,
                    onSummarize = { state.summarize(session.key) },
                    onConfigure = { showConfig = true },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("assistant_messages"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(session.turns) { _, turn -> AssistantTurnItem(turn) }
                    if (session.streaming) {
                        item(key = "streaming") {
                            AssistantReply(
                                text = session.streamingText.ifBlank { "正在生成…" },
                                streaming = true,
                            )
                        }
                    }
                }
            }
        }

        session.quote?.let { quote ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = quote,
                        modifier = Modifier.weight(1f).testTag("assistant_quote"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { session.quote = null }, modifier = Modifier.size(32.dp)) {
                        Icon(AppIcons.Close, contentDescription = "移除引用")
                    }
                }
            }
        }

        session.error?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = error,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { state.retry(session.key) }, modifier = Modifier.testTag("assistant_retry")) {
                    Text("重试")
                }
            }
        }

        if (!state.configured) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = ASSISTANT_MISSING_CONFIG_MESSAGE,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { showConfig = true }) { Text("配置模型") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = session.draft,
                    onValueChange = { session.draft = it },
                    modifier = Modifier.weight(1f).testTag("assistant_input"),
                    placeholder = { Text("基于这篇内容提问") },
                    maxLines = 4,
                    enabled = contentReady,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { state.send(session.key, session.draft) }),
                )
                if (session.streaming) {
                    FilledTonalButton(
                        onClick = { state.cancel(session.key) },
                        modifier = Modifier.testTag("assistant_stop"),
                    ) {
                        Text("停止")
                    }
                } else {
                    FilledTonalButton(
                        onClick = { state.send(session.key, session.draft) },
                        enabled = session.draft.isNotBlank() && contentReady,
                        modifier = Modifier.testTag("assistant_send"),
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }

    if (showConfig) {
        CustomAiConfigDialog(
            initialConfig = state.loadConfig(),
            onDismissRequest = { showConfig = false },
            onSave = { config ->
                state.saveConfig(config)
                showConfig = false
            },
        )
    }
}

@Composable
private fun AssistantEmptyState(
    contentReady: Boolean,
    configured: Boolean,
    onSummarize: () -> Unit,
    onConfigure: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (contentReady) "基于当前正文提问，或先让 AI 总结要点" else "正文加载中，加载完成后即可提问",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (contentReady) {
            FilledTonalButton(
                onClick = if (configured) onSummarize else onConfigure,
                modifier = Modifier.testTag("assistant_summarize"),
            ) {
                Text(if (configured) "总结本文" else "配置模型")
            }
        }
    }
}

@Composable
private fun AssistantTurnItem(turn: AssistantTurn) {
    when (turn.role) {
        ASSISTANT_USER_ROLE -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                Text(
                    text = turn.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        else -> AssistantReply(text = turn.text, streaming = false)
    }
}

@Composable
private fun AssistantReply(text: String, streaming: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RenderMarkdownText(
            markdown = text,
            modifier = Modifier.fillMaxWidth(),
            enableScroll = false,
            isStreaming = streaming,
        )
        if (streaming) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
}

/** 单栏形态下的独立对话页；正文与草稿都来自同一份按文章隔离的会话。 */
@Composable
internal fun AssistantPage(
    contentKey: String,
    title: String,
    state: ArticleAssistantState,
) {
    val navigator = LocalNavigator.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "AI 助手" }) },
                navigationIcon = {
                    IconButton(onClick = { navigator.onNavigateBack() }) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.hasSession(contentKey)) {
                AssistantPane(
                    title = title,
                    session = state.session(contentKey),
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "内容已关闭，返回文章后可继续提问",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
