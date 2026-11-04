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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.desktop.copyDesktopPlainText
import com.github.zly2006.zhihu.desktop.desktopZhihuDataDir
import com.github.zly2006.zhihu.desktop.openDesktopExternalUrl
import com.github.zly2006.zhihu.desktop.saveImageToDownloads
import com.github.zly2006.zhihu.util.suspendRunCatching
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.koin.compose.koinInject

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

@Composable
actual fun rememberAppPrivateDirectory(): Path = remember { Path(desktopZhihuDataDir().absolutePath) }

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener = remember {
    object : ExternalUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberWebViewUrlOpener(): WebViewUrlOpener = remember {
    object : WebViewUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener = remember {
    object : SystemUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener = remember {
    object : ZhihuWebUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberImagePreviewOpener(): ImagePreviewOpener = remember {
    object : ImagePreviewOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberImageGalleryOpener(): ImageGalleryOpener = remember {
    object : ImageGalleryOpener {
        override fun invoke(urls: List<String>, initialIndex: Int) {
            if (urls.isNotEmpty()) {
                openDesktopExternalUrl(urls[initialIndex.coerceIn(0, urls.lastIndex)])
            }
        }
    }
}

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val store = koinInject<ZhihuAccountStore>()
    return remember(scope, userMessages, store) {
        object : ImageSaver {
            override fun invoke(url: String) {
                scope.launch {
                    suspendRunCatching {
                        store.saveImageToDownloads(url, "image")
                    }.onSuccess { file ->
                        userMessages.showShortMessage("已保存图片: ${file.absolutePath}")
                    }.onFailure { error ->
                        userMessages.showShortMessage("保存失败: ${error.message}")
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val userMessages = rememberUserMessageSink()
    return remember(userMessages) {
        object : ImageSharer {
            override fun invoke(url: String) {
                runCatching {
                    copyDesktopPlainText(url)
                    userMessages.showShortMessage("已复制图片链接")
                }.onFailure { error ->
                    userMessages.showShortMessage("分享失败: ${error.message}")
                }
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard =
    remember {
        object : PlainTextClipboard {
            override fun invoke(label: String, text: String) {
                runCatching { copyDesktopPlainText(text) }
            }
        }
    }

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = SnackbarUserMessages

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = PlatformBackHandler(enabled = enabled, onBack = onBack)

actual val isLiteVariant: Boolean = false

actual val platformBottomBarItemLimit: Int? = null

actual val isJvm: Boolean = true

actual val isNative: Boolean = false

actual val isAigcVoteSupported: Boolean = true

actual val isFeedQualityFilterSupported: Boolean = true

actual val isLegacyWebViewSupported: Boolean = false

actual val isInAppBrowserSupported: Boolean = false

actual val isBlocklistNlpSupported: Boolean = false

actual val isSentenceSimilaritySupported: Boolean = false

actual val isArticleHtmlExportSupported: Boolean = true

actual val isArticleImageExportSupported: Boolean = true

actual val isReadingAssistantSupported: Boolean = true

actual val isPageTurnSupported: Boolean = false

actual val isAnswerSwipeSupported: Boolean = false

actual val isTopLevelBackToLauncherSupported: Boolean = false
