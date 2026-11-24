package com.fqapp.mobile

import android.app.Application
import android.util.Log
import com.fqapp.mobile.core.CoreRuntime
import com.fqapp.mobile.settings.ReadingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FqApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ReadingPrefs.init(this)
        // 预热 core：铺资源 + 起 loopback 监听，首屏就不用等这几百毫秒。
        appScope.launch {
            runCatching { CoreRuntime.ensureStarted(this@FqApp) }
                .onFailure { Log.w(TAG, "core warm-up failed", it) }
        }
    }

    private companion object {
        const val TAG = "FqApp"
    }
}