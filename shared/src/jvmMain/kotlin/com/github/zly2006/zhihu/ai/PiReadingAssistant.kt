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

import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 桌面端阅读助手：用一个 Node 后台进程执行 Pi 核心，Kotlin 侧只负责进程生命周期和事件投影。
 *
 * 进程延迟到第一次提问才启动；退出应用时通过 shutdown hook 结束它。
 * 进程内的会话历史由 Pi 持有，这里不做第二份可改写的记录。
 */
class PiReadingAssistant : ReadingAssistant {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channels = ConcurrentHashMap<String, Channel<ReadingAssistantEvent>>()
    private val writeLock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private var process: Process? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ close() }, "zhihu-pi-bridge-shutdown"))
    }

    override fun ask(
        sessionId: String,
        title: String,
        markdown: String,
        question: String,
        config: CustomAiConfig,
    ): Flow<ReadingAssistantEvent> = flow {
        val requestId = UUID.randomUUID().toString()
        val channel = Channel<ReadingAssistantEvent>(Channel.BUFFERED)
        channels[requestId] = channel
        var finished = false
        var bridge: Process? = null
        try {
            bridge = withContext(Dispatchers.IO) { start() }
            val command = buildJsonObject {
                put("type", "ask")
                put("requestId", requestId)
                put("sessionId", sessionId)
                put("question", question)
                put(
                    "article",
                    buildJsonObject {
                        put("title", title)
                        put("markdown", markdown)
                    },
                )
                put(
                    "config",
                    buildJsonObject {
                        put("endpoint", config.endpoint)
                        put("apiKey", config.apiKey)
                        put("model", config.model)
                    },
                )
            }
            withContext(Dispatchers.IO) { writeCommand(command, bridge) }
            for (event in channel) {
                emit(event)
                if (event is ReadingAssistantEvent.Completed ||
                    event is ReadingAssistantEvent.Cancelled ||
                    event is ReadingAssistantEvent.Failed
                ) {
                    finished = true
                }
            }
        } finally {
            channels.remove(requestId)
            channel.close()
            // 收集被取消（用户停止、切换文章或退出页面）时通知后台放弃本次生成。
            if (!finished) {
                val cancel = buildJsonObject {
                    put("type", "cancel")
                    put("sessionId", sessionId)
                }
                val target = bridge
                scope.launch { runCatching { target?.let { writeCommand(cancel, it) } } }
            }
        }
    }

    fun close() {
        val current = synchronized(writeLock) {
            process.also { process = null }
        } ?: return
        runCatching {
            current.outputStream.write("""{"type":"shutdown"}""".toByteArray() + "\n".toByteArray())
            current.outputStream.flush()
        }
        if (!current.waitFor(2, TimeUnit.SECONDS)) current.destroyForcibly()
        scope.cancel()
    }

    private fun start(): Process = synchronized(writeLock) {
        process?.takeIf { it.isAlive }?.let { return it }
        val directory = bridgeDirectory()
        val started = try {
            ProcessBuilder("node", File(directory, "bridge.mjs").absolutePath)
                .directory(directory)
                .start()
        } catch (e: IOException) {
            throw IllegalStateException("未找到可用的 Node.js（需要 22.19 或更高版本），阅读助手无法启动。", e)
        }
        process = started
        scope.launch { drainStderr(started) }
        scope.launch { readEvents(started) }
        started
    }

    private fun drainStderr(process: Process) {
        runCatching {
            process.errorStream.bufferedReader().forEachLine { line ->
                if (line.isNotBlank()) Log.w("PiReadingAssistant", line)
            }
        }
    }

    private fun readEvents(process: Process) {
        try {
            process.inputStream.bufferedReader().forEachLine(::handleLine)
        } catch (e: IOException) {
            Log.w("PiReadingAssistant", "阅读助手事件流中断", e)
        } finally {
            // 进程退出或崩溃：等待中的提问必须得到明确失败，而不是一直停在生成中。
            channels.values.forEach { channel ->
                channel.trySend(ReadingAssistantEvent.Failed("阅读助手后台进程已退出，请重试。"))
                channel.close()
            }
            synchronized(writeLock) {
                if (this.process === process) this.process = null
            }
        }
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        val event = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        val type = event.string("type") ?: return
        val requestId = event.string("requestId") ?: return
        val channel = channels[requestId] ?: return
        when (type) {
            "text" -> channel.trySend(ReadingAssistantEvent.Text(event.string("delta").orEmpty()))
            "tool_start" -> channel.trySend(ReadingAssistantEvent.ToolStarted(event.string("name").orEmpty()))
            "tool_end" -> channel.trySend(
                ReadingAssistantEvent.ToolFinished(
                    name = event.string("name").orEmpty(),
                    isError = event["isError"]?.jsonPrimitive?.contentOrNull == "true",
                ),
            )

            "done" -> {
                channel.trySend(ReadingAssistantEvent.Completed)
                channel.close()
            }

            "cancelled" -> {
                channel.trySend(ReadingAssistantEvent.Cancelled)
                channel.close()
            }

            "error" -> {
                channel.trySend(ReadingAssistantEvent.Failed(event.string("message") ?: "阅读助手请求失败。"))
                channel.close()
            }

            else -> Unit
        }
    }

    private fun writeCommand(command: JsonObject, process: Process) {
        synchronized(writeLock) {
            process.outputStream.write((command.toString() + "\n").toByteArray())
            process.outputStream.flush()
        }
    }

    /**
     * 定位后台脚本目录：发布包从应用资源目录读取，开发时从仓库的 `tools/pi-bridge` 读取。
     * `ZHIHU_PI_BRIDGE_DIR` 只用于本地调试。
     */
    private fun bridgeDirectory(): File {
        val candidates = buildList {
            System.getenv("ZHIHU_PI_BRIDGE_DIR")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            System.getProperty("compose.application.resources.dir")?.let { add(File(it, "pi-bridge")) }
            var directory: File? = File(System.getProperty("user.dir")).absoluteFile
            repeat(4) {
                val current = directory ?: return@repeat
                add(File(current, "tools/pi-bridge"))
                add(File(current, "pi-bridge"))
                directory = current.parentFile
            }
        }
        return candidates.firstOrNull { File(it, "bridge.mjs").isFile }
            ?: throw IllegalStateException("找不到阅读助手的后台脚本（tools/pi-bridge/bridge.mjs）。")
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
