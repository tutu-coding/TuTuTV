package com.tu.iptv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class M3uDownloader {

    companion object {
        // 使用 cn_all_status.m3u8（含分辨率/质量信息）
        const val M3U_URL =
            "https://raw.githubusercontent.com/best-fan/iptv-sources/main/cn_all_status.m3u8"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android TV)")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun download(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(M3U_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP 错误: ${response.code}")
                response.body?.string() ?: error("响应内容为空")
            }
        }
    }
}
