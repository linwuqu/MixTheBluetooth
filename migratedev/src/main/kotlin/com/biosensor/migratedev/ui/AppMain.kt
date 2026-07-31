package com.biosensor.migratedev.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.biosensor.migratedev.AppGraph
import com.biosensor.migratedev.decisioncore.root.RootScreen
import com.biosensor.migratedev.decisioncore.root.RootState
import com.biosensor.migratedev.decisioncore.root.screen
import com.biosensor.migratedev.ui.auth.AuthRoute
import com.biosensor.migratedev.ui.connection.ConnectionRoute

private const val SESSION_GRAPH = "session"
private const val AUTH_ROUTE = "auth"
private const val CONNECTION_ROUTE = "connection/{userId}"

/**
 * Navigation 框架下的 View 管理
 * 主要关注两部分: 后退栈管理 & 路由定义
 * 1. 入参说明
 * navController: rememberNavController 负责实际的导航操作(navigate、popBackStack等)
 * 持有整个应用的后退栈 而且能被 remember 避免 Composable 重组时被重新创建
 *
 * 2. 后退栈管理 RootNavigationEffect
 * Navigation 库内部维护了一个基于路由字符串的 LIFO 后退栈
 * 在 navigate 调用时 通过 popUpTo 和 launchSingleTop 等选项控制栈行为:
 *   - popUpTo(AUTH_ROUTE) { inclusive = true } 表示在跳转前将 AUTH_ROUTE 本身及其之上的所有页面弹出栈
 *     从而“替换”当前栈 避免用户按返回键又回到登录页
 *   - launchSingleTop = true 避免在栈顶已存在相同路由时创建多个实例
 *
 * 3. 路由定义 NavHost
 * Host(startDestination = SESSION_GRAPH) -> AUTH_ROUTE
 * when(RootScreen.Connection) -> CONNECTION_ROUTE(userId)
 */
@Composable
fun AppMain(graph: AppGraph) {
    val navController = rememberNavController()
    val root = graph.rootWorkflow
    val rootState by root.state.collectAsStateWithLifecycle()

    RootNavigationEffect(navController, rootState)

    NavHost(
        navController = navController, startDestination = SESSION_GRAPH
    ) {
        navigation(
            route = SESSION_GRAPH, startDestination = AUTH_ROUTE
        ) {
            composable(AUTH_ROUTE) {
                val auth = root.authOrNull()
                if (auth == null) {
                    AppLoading()
                } else {
                    val state by auth.uiState.collectAsStateWithLifecycle()
                    AuthRoute(state, auth)
                }
            }

            composable(CONNECTION_ROUTE) {
                val connection = root.connectionOrNull()
                if (connection == null) {
                    AppLoading()
                } else {
                    ConnectionRoute(connection)
                }
            }
        }
    }
}

@Composable
private fun RootNavigationEffect(
    navController: NavHostController, rootState: RootState
) {
    val screen = rootState.screen()
    LaunchedEffect(screen) {
        when (screen) {
            // TODO: 这部分有实际意义吗
            RootScreen.Auth -> {
                navController.navigate(AUTH_ROUTE) {
                    popUpTo(SESSION_GRAPH) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }

            is RootScreen.Connection -> {
                val route = "connection/${Uri.encode(screen.userId)}"
                navController.navigate(route) {
                    popUpTo(AUTH_ROUTE) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLoading() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
