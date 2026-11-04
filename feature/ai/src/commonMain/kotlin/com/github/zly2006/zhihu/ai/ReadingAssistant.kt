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

package com.github.zly2006.zhihu.ai

import kotlinx.coroutines.flow.Flow

/** 阅读助手一次提问产生的事件。 */
sealed interface ReadingAssistantEvent {
    /** 模型新增的一小段正文。 */
    data class Text(val delta: String) : ReadingAssistantEvent

    /** 模型调用了只读工具；界面据此提示当前进度。 */
    data class ToolStarted(val name: String) : ReadingAssistantEvent

    data class ToolFinished(
        val name: String,
        val isError: Boolean,
    ) : ReadingAssistantEvent

    data object Completed : ReadingAssistantEvent

    /** 用户停止生成；已经生成的文字仍然有效。 */
    data object Cancelled : ReadingAssistantEvent

    data class Failed(val message: String) : ReadingAssistantEvent
}

/**
 * 文章问答能力。
 *
 * 由平台运行时执行 [ask] 并持有该会话的执行历史：同一个 [sessionId] 的连续提问共享上下文，
 * 不同会话（账号、内容类型、内容 ID）互不影响。取消收集 [ask] 返回的 Flow 即取消本次生成。
 */
interface ReadingAssistant {
    fun ask(
        sessionId: String,
        title: String,
        markdown: String,
        question: String,
        config: CustomAiConfig,
    ): Flow<ReadingAssistantEvent>
}
