package com.fqapp.mobile.core

/**
 * Rust core 的 JNI 入口。
 *
 * 符号名在 `rust/src/jni.rs` 里写死，改包名或类名必须两边同步修改；
 * `scripts/check_native_jni_symbols.py` 会在 CI 里拦住不一致。
 *
 * 方法必须编译成 static：Rust 侧第二个参数是 `JClass`。
 */
internal object FqCoreBridge {
    init {
        System.loadLibrary("fqapi_core")
    }

    @JvmStatic
    external fun nativeVersion(): String

    /** 返回 `{"port","baseUrl","capability"}`；失败抛 [RuntimeException]。 */
    @JvmStatic
    external fun nativeStart(
        configPath: String,
        poolPath: String,
        filterPath: String,
        runtimeDir: String,
        port: Int,
        lanShare: Boolean,
    ): String?

    @JvmStatic
    external fun nativeStop(): Boolean

    @JvmStatic
    external fun nativeCancel(requestId: String): Boolean
}