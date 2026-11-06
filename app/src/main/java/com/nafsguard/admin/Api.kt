package com.nafsguard.admin

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {
    // আপনার Apps Script Web App-এর লিংক (ইউজার অ্যাপে যেটা আছে সেটাই)
    const val ENDPOINT =
        "https://script.google.com/macros/s/AKfycbxvf9TgPZdx-stRrAINe4kOOJpoR8bn65sR-EADx9u8dPKrlmUcAfMSCaA34-caBcCC/exec"

    data class Res(val ok: Boolean, val msg: String, val json: JSONObject? = null) {
        /** অ্যাডমিন কী ভুল হলে সার্ভার auth=false পাঠায় */
        val authFailed: Boolean get() = json != null && !json.optBoolean("auth", true)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun call(params: Map<String, String>): Res {
        val q = params.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val body: String
        try {
            val cn = URL("$ENDPOINT?$q").openConnection() as HttpURLConnection
            cn.connectTimeout = 25000
            cn.readTimeout = 45000
            cn.instanceFollowRedirects = true
            body = cn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return Res(false, "ইন্টারনেট বা সার্ভারে সমস্যা, আবার চেষ্টা করুন")
        }
        return try {
            val j = JSONObject(body)
            Res(j.optBoolean("ok"), j.optString("msg"), j)
        } catch (e: JSONException) {
            Res(false, "সার্ভার সঠিক উত্তর দেয়নি। Apps Script নতুন কোড দিয়ে Deploy (Anyone) করা হয়েছে কিনা দেখুন।")
        }
    }

    fun users(key: String) = call(mapOf("action" to "admin_users", "key" to key))
}

object Store {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("admin", Context.MODE_PRIVATE)
    fun key(c: Context) = sp(c).getString("key", "") ?: ""
    fun setKey(c: Context, k: String) = sp(c).edit().putString("key", k).apply()
    fun clear(c: Context) = sp(c).edit().remove("key").apply()
}
