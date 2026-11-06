package com.nafsguard.admin

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {
    // Cloudflare Worker-এর লিংক (ইউজার অ্যাপের Api.kt-এর সাথে একই)
    const val DEFAULT_ENDPOINT = "https://nafsguard-api-new.khadimulummahtrustfoundation.workers.dev"

    data class Res(val ok: Boolean, val msg: String, val json: JSONObject? = null) {
        /** অ্যাডমিন কী ভুল হলে সার্ভার auth=false পাঠায় */
        val authFailed: Boolean get() = json != null && !json.optBoolean("auth", true)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** key আলাদা: URL-এ না গিয়ে x-admin-key হেডারে যায় */
    fun call(endpoint: String, key: String, params: Map<String, String>): Res {
        val q = params.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val body: String
        try {
            val cn = URL("${endpoint.trimEnd('/')}?$q").openConnection() as HttpURLConnection
            cn.connectTimeout = 20000
            cn.readTimeout = 30000
            cn.instanceFollowRedirects = true
            if (key.isNotEmpty()) cn.setRequestProperty("x-admin-key", key)
            val stream = if (cn.responseCode in 200..399) cn.inputStream else (cn.errorStream ?: cn.inputStream)
            body = stream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return Res(false, "ইন্টারনেট বা সার্ভারে সমস্যা, আবার চেষ্টা করুন")
        }
        return try {
            val j = JSONObject(body)
            Res(j.optBoolean("ok"), j.optString("msg"), j)
        } catch (e: JSONException) {
            Res(false, "সার্ভার সঠিক উত্তর দেয়নি। Worker-এর লিংক ও Deploy ঠিক আছে কিনা দেখুন।")
        }
    }

    fun users(endpoint: String, key: String) = call(endpoint, key, mapOf("action" to "admin_users"))
}

object Store {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("admin", Context.MODE_PRIVATE)
    fun key(c: Context) = sp(c).getString("key", "") ?: ""
    fun setKey(c: Context, k: String) = sp(c).edit().putString("key", k).apply()
    fun clear(c: Context) = sp(c).edit().remove("key").apply()
    fun endpoint(c: Context) = sp(c).getString("endpoint", "")?.ifBlank { null } ?: Api.DEFAULT_ENDPOINT
    fun setEndpoint(c: Context, e: String) = sp(c).edit().putString("endpoint", e.trim().trimEnd('/')).apply()
}
