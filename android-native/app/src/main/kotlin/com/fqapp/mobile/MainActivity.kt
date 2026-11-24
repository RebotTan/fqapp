package com.fqapp.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fqapp.mobile.ui.AppRoot
import com.fqapp.mobile.ui.FqTheme

/**
 * 原生客户端入口。核心（Rust core + loopback）由 `FqApp` 在进程启动时预热，
 * 这里只负责挂上 Compose 界面。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FqTheme {
                AppRoot()
            }
        }
    }
}