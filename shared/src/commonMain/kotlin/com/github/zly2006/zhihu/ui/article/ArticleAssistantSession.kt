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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.ai.CustomAiConfig
import com.github.zly2006.zhihu.ai.ReadingAssistant
import com.github.zly2006.zhihu.ai.ReadingAssistantEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 问答里的一条消息；[fromUser] 区分用户提问和助手回答。 */
data class ArticleAssistantMessage(
    val fromUser: Boolean,
    val text: String,
)

/**
 * 一篇文章的问答会话。
 *
 * 展示用的消息留在 [messages] 里；模型侧的对话历史由 [ReadingAssistant] 按 [id] 持有，
 * 这里不再保存第二份可改写的记录。取消或重置时只丢弃本页面正在生成的那一轮。
 */
@Stable
class ArticleAssistantSession internal constructor(val id: String) {
    val messages = mutableStateListOf<ArticleAssistantMessage>()
    var draft by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)

    /** 正在执行只读工具时的提示，没有工具在跑时为 null。 */
    var toolName by mutableStateOf<String?>(null)

    private var job: Job? = null

    /** 提交输入框里的问题；生成中重复提交会被忽略。收集被取消即取消后台生成。 */
    fun send(
        scope: CoroutineScope,
        assistant: ReadingAssistant,
        title: String,
        markdown: () -> String,
        config: CustomAiConfig,
    ) {
        val question = draft.trim()
        if (running || question.isEmpty()) return
        if (!config.isComplete) {
            error = "请先配置模型。"
            return
        }
        draft = ""
        error = null
        messages += ArticleAssistantMessage(fromUser = true, text = question)
        val answerIndex = messages.size
        messages += ArticleAssistantMessage(fromUser = false, text = "")
        running = true
        job = scope.launch {
            try {
                assistant.ask(id, title, markdown(), question, config).collect { event ->
                    when (event) {
                        is ReadingAssistantEvent.Text -> {
                            val current = messages.getOrNull(answerIndex) ?: return@collect
                            messages[answerIndex] = current.copy(text = current.text + event.delta)
                        }

                        is ReadingAssistantEvent.ToolStarted -> toolName = event.name
                        is ReadingAssistantEvent.ToolFinished -> toolName = null
                        is ReadingAssistantEvent.Failed -> error = event.message
                        ReadingAssistantEvent.Completed, ReadingAssistantEvent.Cancelled -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "阅读助手请求失败。"
            } finally {
                running = false
                toolName = null
                job = null
            }
        }
    }

    /** 停止当前生成；已经生成的部分保留。 */
    fun cancel() {
        job?.cancel()
    }

    /** 丢弃本页面的问答记录并清空模型侧历史。 */
    fun reset() {
        job?.cancel()
        job = null
        messages.clear()
        draft = ""
        error = null
        running = false
        toolName = null
    }
}

/**
 * 按账号和内容标识持有 [ArticleAssistantSession]。
 *
 * 一次阅读里来回切换回答时，之前的问答仍然在；离开的文章只保留最近若干个会话，
 * 生成中的会话不会被淘汰，避免把还在工作的状态删掉。
 */
class ArticleAssistantSessions {
    private val sessions = LinkedHashMap<String, ArticleAssistantSession>()

    fun of(id: String): ArticleAssistantSession {
        sessions.remove(id)?.let {
            sessions[id] = it
            return it
        }
        val created = ArticleAssistantSession(id)
        sessions[id] = created
        if (sessions.size > MAX_SESSIONS) {
            sessions.values.firstOrNull { !it.running }?.let { sessions.remove(it.id) }
        }
        return created
    }

    private companion object {
        const val MAX_SESSIONS = 20
    }
}
