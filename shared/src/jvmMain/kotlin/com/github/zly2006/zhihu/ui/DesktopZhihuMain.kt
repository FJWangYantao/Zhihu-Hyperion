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

package com.github.zly2006.zhihu.ui

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.filter.ContentOpenTracker
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.bottomBarItemOrderFromPreference
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.resolveValidStartDestinationKey
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerTransitionDirection
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import org.koin.compose.koinInject

/**
 * Desktop 平台的 Zhihu-Hyperion 主界面入口。
 *
 * 这里创建桌面 NavController、账号存储、HTTP 客户端和文章等平台行为，再注入共享 [ZhihuMain]。
 * 设计上尽量复用 common 页面结构，只把回答切换状态和桌面账号读取留在 JVM 侧。
 */
@Composable
fun DesktopZhihuMain() {
    /** 主返回栈控制器，承载 MainTabs 主壳和单栏页面。 */
    val navController = rememberNavController()
    val accountStore = koinInject<ZhihuAccountStore>()
    val contentOpens = koinInject<ContentOpenTracker>()
    val answerSwitchState = koinInject<ArticleAnswerSwitchState>()
    val accounts by accountStore.accountsState.collectAsState()
    val accountSession = accounts.session
    val httpClient = remember(accountStore, accountSession) { accountStore.client.httpClient() }
    val userMessages = rememberUserMessageSink()
    var mainTabNavigationTarget by remember { mutableStateOf<TopLevelDestination?>(null) }
    var currentMainTabOpenFrom by remember { mutableStateOf<String?>(null) }

    fun navigateToMainTabs() {
        navController.navigate(MainTabs) {
            launchSingleTop = true
            restoreState = true
            popUpTo(MainTabs) {
                saveState = true
            }
        }
    }

    /**
     * 从指定返回栈的当前页面读取内容打开来源，支持右侧详情栏。
     *
     * @param controller 提供来源页面的返回栈控制器
     */
    fun currentContentOpenSource(controller: NavHostController = navController): NavDestination? {
        val currentEntry = controller.currentBackStackEntry
        return runCatching {
            currentEntry?.toRoute<Article>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Question>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Pin>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<CollectionContent>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<History>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Notification>()
        }.getOrNull()
    }

    /**
     * 通过 [targetController] 指定的主返回栈或详情返回栈打开 [route]。
     *
     * @param route 要打开的页面
     * @param targetController 持有目标页面返回栈的控制器
     */
    fun navigate(route: NavDestination, targetController: NavHostController = navController) {
        when (route) {
            MainTabs -> {
                mainTabNavigationTarget = Home
                navigateToMainTabs()
            }

            else -> {
                contentOpens.prepare(
                    destination = route,
                    openFrom = if (
                        runCatching { navController.currentBackStackEntry?.toRoute<MainTabs>() }.getOrNull() != null
                    ) {
                        currentMainTabOpenFrom
                    } else {
                        null
                    }
                        ?: ContentOpenEventSupport.inferOpenFrom(currentContentOpenSource(targetController), route),
                )
                targetController.navigate(route)
            }
        }
    }

    ZhihuMain(
        navController = navController,
        mainTabNavigationTarget = mainTabNavigationTarget,
        navigate = ::navigate,
        navigateContent = { destination, targetController -> navigate(destination, targetController) },
        enableLandscapeListDetail = true,
        enableAssistantPane = true,
        setCurrentMainTabOpenFrom = { currentMainTabOpenFrom = it },
        consumeMainTabNavigationTarget = { destination ->
            if (mainTabNavigationTarget == destination) {
                mainTabNavigationTarget = null
            }
        },
        preferenceState = rememberDesktopZhihuMainPreferenceState(),
        articleEnterTransition = {
            when (answerSwitchState.answerTransitionDirection) {
                ArticleAnswerTransitionDirection.VERTICAL_NEXT -> {
                    slideInVertically(tween(300)) { it } + fadeIn(tween(300))
                }

                ArticleAnswerTransitionDirection.VERTICAL_PREVIOUS -> {
                    slideInVertically(tween(300)) { -it } + fadeIn(tween(300))
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_NEXT -> {
                    slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_PREVIOUS -> {
                    slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))
                }

                else -> {
                    slideInHorizontally(tween(300)) { it }
                }
            }
        },
        articleExitTransition = {
            when (answerSwitchState.answerTransitionDirection) {
                ArticleAnswerTransitionDirection.VERTICAL_NEXT -> {
                    slideOutVertically(tween(300)) { -it } + fadeOut(tween(300))
                }

                ArticleAnswerTransitionDirection.VERTICAL_PREVIOUS -> {
                    slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_NEXT -> {
                    slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_PREVIOUS -> {
                    slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
                }

                else -> {
                    ExitTransition.None
                }
            }
        },
        articleContent = { article: Article, navEntry ->
            val articleViewModel: ArticleViewModel = viewModel(navEntry) {
                ArticleViewModel(article, httpClient, userMessages)
            }
            ArticleScreen(article, articleViewModel)
        },
    )
}

/**
 * 读取 Desktop 设置中会影响主壳的偏好快照。
 *
 * 语义必须和 Android 的 `rememberAndroidZhihuMainPreferenceState()` 保持一致，避免同一个底栏/启动页设置在不同平台表现不同。
 */
@Composable
private fun rememberDesktopZhihuMainPreferenceState(): ZhihuMainPreferenceState {
    val settings = koinInject<SettingsStore>()
    val allBottomBarItemKeys = remember {
        listOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name, MyCollections.name, Account.name)
    }
    return rememberZhihuMainPreferenceState {
        val duo3HomeAccount = settings.getBoolean("duo3_home_account", false)
        val selectedKeys = normalizeBottomBarSelection(
            settings.getStringSet(
                BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
                defaultBottomBarSelectionKeys(duo3HomeAccount, platformBottomBarItemLimit),
            ),
            duo3HomeAccount,
            enforceMinimumSelection = true,
            maximumSelection = platformBottomBarItemLimit,
        )
        val orderedSelectedKeys = bottomBarItemOrderFromPreference(
            settings.getStringOrNull(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY),
            selectedKeys,
        )
        ZhihuMainPreferenceSnapshot(
            duo3HomeAccount = duo3HomeAccount,
            tapToScrollToTopEnabled = settings.getBoolean("bottomBarTapScrollToTop", true),
            autoHideBottomBar = settings.getBoolean("autoHideBottomBar", false),
            collectionDirectBrowseEnabled = settings.getBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false),
            landscapeListDetailEnabled = settings.getBoolean(LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY, true),
            selectedBottomBarItemKeys = orderedSelectedKeys,
            startDestination = navDestinationFromName(
                resolveValidStartDestinationKey(
                    settings.getString(START_DESTINATION_PREFERENCE_KEY, Home.name),
                    orderedSelectedKeys.ifEmpty { allBottomBarItemKeys.filter { it in selectedKeys } },
                ),
            ),
        )
    }
}
