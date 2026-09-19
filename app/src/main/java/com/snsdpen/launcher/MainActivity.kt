package com.snsdpen.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.snsdpen.launcher.model.Surface
import com.snsdpen.launcher.ui.LauncherTheme

/** ホーム本体(HOME + LAUNCHER)。回転・折りたたみは configChanges で吸収する */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideStatusBar()
        setContent {
            LauncherTheme {
                LauncherApp(surface = Surface.HOME)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    /**
     * ステータスバー(Wi-Fi・電波・電池・時計・通知アイコン)を隠す。
     * 上端から下へスワイプすると一時的に出て、通知シェードも引ける。代わりの表示は床のタイル(METER)。
     */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
