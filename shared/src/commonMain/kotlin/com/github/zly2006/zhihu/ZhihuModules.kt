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

import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.reading.ReadingQueueSourceRegistry
import com.github.zly2006.zhihu.ui.article.ArticleAssistantSessions
import com.github.zly2006.zhihu.ui.components.AppDialogQueue
import com.github.zly2006.zhihu.update.updateModule
import com.github.zly2006.zhihu.viewmodel.AigcVoteService
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.viewmodel.ZhihuGuestClient
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * 与平台无关的进程级绑定；各平台组合根把它和平台模块一起启动。
 * 平台模块还要绑定 [com.github.zly2006.zhihu.update.InstalledBuild]，更新检查用它判断新旧。
 */
val zhihuSharedModule = module {
    includes(updateModule)
    single { ReadingQueueSourceRegistry(get()) }
    single { ArticleAnswerSwitchState() }
    single { AppDialogQueue() }
    single { ArticleAssistantSessions() }
    single { AigcVoteService(get(), get(), get()) } onClose { it?.close() }
    single { ZhihuGuestClient(get(), get<ZhihuAccountStore>().session.userAgent) } onClose { it?.close() }
}
