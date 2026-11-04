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

import com.github.zly2006.zhihu.ai.CustomAiConfigFile
import com.github.zly2006.zhihu.util.TextDocumentStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleAssistantStateTest {
    @Test
    fun sendWithoutContentReportsWaitingAndMakesNoRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val state = state(requests)

        state.send("article-a", "这篇讲了什么")

        assertEquals(0, requests.size)
        assertEquals(ASSISTANT_WAITING_CONTENT_MESSAGE, state.session("article-a").error)
        assertTrue(state.session("article-a").turns.isEmpty())
    }

    @Test
    fun sendWithoutConfigReportsMissingConfigAndMakesNoRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val state = state(requests, configured = false)
        state.publishContent("article-a", "# 正文")

        state.send("article-a", "这篇讲了什么")

        assertEquals(0, requests.size)
        assertEquals(ASSISTANT_MISSING_CONFIG_MESSAGE, state.session("article-a").error)
        assertTrue(state.session("article-a").turns.isEmpty())
    }

    @Test
    fun sessionsForDifferentArticlesDoNotMixTurns() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val state = state(requests)
        state.publishContent("article-a", "# 文章 A")
        state.publishContent("article-b", "# 文章 B")

        state.send("article-a", "A 的问题")
        state.session("article-a").job?.join()

        assertEquals(2, state.session("article-a").turns.size)
        assertTrue(state.session("article-b").turns.isEmpty())
        assertEquals(1, requests.size)
    }

    @Test
    fun cancelledRequestKeepsPartialTextInItsOwnSession() = runTest {
        val body = ByteChannel(autoFlush = true)
        val state = ArticleAssistantState(
            this,
            MockEngine {
                respond(
                    body,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            },
            configuredFile(),
        )
        state.publishContent("article-a", "# 文章 A")
        state.send("article-a", "A 的问题")
        val job = state.session("article-a").job

        // 只写分片不发 [DONE]，模拟模型还在生成时被取消。
        body.writeStringUtf8(partialChunk("半截"))
        state.awaitStreamingText("article-a", "半截")
        assertEquals("半截", state.session("article-a").streamingText)

        state.cancel("article-a")
        job?.join()

        val session = state.session("article-a")
        assertEquals(ASSISTANT_MODEL_ROLE, session.turns.last().role)
        assertEquals("半截", session.turns.last().text)
        assertFalse(session.streaming)
    }

    @Test
    fun requestsIncludeArticleMarkdownOnceAndFullHistory() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val state = state(requests)
        state.publishContent("article-a", "# 只在请求里出现一次的正文")

        state.send("article-a", "第一个问题")
        state.session("article-a").job?.join()
        state.send("article-a", "第二个问题")
        state.session("article-a").job?.join()

        assertEquals(2, requests.size)
        for (request in requests) {
            val body = request.bodyText()
            assertEquals(1, body.split("# 只在请求里出现一次的正文").size - 1, "正文应整体出现且只出现一次：$body")
        }
        val secondBody = requests[1].bodyText()
        assertTrue(secondBody.contains("第一个问题"), secondBody)
        assertTrue(secondBody.contains("第二个问题"), secondBody)
        assertTrue(secondBody.contains(MODEL_REPLY), secondBody)
    }

    @Test
    fun summarizeSendsExactlyOneRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val state = state(requests)
        state.publishContent("article-a", "# 正文")

        state.summarize("article-a")
        state.session("article-a").job?.join()

        assertEquals(1, requests.size, "session.error=${state.session("article-a").error}")
        assertTrue(requests.single().bodyText().contains("总结"))
    }

    private fun TestScope.state(
        requests: MutableList<HttpRequestData>,
        configured: Boolean = true,
    ): ArticleAssistantState = ArticleAssistantState(
        this,
        MockEngine { request ->
            requests += request
            respond(streamedChunk(MODEL_REPLY))
        },
        configuredFile(configured),
    )

    private fun configuredFile(configured: Boolean = true) = CustomAiConfigFile(
        object : TextDocumentStore {
            private var text: String? = if (configured) {
                """{"endpoint":"https://example.invalid/v1","model":"test-model"}"""
            } else {
                null
            }

            override fun readText() = text

            override fun writeText(text: String) {
                this.text = text
            }

            override fun delete() {
                text = null
            }
        },
    )

    private fun streamedChunk(text: String): String =
        """data: {"choices":[{"delta":{"content":"$text"}}]}""" + "\n\n" + "data: [DONE]\n\n"

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private companion object {
        const val MODEL_REPLY = "模型回复"
    }
}
