//! JNI 导出层：Kotlin 的 `external fun` 落到这四个函数。
//!
//! 业务决策全在 [`crate::mobile`]，这里只做 Java 类型与 Rust 类型的转换。
//! 出错一律抛 Java 异常：JNI 边界上的 panic 会直接把进程 abort，用户看到的是
//! 闪退而不是一条错误提示。
//!
//! 符号名对应 Kotlin 的 `com.fqapp.mobile.core.FqCoreBridge`；
//! `scripts/check_native_jni_symbols.py` 在构建阶段校验这四个名字。

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde::Serialize;

use crate::mobile::{self, MobileConfig};

/// `nativeStart` 的返回值，字段直接对应 Kotlin 的 data class。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct StartReply<'a> {
    port: u16,
    base_url: &'a str,
    capability: &'a str,
}

fn throw(env: &mut JNIEnv, message: &str) {
    // 抛异常本身失败也别 panic，Java 侧至少还能拿到一个未决的 JNI 调用。
    let _ = env.throw_new("java/lang/RuntimeException", message);
}

fn read_string(env: &mut JNIEnv, value: &JString) -> Result<String, String> {
    env.get_string(value)
        .map(|text| text.into())
        .map_err(|error| format!("读取 Java 字符串失败: {error}"))
}

/// 取参数，取不到就抛异常并返回调用方给的兜底值。
macro_rules! read_string_or_throw {
    ($env:ident, $value:expr, $on_error:expr) => {
        match read_string(&mut $env, &$value) {
            Ok(text) => text,
            Err(error) => {
                throw(&mut $env, &error);
                return $on_error;
            }
        }
    };
}

#[no_mangle]
pub extern "system" fn Java_com_fqapp_mobile_core_FqCoreBridge_nativeVersion<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    match env.new_string(mobile::version()) {
        Ok(text) => text.into_raw(),
        Err(error) => {
            throw(&mut env, &format!("返回版本号失败: {error}"));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fqapp_mobile_core_FqCoreBridge_nativeStart<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    config_path: JString<'local>,
    pool_path: JString<'local>,
    filter_path: JString<'local>,
    runtime_dir: JString<'local>,
    port: jint,
    lan_share: jboolean,
) -> jstring {
    let config_path = read_string_or_throw!(env, config_path, std::ptr::null_mut());
    let pool_path = read_string_or_throw!(env, pool_path, std::ptr::null_mut());
    let filter_path = read_string_or_throw!(env, filter_path, std::ptr::null_mut());
    let runtime_dir = read_string_or_throw!(env, runtime_dir, std::ptr::null_mut());

    let config = MobileConfig {
        config_path,
        pool_path,
        filter_path,
        runtime_dir,
        // Kotlin 传 0 表示「随便挑一个空闲端口」，这是唯一推荐的用法。
        port: port.clamp(0, i32::from(u16::MAX)) as u16,
        lan_share: lan_share != JNI_FALSE,
    };

    let handle = match mobile::start(&config) {
        Ok(handle) => handle,
        Err(error) => {
            throw(&mut env, &error);
            return std::ptr::null_mut();
        }
    };
    let reply = StartReply {
        port: handle.port,
        base_url: &handle.base_url,
        capability: &handle.capability,
    };
    let json = match serde_json::to_string(&reply) {
        Ok(json) => json,
        Err(error) => {
            throw(&mut env, &format!("序列化启动结果失败: {error}"));
            return std::ptr::null_mut();
        }
    };
    match env.new_string(json) {
        Ok(text) => text.into_raw(),
        Err(error) => {
            throw(&mut env, &format!("返回启动结果失败: {error}"));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fqapp_mobile_core_FqCoreBridge_nativeStop<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    if let Err(error) = mobile::stop() {
        throw(&mut env, &error);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fqapp_mobile_core_FqCoreBridge_nativeCancel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    request_id: JString<'local>,
) -> jboolean {
    let request_id = read_string_or_throw!(env, request_id, JNI_FALSE);
    if mobile::cancel(&request_id) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
