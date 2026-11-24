package com.fqapp.mobile.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 进程级的 Rust core 句柄。
 *
 * 与 `lib/services/backend_service.dart` 保持同样的运行期契约：把
 * `assets/config/{config,filter,device_pool}.json` 和 `assets/filters` 整个目录
 * 铺到 `<filesDir>/backend`，再用 `init_core` 起 loopback 服务。
 */
object CoreRuntime {
    private const val TAG = "FqCore"
    private const val RUNTIME_DIR = "backend"

    /** core 监听成功后拿到的东西；其余请求全走 HTTP。 */
    data class Handle(val baseUrl: String, val capability: String)

    private val mutex = Mutex()

    @Volatile
    private var handle: Handle? = null

    /** 只连 loopback，不需要连接池以外的任何调优。 */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun version(): String = FqCoreBridge.nativeVersion()

    /** 幂等启动：并发调用只会真正启动一次。 */
    suspend fun ensureStarted(context: Context): Handle = mutex.withLock {
        handle?.let { return@withLock it }
        val started = withContext(Dispatchers.IO) { startBlocking(context.applicationContext) }
        handle = started
        started
    }

    suspend fun stop() = mutex.withLock {
        if (handle == null) return@withLock
        withContext(Dispatchers.IO) { FqCoreBridge.nativeStop() }
        handle = null
    }

    private fun startBlocking(context: Context): Handle {
        val dir = deployRuntime(context)
        val raw = FqCoreBridge.nativeStart(
            configPath = File(dir, "config/config.json").path,
            poolPath = File(dir, "config/device_pool.json").path,
            filterPath = File(dir, "config/filter.json").path,
            runtimeDir = dir.path,
            port = declaredPort(File(dir, "config/config.json")),
            lanShare = false,
        ) ?: throw IllegalStateException("init_core 没有返回句柄")

        val json = JSONObject(raw)
        val started = Handle(json.getString("baseUrl"), json.getString("capability"))
        Log.i(TAG, "core v${version()} ready at ${started.baseUrl}")
        return started
    }

    /** 把 core 需要的资源铺到应用私有目录；内容没变就不重写。 */
    private fun deployRuntime(context: Context): File {
        val dir = File(context.filesDir, RUNTIME_DIR)
        val configDir = File(dir, "config").apply { mkdirs() }

        copyAsset(context, "config/config.json", File(configDir, "config.json"))
        copyAsset(context, "config/filter.json", File(configDir, "filter.json"))
        copyAsset(context, "config/device_pool.example.json", File(configDir, "device_pool.example.json"))

        // device_pool.json 由 core 首次注册设备时写入，只在一开始播种。
        val pool = File(configDir, "device_pool.json")
        if (!pool.exists()) {
            copyAsset(context, "config/device_pool.example.json", pool)
        }
        copyAssetTree(context, "filters", File(dir, "filters"))
        return dir
    }

    private fun copyAssetTree(context: Context, assetDir: String, target: File) {
        val children = context.assets.list(assetDir).orEmpty()
        if (children.isEmpty()) {
            copyAsset(context, assetDir, target)
            return
        }
        target.mkdirs()
        for (child in children) {
            copyAssetTree(context, "$assetDir/$child", File(target, child))
        }
    }

    private fun copyAsset(context: Context, asset: String, target: File) {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        if (target.isFile && target.length() == bytes.size.toLong() &&
            target.readBytes().contentEquals(bytes)
        ) {
            return
        }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun declaredPort(config: File): Int =
        runCatching { JSONObject(config.readText()).optInt("port", 0) }.getOrDefault(0)
}