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

package com.github.zly2006.zhihu.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.github.zly2006.zhihu.platform.SettingsStore
import java.awt.GraphicsEnvironment
import kotlin.math.roundToInt

// 窗口键只服务桌面窗口本身，不进入跨平台设置契约。
internal const val DESKTOP_WINDOW_WIDTH_KEY = "desktop_window_width"
internal const val DESKTOP_WINDOW_HEIGHT_KEY = "desktop_window_height"
internal const val DESKTOP_WINDOW_X_KEY = "desktop_window_x"
internal const val DESKTOP_WINDOW_Y_KEY = "desktop_window_y"
internal const val DESKTOP_WINDOW_MAXIMIZED_KEY = "desktop_window_maximized"

/** 首次启动的窗口尺寸；1360dp 宽下正文 560dp 与 AI 助手 320dp 可以同时成立。 */
internal val DefaultDesktopWindowSize = DpSize(1360.dp, 860.dp)
private val MinDesktopWindowSize = DpSize(1024.dp, 700.dp)

@Composable
internal fun rememberDesktopWindowState(settings: SettingsStore): WindowState {
    val density = LocalDensity.current
    val screenBounds = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    }
    val screenSize = remember(density, screenBounds) {
        with(density) {
            DpSize(screenBounds.width.toDp(), screenBounds.height.toDp())
        }
    }
    val restoredSize = remember(settings, screenSize) {
        val storedWidth = settings
            .getInt(DESKTOP_WINDOW_WIDTH_KEY, DefaultDesktopWindowSize.width.value.roundToInt()).dp
        val storedHeight = settings
            .getInt(DESKTOP_WINDOW_HEIGHT_KEY, DefaultDesktopWindowSize.height.value.roundToInt()).dp
        DpSize(
            storedWidth.coerceIn(minOf(MinDesktopWindowSize.width, screenSize.width), screenSize.width),
            storedHeight.coerceIn(minOf(MinDesktopWindowSize.height, screenSize.height), screenSize.height),
        )
    }
    val restoredPosition = remember(settings, screenSize, restoredSize) {
        val hasStoredPosition = settings.contains(DESKTOP_WINDOW_X_KEY) && settings.contains(DESKTOP_WINDOW_Y_KEY)
        // 只按主显示器校验：换显示器或改分辨率后旧坐标可能整体落屏外，这时退回居中，
        // 而不是把窗口还原到看不见的位置。
        val storedX = settings.getInt(DESKTOP_WINDOW_X_KEY, 0).dp
        val storedY = settings.getInt(DESKTOP_WINDOW_Y_KEY, 0).dp
        val fitsPrimaryScreen = hasStoredPosition &&
            storedX >= 0.dp &&
            storedY >= 0.dp &&
            storedX + restoredSize.width <= screenSize.width &&
            storedY + restoredSize.height <= screenSize.height
        if (fitsPrimaryScreen) WindowPosition(storedX, storedY) else WindowPosition(Alignment.Center)
    }
    return rememberWindowState(
        size = restoredSize,
        position = restoredPosition,
        placement = if (settings.getBoolean(DESKTOP_WINDOW_MAXIMIZED_KEY, false)) {
            WindowPlacement.Maximized
        } else {
            WindowPlacement.Floating
        },
    )
}

internal fun saveDesktopWindowState(settings: SettingsStore, state: WindowState) {
    // 最大化时保留上一次的还原尺寸，否则下次启动会把最大化后的屏幕尺寸当成窗口尺寸。
    if (state.placement == WindowPlacement.Floating) {
        settings.putInt(DESKTOP_WINDOW_WIDTH_KEY, state.size.width.value.roundToInt())
        settings.putInt(DESKTOP_WINDOW_HEIGHT_KEY, state.size.height.value.roundToInt())
        (state.position as? WindowPosition.Absolute)?.let { position ->
            settings.putInt(DESKTOP_WINDOW_X_KEY, position.x.value.roundToInt())
            settings.putInt(DESKTOP_WINDOW_Y_KEY, position.y.value.roundToInt())
        }
    }
    settings.putBoolean(DESKTOP_WINDOW_MAXIMIZED_KEY, state.placement == WindowPlacement.Maximized)
}
