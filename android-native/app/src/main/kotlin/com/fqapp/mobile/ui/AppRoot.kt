package com.fqapp.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fqapp.mobile.ui.chapters.ChapterListScreen
import com.fqapp.mobile.ui.detail.DetailScreen
import com.fqapp.mobile.ui.reader.ReaderScreen
import com.fqapp.mobile.ui.shelf.ShelfScreen
import com.fqapp.mobile.ui.store.StoreScreen

private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab("shelf", "书架", Icons.Filled.Home),
    TopLevelTab("store", "书城", Icons.Filled.Search),
    TopLevelTab("profile", "我的", Icons.Filled.Person),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // 详情/阅读这类二级页自己带底栏，一级页才挂 tab。
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "store",
            modifier = Modifier.padding(padding),
        ) {
            composable("shelf") {
                ShelfScreen(onOpenBook = { bookId -> navController.navigate("book/$bookId") })
            }
            composable("store") {
                StoreScreen(onOpenBook = { bookId -> navController.navigate("book/$bookId") })
            }
            composable("profile") {
                PlaceholderScreen("我的", "P6 才做设置、缓存管理与次要功能。")
            }
            composable(
                route = "book/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId").orEmpty()
                DetailScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                    onOpenChapters = { navController.navigate("book/$bookId/chapters") },
                    onOpenReader = { navController.navigate("book/$bookId/read") },
                )
            }
            composable(
                route = "book/{bookId}/chapters",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId").orEmpty()
                ChapterListScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                    onOpenReader = { itemId -> navController.navigate("book/$bookId/read?itemId=$itemId") },
                )
            }
            composable(
                route = "book/{bookId}/read?itemId={itemId}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("itemId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ReaderScreen(
                    bookId = entry.arguments?.getString("bookId").orEmpty(),
                    startItemId = entry.arguments?.getString("itemId"),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}