package com.fap.core

/**
 * Transport abstraction. The core module never talks to a socket directly:
 * the Android app plugs in OkHttp, tests plug in a fake. That keeps the core
 * 100% pure Kotlin.
 */
public interface HttpClient {
    /** Must return a response for any outcome; only network failures may throw. */
    public fun get(url: String, headers: Map<String, String> = emptyMap()): RawResponse
}

public data class RawResponse(
    val code: Int,
    val body: String?,
    val message: String = ""
) {
    public val isSuccessful: Boolean get() = code in 200..299
}

public class HttpStatusException(
    public val code: Int,
    public val url: String
) : Exception("HTTP $code for $url")

/** Trivial in-memory [HttpClient] used by the unit tests. */
public class FakeHttpClient(
    private val responses: Map<String, RawResponse>,
    private val default: RawResponse = RawResponse(404, null, "not found")
) : HttpClient {
    private val requested = ArrayList<String>()

    public val requests: List<String> get() = requested

    override fun get(url: String, headers: Map<String, String>): RawResponse {
        requested.add(url)
        return responses[url] ?: default
    }
}
