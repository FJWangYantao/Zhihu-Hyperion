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

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.net.toUri
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.ui.components.OpenImageDialog
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.luoTianYiUrlLauncher
import com.github.zly2006.zhihu.util.saveImageToGallery
import com.github.zly2006.zhihu.util.shareImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.koin.compose.koinInject

private const val WEBVIEW_ACTIVITY_CLASS = "com.github.zly2006.zhihu.WebviewActivity"

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : ExternalUrlOpener {
            override fun invoke(url: String) = luoTianYiUrlLauncher(context, url.toUri())
        }
    }
}

@Composable
actual fun rememberWebViewUrlOpener(): WebViewUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : WebViewUrlOpener {
            override fun invoke(url: String) {
                context.startActivity(
                    Intent(context, Class.forName(WEBVIEW_ACTIVITY_CLASS)).apply {
                        data = url.toUri()
                    },
                )
            }
        }
    }
}

actual val platformBottomBarItemLimit: Int? = 5

actual val isJvm: Boolean = false

actual val isNative: Boolean = false

actual val isAigcVoteSupported: Boolean = true

actual val isFeedQualityFilterSupported: Boolean = true

actual val isLegacyWebViewSupported: Boolean = true

actual val isInAppBrowserSupported: Boolean = true

actual val isArticleHtmlExportSupported: Boolean = true

actual val isArticleImageExportSupported: Boolean = true

actual val isReadingAssistantSupported: Boolean = false

actual val isPageTurnSupported: Boolean = true

actual val isAnswerSwipeSupported: Boolean = true

actual val isTopLevelBackToLauncherSupported: Boolean = true

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : SystemUrlOpener {
            override fun invoke(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }
}

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : ZhihuWebUrlOpener {
            override fun invoke(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setClassName(context, WEBVIEW_ACTIVITY_CLASS))
        }
    }
}

@Composable
actual fun rememberImagePreviewOpener(): ImagePreviewOpener {
    val openGallery = rememberImageGalleryOpener()
    return remember(openGallery) {
        object : ImagePreviewOpener {
            override fun invoke(url: String) = openGallery(listOf(url), 0)
        }
    }
}

@Composable
actual fun rememberImageGalleryOpener(): ImageGalleryOpener {
    val context = LocalContext.current
    return remember(context) {
        object : ImageGalleryOpener {
            override fun invoke(urls: List<String>, initialIndex: Int) {
                OpenImageDialog(context, urls, initialIndex).show()
            }
        }
    }
}

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val context = LocalContext.current
    val accountStore = koinInject<ZhihuAccountStore>()
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        object : ImageSaver {
            override fun invoke(url: String) {
                scope.launch {
                    saveImageToGallery(context, accountStore.client.httpClient(), url)
                }
            }
        }
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val context = LocalContext.current
    val accountStore = koinInject<ZhihuAccountStore>()
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        object : ImageSharer {
            override fun invoke(url: String) {
                scope.launch {
                    shareImage(context, accountStore.client.httpClient(), url)
                }
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard {
    val context = LocalContext.current
    return remember(context) {
        object : PlainTextClipboard {
            override fun invoke(label: String, text: String) = context.clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }
}

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = semantics { testTagsAsResourceId = true }

@Composable
actual fun rememberAppPrivateDirectory(): Path {
    val context = LocalContext.current.applicationContext
    return remember(context) { Path(context.filesDir.absolutePath) }
}

fun androidUserMessageSink(context: Context): UserMessageSink {
    val appContext = context.applicationContext
    val mainHandler = Handler(Looper.getMainLooper())

    fun showToast(
        message: String,
        duration: Int,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(appContext, message, duration).show()
        } else {
            mainHandler.post {
                Toast.makeText(appContext, message, duration).show()
            }
        }
    }

    return object : UserMessageSink {
        override fun showShortMessage(message: String) = showToast(message, Toast.LENGTH_SHORT)

        override fun showLongMessage(message: String) = showToast(message, Toast.LENGTH_LONG)
    }
}

@Composable
actual fun rememberUserMessageSink(): UserMessageSink {
    val context = LocalContext.current.applicationContext
    return remember(context) { androidUserMessageSink(context) }
}

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
) = PredictiveBackHandler(enabled = enabled) { progress ->
    // The progress flow is cancelled when the user abandons the gesture; it only completes on a committed back.
    var committed = false
    try {
        progress.collect { backEvent ->
            onProgress(backEvent.progress)
        }
        committed = true
        onBack()
    } finally {
        if (!committed) onCancel()
    }
}
