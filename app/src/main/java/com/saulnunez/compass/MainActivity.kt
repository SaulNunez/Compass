package com.saulnunez.compass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.saulnunez.compass.practice.PracticeScreen
import com.saulnunez.compass.practice.demoSong
import com.saulnunez.compass.ui.theme.CompassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompassTheme {
                PracticeScreen(demoSong)
            }
        }
    }
}
