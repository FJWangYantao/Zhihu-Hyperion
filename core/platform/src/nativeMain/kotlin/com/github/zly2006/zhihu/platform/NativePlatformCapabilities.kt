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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.util.suspendRunCatching
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.koin.compose.koinInject
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithBytes
import kotlin.time.Clock

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener = rememberExternalUrlOpener()

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener = rememberExternalUrlOpener()

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val accountStore = koinInject<ZhihuAccountStore>()
    return remember(scope, userMessages, accountStore) {
        object : ImageSaver {
            override fun invoke(url: String) {
                scope.launch {
                    suspendRunCatching {
                        val image = downloadNativeImage(accountStore, url)
                        storeNativeImage(image.bytes, image.extension)
                    }.onSuccess(userMessages::showShortMessage).onFailure { error ->
                        userMessages.showShortMessage("保存失败: ${error.message}")
                    }
                }
            }
        }
    }
}

internal class NativeImage(
    val bytes: ByteArray,
    val extension: String,
)

internal suspend fun downloadNativeImage(
    accountStore: ZhihuAccountStore,
    imageUrl: String,
): NativeImage {
    val bytes = accountStore.client
        .httpClient()
        .get(imageUrl)
        .body<ByteArray>()
    val extension = imageUrl
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('.', "")
        .takeIf { it.length in 2..5 } ?: "jpg"
    return NativeImage(bytes, extension)
}

/** 把下载好的图片存到用户能找到的位置，返回给用户看的提示。 */
internal expect suspend fun storeNativeImage(bytes: ByteArray, extension: String): String

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), size.toULong()) }
}

/**
 * 盖在主窗口上的界面（iOS 的看图页）显示自己的提示条：主窗口的 [SnackbarUserMessageHost] 被挡在下面，
 * 消息若仍进全局队列，用户在看图页里看不到“已保存”之类的结果。
 */
internal val LocalNativeUserMessageSink = staticCompositionLocalOf<UserMessageSink?> { null }

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = LocalNativeUserMessageSink.current ?: SnackbarUserMessages

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard = remember {
    object : PlainTextClipboard {
        override fun invoke(label: String, text: String) = copyNativePlainText(text)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) =
    BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = PlatformBackHandler(enabled = enabled, onBack = onBack)

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

@Composable
actual fun rememberAppPrivateDirectory(): Path = remember { Path(nativeAppPrivateDirectoryPath()) }

actual val isLiteVariant: Boolean = false

actual val isJvm: Boolean = false

actual val isNative: Boolean = true

actual val isBlocklistNlpSupported: Boolean = false

actual val isSentenceSimilaritySupported: Boolean = false

actual val isArticleHtmlExportSupported: Boolean = false

actual val isArticleImageExportSupported: Boolean = false

actual val isReadingAssistantSupported: Boolean = false

actual val isPageTurnSupported: Boolean = false

actual val isAnswerSwipeSupported: Boolean = !nativeIsDesktop

actual val isTopLevelBackToLauncherSupported: Boolean = false
