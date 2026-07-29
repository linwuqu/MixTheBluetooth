package com.biosensor.migratedev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.biosensor.migratedev.ui.AppMain

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = (application as MigrateDevApplication).appGraph
        setContent {
            MaterialTheme {
                AppMain(appGraph)
            }
        }
    }
}
