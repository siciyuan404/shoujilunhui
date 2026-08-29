package com.shoujilunhui.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shoujilunhui.app.ui.home.HomeScreen
import com.shoujilunhui.app.ui.recognize.RecognizeScreen
import com.shoujilunhui.app.ui.settings.SettingsScreen
import com.shoujilunhui.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通知权限（用于静默更新完成后的"点击安装"通知）
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        setContent {
            AppTheme {
                AppNav()
            }
        }
        // 无感更新：后台静默检查（灰度滚动更新由服务端 /api/update 控制），延迟 5 秒避免占用首屏
        Handler(Looper.getMainLooper()).postDelayed({
            Updater.checkSilently(this, BuildConfig.VERSION_NAME, ConfigStore(this).baseUrl)
        }, 5000)
    }
}

object Routes {
    const val HOME = "home"
    const val RECOGNIZE = "recognize"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenRecognize = { nav.navigate(Routes.RECOGNIZE) },
            )
        }
        composable(Routes.RECOGNIZE) {
            RecognizeScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
