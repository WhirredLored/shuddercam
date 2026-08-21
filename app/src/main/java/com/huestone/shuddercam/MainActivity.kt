package com.huestone.shuddercam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.huestone.shuddercam.ui.ViewfinderScreen
import com.huestone.shuddercam.ui.theme.ShudderCamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShudderCamTheme {
                ViewfinderScreen()
            }
        }
    }
}
