# 内置 AI 助手的可行性调研

本文评估在 Zhihu-Hyperion 中新增 AI 助手的难度、已有底座、工作量与主要风险。结论基于 `711e6ea` 的源码。

## 结论

云端 AI 助手属于中等偏低难度。项目已有 `:feature:ai` 模块，包含 OpenAI 兼容流式请求、SSE 解析和配置界面；文章页已接入「总结本文」。缺的是通用聊天页、多轮会话、历史持久化和全局入口。

端侧大模型是另一类工程，不建议现在做。Lite 包目标小于 5 MB，当前只有 Android Full 带 ONNX 语义模型，而且该模型只做向量编码，不能生成文本。

## 已有能力

| 能力 | 位置 | 说明 |
| --- | --- | --- |
| OpenAI 兼容流式请求 | `feature/ai/src/commonMain/kotlin/com/github/zly2006/zhihu/ai/CustomAi.kt` | `streamChatCompletion` 返回 `Flow<String>`，支持 `[DONE]` 和非流式兜底 |
| 接口配置 | `CustomAiConfigDialog.kt` | 地址、API Key、模型和连接测试 |
| 配置持久化 | `CustomAiConfigFile` + `custom_ai.json` | 不进 WebDAV 同步，也不进 Android 云备份 |
| 文章总结 | `shared/.../viewmodel/ArticleViewModel.kt`、`ui/article/ArticleSheets.kt` | 知乎直答与自定义模型二选一 |
| 总结入口 | `shared/.../ui/article/ArticleActionsMenu.kt` | 「更多」菜单中的「总结本文」 |
| 流式 Markdown | `third_party/markdown/markdown-renderer/.../Markdown.kt`、`MarkdownStreaming.kt` | `Markdown(isStreaming = true)` 与 `MarkdownConfig.LlmStreaming` |
| 多平台 HTTP 引擎 | `core/account/.../AccountHttpClientEngine.*.kt` | Android OkHttp、JVM CIO、Native Darwin |
| 依赖注入 | `core/account/.../AccountModule.kt` | `HttpClientEngine` 由 Koin 提供 |
| 数据库 | `core/database/...` | Room 已在 Android、JVM、Native 注册 |

## 缺失能力

- 通用聊天页面、消息气泡和输入框。
- 多轮会话状态、上下文拼装、token 预算和截断。
- 会话与消息持久化；当前没有聊天表，也没有通用数据库。
- AI 设置页与设置搜索入口；现在只有文章页内的配置对话框。
- 全局入口；现在只能从文章「总结本文」进入。
- 选中文本回调；正文渲染器只暴露 `enableSelection`，没有 `onSelectionChange`。

## 关键约束

1. **模块边界**：`feature:*` 不能依赖 `shared` 或另一个 feature。聊天页放在 `feature:ai` 时，需要通过参数和 Koin 取得引擎、配置与导航回调；放在 `shared` 则不受此限制。
2. **渲染器依赖**：`core:markdown` 用 `implementation` 引入 `markdown-renderer`，`shared` 不能直接使用 `Markdown`。需要新增直接依赖，或在 `core:markdown` 增加流式包装。
3. **选中文本**：内置渲染器不提供选中回调。划词提问可以先接现有划线弹层；任意选区需要改造内置渲染器。
4. **密钥存储**：`custom_ai.json` 是明文，虽然排除在备份和 WebDAV 之外，但没有加密。
5. **体积**：云端方案几乎不增加安装包；端侧模型会与 Lite 目标冲突。
6. **接口稳定性**：知乎直答 `ai_ingress` 是非公开接口。聊天不应建立在该接口上，只保留现有总结用途。

## 分档与工作量

估算按熟悉该仓库的开发者计算，包含四平台验证与 review，不包含产品设计和 Prompt 调优。

| 档位 | 范围 | 估算 |
| --- | --- | --- |
| A 文章内问答 | 单轮或短多轮，围绕当前文章，不存历史 | 2-4 人日 |
| B 全局助手 | 独立聊天页、多轮会话、本地历史、设置页 | 8-12 人日 |
| C 深度集成 | 划词提问、评论代写、工具调用、内容跳转 | 15-20 人日 |
| D 端侧模型 | 内置小模型或本地推理 | 30 人日以上 |

## 推荐 MVP

1. 会话模型先用 JSON 文件，字段包含角色、内容和时间；稳定后再迁 Room。
2. `AiChatViewModel` 持有消息列表、加载状态和 `Job`；离开页面时取消请求。
3. 聊天页复用 `Markdown(isStreaming = true)` 和现有输入框、剪贴板组件。
4. 在文章操作菜单加入「问问 AI」，携带 `articleId` 和标题。
5. 把配置对话框升级为设置项，补 `commonTest` 和 AVD 截图。

## 涉及文件

- 路由：`core/navigation/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/NavDestination.kt`
- 宿主注册：`shared/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/ZhihuMain.kt`
- 设置入口：`shared/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/AccountSettingScreen.kt`
- 网络：`feature/ai/src/commonMain/kotlin/com/github/zly2006/zhihu/ai/CustomAi.kt`
- 会话存储：`core/database` 新数据库，并在三个 `DatabaseModule` 注册

## 验收

- 两个 flavor 分别构建。
- `./gradlew jvmUnitTests` 与 `:feature:ai` 的 `commonTest`。
- `./gradlew ktlintFormat` 与 detekt。
- Android AVD 真实页面与截图。
- Desktop、macOS、iOS 至少各启动一次。
- 按仓库要求完成 review 后再提交。
