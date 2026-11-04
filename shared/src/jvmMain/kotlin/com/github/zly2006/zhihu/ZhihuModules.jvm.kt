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

package com.github.zly2006.zhihu

import com.github.zly2006.zhihu.account.accountModule
import com.github.zly2006.zhihu.ai.CustomAiConfigFile
import com.github.zly2006.zhihu.ai.PiReadingAssistant
import com.github.zly2006.zhihu.ai.ReadingAssistant
import com.github.zly2006.zhihu.data.dataModule
import com.github.zly2006.zhihu.data.databaseModule
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.notification.desktopNotificationSettingsStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.update.InstalledBuild
import com.github.zly2006.zhihu.update.UpdateTarget
import com.github.zly2006.zhihu.util.AtomicTextFile
import com.github.zly2006.zhihu.viewmodel.AccountWebClientProvider
import com.github.zly2006.zhihu.viewmodel.MobileClientProvider
import com.github.zly2006.zhihu.viewmodel.WebDavConfigFile
import com.github.zly2006.zhihu.viewmodel.filter.HomeFeedFilter
import kotlinx.io.files.Path
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.util.Properties

/** 桌面进程的全部 Koin 绑定，由 desktopApp 的组合根启动。 */
fun desktopZhihuModules(): List<Module> = listOf(
    accountModule(Path(desktopZhihuDataFile("account.json").path)),
    databaseModule(desktopZhihuDataFile("content-filter.db"), desktopZhihuDataFile("local-content.db")),
    dataModule,
    zhihuSharedModule,
    module {
        single { desktopSettingsStore(desktopZhihuDataFile("settings.properties")) }
        single {
            val buildInfo = Properties()
            checkNotNull(Thread.currentThread().contextClassLoader?.getResourceAsStream("zhihu-build.properties")) {
                "zhihu-build.properties is missing; desktopApp's writeBuildInfo task generates it"
            }.use(buildInfo::load)
            val isX64 = System.getProperty("os.arch") in setOf("amd64", "x86_64")
            val osName = System.getProperty("os.name").lowercase()
            InstalledBuild(
                versionName = buildInfo.getProperty("versionName"),
                versionCode = buildInfo.getProperty("versionCode").toInt(),
                commit = buildInfo.getProperty("commit"),
                // macOS ships the Kotlin/Native app, so CI publishes no JVM package for it.
                target = when {
                    isX64 && osName.startsWith("windows") -> UpdateTarget.WINDOWS_X64
                    isX64 && osName.startsWith("linux") -> UpdateTarget.LINUX_X64
                    else -> null
                },
            )
        }
        single { WebDavConfigFile(AtomicTextFile(Path(desktopZhihuDataFile("webdav.json").path))) }
        single { CustomAiConfigFile(AtomicTextFile(Path(desktopZhihuDataFile("custom_ai.json").path))) }
        single { PiReadingAssistant() } onClose { it?.close() }
        single<ReadingAssistant> { get<PiReadingAssistant>() }
        single { desktopNotificationSettingsStore() }
        single { HomeFeedFilter(get(), get(), get()) }
        single<MobileClientProvider> { AccountWebClientProvider(get()) }
    },
)
