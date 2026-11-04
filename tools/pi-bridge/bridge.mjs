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

// 桌面阅读助手的后台进程：从 stdin 读 JSON 行命令，向 stdout 写 JSON 行事件。
// 只有这一层接触 Pi 核心与模型密钥；Kotlin 侧不直接引用 TypeScript 包。
import { Agent } from '@earendil-works/pi-agent-core'
import { createModels, createProvider } from '@earendil-works/pi-ai'
import { openAICompletionsApi } from '@earendil-works/pi-ai/api/openai-completions.lazy'
import { createInterface } from 'node:readline'

const PROTOCOL_VERSION = 1
const PROVIDER_ID = 'custom'
const DEFAULT_CONTEXT_WINDOW = 128_000
const DEFAULT_MAX_TOKENS = 8_192

/** @type {Map<string, Session>} */
const sessions = new Map()

/**
 * @typedef {object} Session
 * @property {string} id
 * @property {Agent | undefined} agent
 * @property {string} configKey
 * @property {string} articleTitle
 * @property {string} articleMarkdown
 * @property {boolean} busy
 * @property {string | null} requestId
 * @property {boolean} cancelled
 */

/** 写一行事件。stdout 只承载协议，任何日志都必须走 stderr。 */
function emit(event) {
  process.stdout.write(`${JSON.stringify(event)}\n`)
}

function fail(requestId, message) {
  emit({ type: 'error', requestId, message })
}

/** 用户可能填到 `/v1` 或 `/v1/chat/completions`；pi-ai 会在 baseUrl 后自己拼路径。 */
function normalizeBaseUrl(endpoint) {
  return endpoint.trim().replace(/\/+$/, '').replace(/\/chat\/completions$/, '')
}

function normalizeConfig(raw) {
  const config = raw ?? {}
  return {
    endpoint: String(config.endpoint ?? '').trim(),
    apiKey: String(config.apiKey ?? '').trim(),
    model: String(config.model ?? '').trim(),
  }
}

function configKey(config) {
  return `${config.endpoint}\u0000${config.apiKey}\u0000${config.model}`
}

/**
 * 用用户配置的 OpenAI 兼容服务构造一个只含单个模型的 provider。
 * 密钥只存在于本进程内存，且只作为请求头发给用户自己配置的地址。
 */
function buildAgent(session, config) {
  const baseUrl = normalizeBaseUrl(config.endpoint)
  const model = {
    id: config.model,
    name: config.model,
    api: 'openai-completions',
    provider: PROVIDER_ID,
    baseUrl,
    reasoning: false,
    input: ['text'],
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    contextWindow: DEFAULT_CONTEXT_WINDOW,
    maxTokens: DEFAULT_MAX_TOKENS,
  }
  const provider = createProvider({
    id: PROVIDER_ID,
    name: '自定义模型',
    baseUrl,
    // 本地服务（如 Ollama）允许空密钥；空密钥时仍然报告已配置，由服务端决定是否拒绝。
    auth: {
      apiKey: {
        name: '自定义模型 API Key',
        resolve: async () => ({ auth: config.apiKey ? { apiKey: config.apiKey } : {} }),
      },
    },
    models: [model],
    api: openAICompletionsApi(),
  })
  const models = createModels()
  models.setProvider(provider)

  const articleTool = {
    name: 'read_current_article',
    label: '读取当前文章',
    description:
      '读取用户当前正在阅读的内容全文（Markdown）。需要核对原文细节、引用原句或确认是否存在某段内容时调用。' +
      '返回的是用户本地已加载的正文快照，不会访问网络。',
    parameters: { type: 'object', properties: {}, additionalProperties: false },
    execute: async () => {
      if (!session.articleMarkdown.trim()) {
        return { content: [{ type: 'text', text: '当前没有可读取的正文。' }], details: {}, isError: true }
      }
      return { content: [{ type: 'text', text: session.articleMarkdown }], details: {} }
    },
  }

  const agent = new Agent({
    initialState: {
      systemPrompt: systemPrompt(session.articleTitle, session.articleMarkdown),
      model,
      tools: [articleTool],
      messages: [],
    },
    streamFn: models.streamSimple.bind(models),
    identity: { attribution: { name: 'Zhihu-Hyperion', url: 'https://github.com/zly2006/zhihu-hyperion' } },
    // 阅读助手没有副作用工具，串行执行即可，避免并发回填打乱事件顺序。
    toolExecution: 'sequential',
  })

  agent.subscribe((event) => {
    const requestId = session.requestId
    if (!requestId) return
    if (event.type === 'message_update' && event.assistantMessageEvent?.type === 'text_delta') {
      emit({ type: 'text', requestId, delta: event.assistantMessageEvent.delta })
    } else if (event.type === 'tool_execution_start') {
      emit({ type: 'tool_start', requestId, name: event.toolName })
    } else if (event.type === 'tool_execution_end') {
      emit({ type: 'tool_end', requestId, name: event.toolName, isError: Boolean(event.isError) })
    }
  })

  return { agent, configKey: configKey(config) }
}

