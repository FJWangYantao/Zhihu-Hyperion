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

package com.github.zly2006.zhihu.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.zly2006.zhihu.desktopZhihuModules
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.SnackbarUserMessageHost
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.DesktopZhihuMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

fun main() {
    System.setProperty("java.awt.im.style", "below-the-spot")
    startKoin { modules(desktopZhihuModules()) }
    application {
        val settings = KoinPlatform.getKoin().get<SettingsStore>()
        val windowState = rememberDesktopWindowState(settings)
        // 用户可能在拖动窗口时直接结束进程，所以除了关闭时保存，也在停手后补一次落盘。
        LaunchedEffect(windowState, settings) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .distinctUntilChanged()
                .collectLatest {
                    delay(600)
                    saveDesktopWindowState(settings, windowState)
                }
        }
        Window(
            onCloseRequest = {
                saveDesktopWindowState(settings, windowState)
                exitApplication()
            },
            state = windowState,
            title = "Zhihu-Hyperion",
            icon = painterResource("desktop-icon.png"),
        ) {
            ZhihuTheme {
                SnackbarUserMessageHost {
                    DesktopZhihuMain()
                }
            }
        }
    }
}
