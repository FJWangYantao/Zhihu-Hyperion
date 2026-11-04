/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
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

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.io.files.Path

expect val platformBottomBarItemLimit: Int?

expect val isJvm: Boolean

expect val isNative: Boolean

expect val isAigcVoteSupported: Boolean

/** 信息流按赞数、关注数等规则标记或隐藏低质量内容；未支持的平台忽略质量屏蔽设置。 */
expect val isFeedQualityFilterSupported: Boolean

/** 旧版 WebView 正文渲染；只有 Android 提供，其他平台始终使用 Compose Markdown。 */
expect val isLegacyWebViewSupported: Boolean

/** 外部链接在可设定工具栏颜色的应用内浏览器（Android Custom Tabs）打开；其他平台交给系统浏览器。 */
expect val isInAppBrowserSupported: Boolean

expect val isLiteVariant: Boolean

expect val isBlocklistNlpSupported: Boolean

expect val isSentenceSimilaritySupported: Boolean

expect val isArticleHtmlExportSupported: Boolean

expect val isArticleImageExportSupported: Boolean

/** 文章问答助手：依赖随桌面应用打包的 Pi 后台运行时，其他平台暂不提供。 */
expect val isReadingAssistantSupported: Boolean

expect val isPageTurnSupported: Boolean

expect val isAnswerSwipeSupported: Boolean

/** 一级页面返回时回到桌面：只有 Android 的系统返回在应用不处理时会回到桌面，桌面端 ESC 和 iOS 没有这层语义。 */
expect val isTopLevelBackToLauncherSupported: Boolean

enum class UserMessageDuration {
    Short,
    Long,
}

interface UserMessageSink {
    fun showShortMessage(message: String)

    fun showLongMessage(message: String) = showShortMessage(message)

    fun showMessage(
        message: String,
        duration: UserMessageDuration = UserMessageDuration.Short,
    ) {
        when (duration) {
            UserMessageDuration.Short -> showShortMessage(message)
            UserMessageDuration.Long -> showLongMessage(message)
        }
    }
}

@Composable
expect fun rememberUserMessageSink(): UserMessageSink

interface SystemUrlOpener {
    operator fun invoke(url: String)
}

interface ZhihuWebUrlOpener {
    operator fun invoke(url: String)
}

interface ImagePreviewOpener {
    operator fun invoke(url: String)
}

interface ExternalUrlOpener :
    SystemUrlOpener,
    ZhihuWebUrlOpener,
    ImagePreviewOpener

/** Opens a third-party page inside the app's embedded WebView. */
interface WebViewUrlOpener {
    operator fun invoke(url: String)
}

interface ImageGalleryOpener {
    operator fun invoke(urls: List<String>, initialIndex: Int)
}

interface ImageSaver {
    operator fun invoke(url: String)
}

interface ImageSharer {
    operator fun invoke(url: String)
}

interface PlainTextClipboard {
    operator fun invoke(label: String, text: String)
}

expect fun Modifier.exportTestTagsForUiAutomation(): Modifier

@Composable
expect fun rememberAppPrivateDirectory(): Path

@Composable
expect fun rememberExternalUrlOpener(): ExternalUrlOpener

@Composable
expect fun rememberWebViewUrlOpener(): WebViewUrlOpener

@Composable
expect fun rememberSystemUrlOpener(): SystemUrlOpener

@Composable
expect fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener

@Composable
expect fun rememberImagePreviewOpener(): ImagePreviewOpener

@Composable
expect fun rememberImageGalleryOpener(): ImageGalleryOpener

@Composable
expect fun rememberImageSaver(): ImageSaver

@Composable
expect fun rememberImageSharer(): ImageSharer

@Composable
expect fun rememberPlainTextClipboard(): PlainTextClipboard

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)

@Composable
expect fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
)

/** macOS 关闭主窗口时是否退出应用的偏好键，设置页与窗口宿主共用。 */
const val MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY = "macosQuitOnWindowClose"
