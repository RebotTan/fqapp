package com.fqapp.mobile.core

import com.fqapp.mobile.core.CoreRuntime.Handle
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/** 业务失败或网络不可用时抛这个，message 直接给用户看。 */
class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 所有业务请求的唯一出口：拼 baseUrl、带 capability、把上游错误翻成可读文案。
 *
 * 调用方负责切到 IO 线程（[getJson] 是阻塞的）。
 */
class CoreHttp(private val handle: Handle) {
    fun getJson(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val base = handle.baseUrl.toHttpUrl()
        val url = base.newBuilder().apply {
            path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${handle.capability}")
            .build()

        val text = try {
            CoreRuntime.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(readError(body) ?: "请求失败（HTTP ${response.code}）")
                }
                body
            }
        } catch (error: ApiException) {
            throw error
        } catch (error: Exception) {
            throw ApiException("网络不可用，请检查网络后重试", error)
        }

        return runCatching { JSONObject(text) }
            .getOrElse { throw ApiException("服务返回了无法识别的数据", it) }
    }

    private fun readError(body: String): String? =
        runCatching { JSONObject(body).optString("error").takeIf { it.isNotBlank() } }.getOrNull()
}