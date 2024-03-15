package com.aitsuki.android.awsfaceliveness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aitsuki.android.awsfaceliveness.screen.DetectionScreen
import com.aitsuki.android.awsfaceliveness.screen.StartScreen

val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("NavContoller not found!")
}

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = "/start",
            modifier = modifier,
        ) {
            composable("/start") {
                StartScreen()
            }
            composable("/detection") {
                DetectionScreen()
            }
        }
    }
}