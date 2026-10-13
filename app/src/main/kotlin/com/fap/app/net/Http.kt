package com.fap.app.net

import com.fap.core.HttpClient
import com.fap.core.RawResponse
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Plugs OkHttp into the (dependency free) core. */
public class OkHttpTransport(private val client: OkHttpClient) : HttpClient {
    override fun get(url: String, headers: Map<String, String>): RawResponse {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            val body = runCatching { response.body?.string() }.getOrNull()
            return RawResponse(response.code, body, response.message)
        }
    }
}

public object HttpProvider {
    public fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()
}
