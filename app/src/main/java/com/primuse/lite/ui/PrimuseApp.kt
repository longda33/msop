package com.primuse.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem("library", "音乐库", Icons.AutoMirrored.Filled.List),
    TabItem("search", "搜索", Icons.Filled.Search),
    TabItem("settings", "设置", Icons.Filled.Settings),
)

@Composable
fun PrimuseApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == tab.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("library") {
                PlaceholderScreen(
                    title = "音乐库",
                    description = "P1 将在这里实现歌曲 / 专辑 / 艺术家 / 文件夹四种浏览",
                )
            }
            composable("search") {
                PlaceholderScreen(
                    title = "搜索",
                    description = "P4-02 将在这里实现本地与远程合并搜索",
                )
            }
            composable("settings") {
                PlaceholderScreen(
                    title = "设置",
                    description = "P4-05 将在这里放扫描目录、OpenList 服务器、缓存清理",
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
