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

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertTrue

/** 临时验证：Kotlin 侧通过真实 Node 后台进程完成工具回合和流式回答。 */
class PiReadingAssistantSmokeTest {
    @Test
    fun askStreamsAnswerAfterReadTool() = runBlocking {
        var requestCount = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requestCount += 1
            val hasToolResult = body.contains("\"role\":\"tool\"")
            exchange.responseHeaders.add("content-type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.responseBody.bufferedWriter()
            fun send(choice: String) {
                out.write("""data: {"id":"x","object":"chat.completion.chunk","choices":[{"index":0,$choice}]}""" + "\n\n")
                out.flush()
            }
            if (!hasToolResult) {
                send("""{"delta":{"role":"assistant","content":""},"finish_reason":null}""")
                send(
                    """{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read_current_article","arguments":""}}]},"finish_reason":null}""",
                )
                send("""{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]},"finish_reason":null}""")
                send("""{"delta":{},"finish_reason":"tool_calls"}""")
            } else {
                listOf("原文提到：", "**测试占位**", "。").forEach { piece ->
                    send("""{"delta":{"content":"$piece"},"finish_reason":null}""")
                }
                send("""{"delta":{},"finish_reason":"stop"}""")
            }
            out.write("data: [DONE]\n\n")
            out.flush()
            out.close()
        }
        server.start()
        val assistant = PiReadingAssistant()
        try {
            val events = withTimeout(120_000) {
                assistant.ask(
                    sessionId = "smoke:1",
                    title = "测试标题",
                    markdown = "# 标题\n\n正文第一段。",
                    question = "这篇文章说了什么？",
                    config = CustomAiConfig(
                        endpoint = "http://127.0.0.1:${server.address.port}/v1",
                        apiKey = "sk-test",
                        model = "test-model",
                    ),
                ).toList()
            }
            println("EVENTS: $events")
            println("REQUESTS: $requestCount")
            val text = events.filterIsInstance<ReadingAssistantEvent.Text>().joinToString("") { it.delta }
            assertTrue(events.any { it is ReadingAssistantEvent.ToolStarted && it.name == "read_current_article" })
            assertTrue(text.contains("**测试占位**"), "streamed text was: $text")
            assertTrue(events.last() is ReadingAssistantEvent.Completed)
            assertTrue(requestCount == 2)
        } finally {
            assistant.close()
            server.stop(0)
        }
    }
}
