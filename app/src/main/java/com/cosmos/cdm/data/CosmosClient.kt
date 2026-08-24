package com.cosmos.cdm.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal HTTP client for the COSMOS /api/v1 API.
 * Uses HttpURLConnection (no extra dependency). All calls are blocking —
 * callers must run them on Dispatchers.IO.
 *
 * Same pattern as VMC (`reference/CosmosClient.kt`): bearer on the wire if
 * present, JSON in/out, http_status stuffed into the body on non-2xx so a
 * panel can render an error without throwing.
 */
object CosmosClient {

    fun getStatus(baseUrl: String, token: String): JSONObject =
        request("GET", url(baseUrl, "/api/v1/status"), token, null)

    fun getHealth(baseUrl: String, token: String): JSONObject =
        request("GET", url(baseUrl, "/api/v1/health"), token, null)

    fun getSpend(baseUrl: String, token: String): JSONObject =
        request("GET", url(baseUrl, "/api/v1/spend"), token, null)

    fun getJobs(baseUrl: String, token: String): JSONObject =
        request("GET", url(baseUrl, "/api/v1/jobs"), token, null)

    fun getMakers(baseUrl: String, token: String, kind: String? = null): JSONObject {
        val q = if (kind.isNullOrBlank()) "" else "?kind=" + URLEncoder.encode(kind, "UTF-8")
        return request("GET", url(baseUrl, "/api/v1/makers$q"), token, null)
    }

    fun getEvents(baseUrl: String, token: String, sinceSeq: Long): JSONObject =
        request("GET", url(baseUrl, "/api/v1/events?since_seq=$sinceSeq"), token, null)

    fun postCommand(baseUrl: String, token: String, text: String): JSONObject =
        request(
            "POST",
            url(baseUrl, "/api/v1/command"),
            token,
            JSONObject().put("text", text),
        )

    private fun url(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    private fun request(
        method: String,
        urlStr: String,
        token: String,
        body: JSONObject?,
    ): JSONObject {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = method
            // Short connect so a down server says "offline" instead of hanging.
            conn.connectTimeout = 8_000
            conn.readTimeout = if (method == "POST") 30_000 else 12_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val parsed = try {
                JSONObject(text)
            } catch (_: Exception) {
                JSONObject().put("raw", text.take(500))
            }
            if (code !in 200..299) {
                parsed.put("http_status", code)
                if (!parsed.has("error") || parsed.isNull("error")) {
                    parsed.put(
                        "error",
                        if (code == 401) "UNAUTHORIZED" else "HTTP $code",
                    )
                }
            }
            parsed
        } catch (e: Exception) {
            JSONObject()
                .put("error", "offline")
                .put("detail", e.message ?: e.javaClass.simpleName)
                .put("http_status", 0)
        } finally {
            conn?.disconnect()
        }
    }
}
