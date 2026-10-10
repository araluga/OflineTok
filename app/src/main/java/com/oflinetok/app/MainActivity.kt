package com.oflinetok.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oflinetok.app.ui.OflineTokRoot
import com.oflinetok.app.ui.OflineTokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OflineTokTheme {
                OflineTokRoot()
            }
        }
    }
}