function systemPrompt(title, markdown) {
  const heading = title.trim() || '（无标题）'
  return [
    '你是 Zhihu-Hyperion 阅读器里的阅读助手，帮助用户理解、追问和核对当前正在阅读的内容。',
    '',
    `当前内容标题：《${heading}》`,
    '',
    '当前内容全文（Markdown）：',
    '<content>',
    markdown.trim() || '（正文尚未加载）',
    '</content>',
    '',
    '回答要求：',
    '- 只依据上面的内容回答；内容里没有的信息直接说明无法从当前内容确认，不要编造。',
    '- 需要引用时使用 Markdown 引用块，并尽量贴近原文。',
    '- 使用中文，保持简洁；除非用户要求，不要复述整篇文章。',
  ].join('\n')
}

async function handleAsk(message) {
  const requestId = String(message.requestId ?? '')
  const sessionId = String(message.sessionId ?? '')
  if (!requestId || !sessionId) {
    fail(requestId, '请求缺少 requestId 或 sessionId。')
    return
  }
  const question = String(message.question ?? '').trim()
  if (question.length === 0) {
    fail(requestId, '问题为空。')
    return
  }
  const config = normalizeConfig(message.config)
  if (!config.endpoint || !config.model) {
    fail(requestId, '还没有配置可用的模型地址和模型名称。')
    return
  }
  const articleTitle = String(message.article?.title ?? '')
  const articleMarkdown = String(message.article?.markdown ?? '')

  const session = sessions.get(sessionId) ?? { id: sessionId, busy: false, requestId: null, cancelled: false }
  sessions.set(sessionId, session)
  if (session.busy) {
    fail(requestId, '上一个问题还在回答中，请先停止。')
    return
  }

  session.busy = true
  session.requestId = requestId
  session.cancelled = false
  try {
    // 正文、配置或会话归属变化时丢弃旧执行历史，避免把另一篇文章的上下文带进来。
    if (
      !session.agent ||
      session.configKey !== configKey(config) ||
      session.articleTitle !== articleTitle ||
      session.articleMarkdown !== articleMarkdown
    ) {
      session.agent?.reset()
      session.articleTitle = articleTitle
      session.articleMarkdown = articleMarkdown
      const built = buildAgent(session, config)
      session.agent = built.agent
      session.configKey = built.configKey
    }

    await session.agent.prompt(question)

    const lastAssistant = [...session.agent.state.messages].reverse().find((entry) => entry.role === 'assistant')
    if (lastAssistant?.stopReason === 'error') {
      fail(requestId, lastAssistant.errorMessage || '模型请求失败。')
    } else if (session.cancelled) {
      emit({ type: 'cancelled', requestId })
    } else {
      emit({ type: 'done', requestId })
    }
  } catch (error) {
    fail(requestId, error instanceof Error ? error.message : String(error))
  } finally {
    session.busy = false
    session.requestId = null
  }
}

function handleCancel(message) {
  const session = sessions.get(String(message.sessionId ?? ''))
  if (!session?.agent || !session.busy) return
  session.cancelled = true
  session.agent.abort()
}

function shutdown() {
  for (const session of sessions.values()) {
    session.agent?.abort()
  }
  process.exit(0)
}

const input = createInterface({ input: process.stdin, terminal: false })
input.on('line', (line) => {
  if (!line.trim()) return
  let message
  try {
    message = JSON.parse(line)
  } catch {
    process.stderr.write(`pi-bridge: 无法解析的命令：${line.slice(0, 200)}\n`)
    return
  }
  switch (message.type) {
    case 'ask':
      void handleAsk(message)
      break
    case 'cancel':
      handleCancel(message)
      break
    case 'shutdown':
      shutdown()
      break
    default:
      process.stderr.write(`pi-bridge: 未知命令 ${String(message.type)}\n`)
  }
})
input.on('close', shutdown)
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)

emit({ type: 'ready', protocol: PROTOCOL_VERSION })
