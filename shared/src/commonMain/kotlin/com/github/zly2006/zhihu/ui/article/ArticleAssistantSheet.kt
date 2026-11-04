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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.ai.CustomAiConfig
import com.github.zly2006.zhihu.ai.ReadingAssistant
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet

const val ARTICLE_ASSISTANT_INPUT_TAG = "articleAssistant:input"
const val ARTICLE_ASSISTANT_SEND_TAG = "articleAssistant:send"
const val ARTICLE_ASSISTANT_STOP_TAG = "articleAssistant:stop"
const val ARTICLE_ASSISTANT_EDIT_MODEL_TAG = "articleAssistant:editModel"

/**
 * 文章问答弹层：输入问题、流式显示回答、可随时停止。
 *
 * 流式阶段逐字追加的纯文本不重新解析 Markdown；一轮结束后才交给 Markdown 渲染器，
 * 避免每个分片都重建一次文档结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleAssistantSheet(
    showDialog: Boolean,
    session: ArticleAssistantSession,
    assistant: ReadingAssistant,
    config: CustomAiConfig,
    articleTitle: String,
    articleMarkdown: () -> String,
    onConfigureModel: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!showDialog) return
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val lastMessageLength = session.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(session.messages.size, lastMessageLength) {
        if (session.messages.isNotEmpty()) listState.scrollToItem(session.messages.lastIndex)
    }
    MyModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("问 AI", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConfigureModel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ARTICLE_ASSISTANT_EDIT_MODEL_TAG),
                ) {
                    Text(
                        text = config.model.ifBlank { "配置模型" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (session.messages.isEmpty()) {
                Text(
                    "助手只读取当前正文，不会访问你的账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(session.messages) { index, message ->
                    AssistantMessage(
                        message = message,
                        streaming = session.running && index == session.messages.lastIndex && !message.fromUser,
                    )
                }
            }
            val toolName = session.toolName
            if (toolName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在读取正文…", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            val error = session.error
            if (!error.isNullOrBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = session.draft,
                    onValueChange = { session.draft = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ARTICLE_ASSISTANT_INPUT_TAG),
                    placeholder = { Text("基于当前正文提问") },
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        if (session.running) {
                            session.cancel()
                        } else {
                            session.send(
                                scope = scope,
                                assistant = assistant,
                                title = articleTitle,
                                markdown = articleMarkdown,
                                config = config,
                            )
                        }
                    },
                    modifier = Modifier.testTag(if (session.running) ARTICLE_ASSISTANT_STOP_TAG else ARTICLE_ASSISTANT_SEND_TAG),
                ) {
                    Icon(
                        icon = if (session.running) AppIcons.Stop else AppIcons.Send,
                        contentDescription = if (session.running) "停止生成" else "发送",
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ArticleAssistantMessage,
    streaming: Boolean,
) {
    if (message.fromUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else if (streaming || message.text.isEmpty()) {
        Text(
            text = message.text.ifEmpty { "…" },
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        RenderMarkdownText(
            markdown = message.text,
            enableScroll = false,
        )
    }
}
