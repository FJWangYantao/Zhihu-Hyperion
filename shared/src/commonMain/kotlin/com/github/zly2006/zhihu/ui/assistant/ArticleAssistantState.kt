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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.ai.ChatMessage
import com.github.zly2006.zhihu.ai.CustomAiConfig
import com.github.zly2006.zhihu.ai.CustomAiConfigFile
import com.github.zly2006.zhihu.ai.streamChatCompletion
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val ASSISTANT_USER_ROLE = "user"
internal const val ASSISTANT_MODEL_ROLE = "assistant"

internal const val ASSISTANT_MISSING_CONFIG_MESSAGE = "请先配置模型地址和模型名称"
internal const val ASSISTANT_WAITING_CONTENT_MESSAGE = "正文尚未加载完成"
internal const val ASSISTANT_EMPTY_REPLY_MESSAGE = "模型没有返回内容"

private const val ASSISTANT_SYSTEM_PROMPT =
    "你是知乎阅读助手。用户会先给你一篇知乎内容的正文，然后基于它提问。" +
        "只依据正文回答，正文没有依据时明确说明，不要编造；用简体中文，可以使用 Markdown。"
private const val ASSISTANT_SUMMARY_PROMPT = "请总结这篇内容的要点。"
private const val ASSISTANT_QUOTE_PREFIX = "关于下面这段引用：\n"

/** 会话里的一条消息，`role` 直接沿用 OpenAI 协议的 `user` / `assistant`。 */
internal data class AssistantTurn(val role: String, val text: String)

/**
 * 单篇内容的助手会话。
 *
 * 会话按内容分别持有，切换文章只是切换展示的会话对象；正在流式的旧请求即使取消失败，
 * 也只会写回自己这个对象，不会串到另一篇文章。
 */
internal class AssistantSession(val key: String) {
    val turns = mutableStateListOf<AssistantTurn>()
    var draft by mutableStateOf("")
    var quote by mutableStateOf<String?>(null)
    var streamingText by mutableStateOf("")
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    internal var job: Job? = null
    internal var requestId = 0L
}

/**
 * 文章 AI 助手的状态与请求入口。
 *
 * 正文由文章页发布进来，只在内存里按 key 保存，不落盘；打开面板、切换文章都不会产生请求，
 * 只有总结、发送和重试会各自发出一次请求。
 */
internal class ArticleAssistantState(
    private val scope: CoroutineScope,
    private val engine: HttpClientEngine,
    private val configFile: CustomAiConfigFile,
) {
    private val contents = mutableStateMapOf<String, String>()
    private val sessions = mutableMapOf<String, AssistantSession>()

    /** 模型配置是否完整；配置弹窗保存后立即更新，面板据此在「配置」和「输入」之间切换。 */
    var configured by mutableStateOf(configFile.load().isComplete)
        private set

    fun session(key: String): AssistantSession = sessions.getOrPut(key) { AssistantSession(key) }

    fun hasSession(key: String): Boolean = sessions.containsKey(key)

    fun isContentReady(key: String): Boolean = !contents[key].isNullOrBlank()

    fun publishContent(key: String, markdown: String) {
        if (markdown.isNotBlank()) contents[key] = markdown
    }

    fun reloadConfig() {
        configured = configFile.load().isComplete
    }

    fun loadConfig(): CustomAiConfig = configFile.load()

    fun saveConfig(config: CustomAiConfig) {
        configFile.save(config)
        configured = config.isComplete
    }

    /** 划线段评里点「问 AI」时只记录引用，不发送；引用在下次发送时并入提问。 */
    fun setQuote(key: String, quote: String) {
        session(key).quote = quote
    }

    fun cancel(key: String) {
        sessions[key]?.job?.cancel()
    }

    fun summarize(key: String) {
        send(key, ASSISTANT_SUMMARY_PROMPT)
    }

    /** 重发最后一条提问；失败后残留的半成品回复会一并丢弃，避免同一句话记两遍。 */
    fun retry(key: String) {
        val session = sessions[key] ?: return
        if (session.streaming) return
        val lastUserIndex = session.turns.indexOfLast { it.role == ASSISTANT_USER_ROLE }
        if (lastUserIndex < 0) return
        val prompt = session.turns[lastUserIndex].text
        while (session.turns.size > lastUserIndex) {
            session.turns.removeAt(session.turns.lastIndex)
        }
        session.error = null
        send(key, prompt)
    }

    fun send(key: String, prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        val session = session(key)
        val config = configFile.load()
        if (!config.isComplete) {
            configured = false
            session.error = ASSISTANT_MISSING_CONFIG_MESSAGE
            return
        }
        configured = true
        val article = contents[key]
        if (article.isNullOrBlank()) {
            session.error = ASSISTANT_WAITING_CONTENT_MESSAGE
            return
        }
        val quote = session.quote
        session.turns += AssistantTurn(
            ASSISTANT_USER_ROLE,
            if (quote.isNullOrBlank()) text else "$ASSISTANT_QUOTE_PREFIX$quote\n\n$text",
        )
        session.draft = ""
        session.quote = null
        val messages = buildList {
            add(ChatMessage("system", ASSISTANT_SYSTEM_PROMPT))
            // 正文每次请求都整体带上，且只出现这一次：历史消息里不再重复正文。
            add(ChatMessage(ASSISTANT_USER_ROLE, article))
            session.turns.forEach { add(ChatMessage(it.role, it.text)) }
        }
        startRequest(session, config, messages)
    }

    private fun startRequest(session: AssistantSession, config: CustomAiConfig, messages: List<ChatMessage>) {
        session.job?.cancel()
        val requestId = ++session.requestId
        session.error = null
        session.streamingText = ""
        session.streaming = true
        val reply = StringBuilder()
        session.job = scope.launch {
            var completedNormally = false
            try {
                streamChatCompletion(engine, config, messages).collect { chunk ->
                    if (session.requestId != requestId) return@collect
                    reply.append(chunk)
                    session.streamingText = reply.toString()
                }
                completedNormally = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (session.requestId == requestId) {
                    session.error = e.message ?: "请求失败"
                }
            } finally {
                finishRequest(session, requestId, reply.toString(), completedNormally)
            }
        }
    }

    /** 只在仍是同一次请求时收尾；被新请求顶替的旧响应整条丢弃，不写入半成品。 */
    private fun finishRequest(
        session: AssistantSession,
        requestId: Long,
        reply: String,
        completedNormally: Boolean,
    ) {
        if (session.requestId != requestId) return
        if (reply.isNotEmpty()) {
            session.turns += AssistantTurn(ASSISTANT_MODEL_ROLE, reply)
        } else if (completedNormally && session.error == null) {
            session.error = ASSISTANT_EMPTY_REPLY_MESSAGE
        }
        session.streamingText = ""
        session.streaming = false
        session.job = null
    }
}
