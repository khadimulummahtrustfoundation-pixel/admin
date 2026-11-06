package com.nafsguard.admin

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class User(val name: String, val phone: String, val date: String, val hasToken: Boolean)

class MainActivity : Activity() {
    private var users: List<User> = emptyList()
    private var query = ""
    private var listBox: LinearLayout? = null
    private var countTx: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.bars(this)
        if (Store.key(this).isEmpty()) showLogin() else { showUsers(); loadUsers() }
    }

    // ---------------- লগইন ----------------
    private fun showLogin(msg: String = "") {
        val c: Activity = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.bg()
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.heroLogo(c, 96))
        root.addView(Ui.dtext(c, "NafsGuard", 30f, true, Ui.NEON).apply { gravity = Gravity.CENTER })
        root.addView(Ui.dtext(c, "অ্যাডমিন প্যানেল").apply { gravity = Gravity.CENTER })
        val keyEt = Ui.dedit(c, "অ্যাডমিন কী").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.dtext(c, msg, 14f, false, Ui.RED)
        root.addView(keyEt); root.addView(status)
        lateinit var btn: Button
        btn = Ui.dbutton(c, "প্রবেশ করুন") {
            val k = keyEt.text.toString().trim()
            if (k.isEmpty()) { status.setTextColor(Ui.RED); status.text = "অ্যাডমিন কী লিখুন"; return@dbutton }
            btn.isEnabled = false
            status.setTextColor(Ui.NEON); status.text = "যাচাই হচ্ছে..."
            Thread {
                val r = Api.users(k)
                runOnUiThread {
                    if (r.ok) {
                        Store.setKey(c, k)
                        users = parseUsers(r)
                        showUsers(); renderList()
                    } else {
                        status.setTextColor(Ui.RED); status.text = r.msg; btn.isEnabled = true
                    }
                }
            }.start()
        }
        root.addView(btn)
        setContentView(ScrollView(c).apply { background = Ui.bg(); isFillViewport = true; addView(root) })
    }

    // ---------------- ইউজার তালিকা ----------------
    private fun showUsers() {
        val c: Activity = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.bg()
            val p = Ui.dp(c, 16); setPadding(p, p, p, p)
        }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(c).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34)))
        head.addView(Ui.dtext(c, "ইউজার তালিকা", 20f, true, Ui.NEON),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 10) })
        head.addView(Ui.icon(c, R.drawable.ic_refresh) { loadUsers() })
        head.addView(Ui.icon(c, R.drawable.ic_logout) {
            confirm("অ্যাডমিন থেকে বের হবেন? আবার ঢুকতে অ্যাডমিন কী লাগবে।", "বের হন") {
                Store.clear(c); users = emptyList(); showLogin()
            }
        })
        root.addView(head)

        val cnt = Ui.dtext(c, "", 13f, false, Ui.TXT2)
        countTx = cnt
        root.addView(cnt)

        root.addView(Ui.dbutton(c, "সবাইকে বার্তা পাঠান", outline = true) { messageDialog(null) })

        val search = Ui.dedit(c, "নাম বা নম্বর খুঁজুন")
        search.setText(query)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, cn: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, cn: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; renderList() }
        })
        root.addView(search)

        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        listBox = box
        root.addView(ScrollView(c).apply { addView(box) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun parseUsers(r: Api.Res): List<User> {
        val arr = r.json?.optJSONArray("users") ?: return emptyList()
        val out = ArrayList<User>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(User(o.optString("name"), o.optString("phone"), o.optString("date"), o.optBoolean("hasToken")))
        }
        return out
    }

    private fun loadUsers() {
        countTx?.setTextColor(Ui.TXT2); countTx?.text = "লোড হচ্ছে..."
        val k = Store.key(this)
        Thread {
            val r = Api.users(k)
            runOnUiThread {
                when {
                    r.authFailed -> { Store.clear(this); showLogin("অ্যাডমিন কী ভুল বা বদলে গেছে") }
                    r.ok -> { users = parseUsers(r); renderList() }
                    else -> { countTx?.setTextColor(Ui.RED); countTx?.text = r.msg }
                }
            }
        }.start()
    }

    private fun renderList() {
        val c: Activity = this
        val lb = listBox ?: return
        lb.removeAllViews()
        val q = query.trim().lowercase()
        val m = if (q.isEmpty()) users else users.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
        countTx?.setTextColor(Ui.TXT2)
        countTx?.text = "মোট ${users.size} জন" + (if (q.isNotEmpty()) ", মিলেছে ${m.size} জন" else "")
        if (m.isEmpty()) {
            lb.addView(Ui.dtext(c, if (users.isEmpty()) "কোনো ইউজার নেই" else "কিছু মেলেনি", 14f, false, Ui.TXT2)
                .apply { gravity = Gravity.CENTER })
            return
        }
        for (u in m.take(200)) lb.addView(userCard(u))
        if (m.size > 200) lb.addView(Ui.dtext(c, "আরও দেখতে নাম বা নম্বর লিখে খুঁজুন", 13f, false, Ui.TXT2)
            .apply { gravity = Gravity.CENTER })
    }

    private fun userCard(u: User): LinearLayout {
        val c: Activity = this
        val card = Ui.dcard(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        card.addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_person); setColorFilter(Ui.NEON)
            val p = Ui.dp(c, 10); setPadding(p, p, p, p)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1A5A41")) }
        }, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        val mid = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(c).apply {
            text = u.name; textSize = 16f; setTextColor(Ui.TXT); setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        mid.addView(TextView(c).apply { text = u.phone; textSize = 13.5f; setTextColor(Ui.TXT2) })
        mid.addView(TextView(c).apply { text = u.date; textSize = 11.5f; setTextColor(Ui.TXT2) })
        card.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { leftMargin = Ui.dp(c, 12) })
        card.addView(TextView(c).apply {
            text = if (u.hasToken) "ফোন যুক্ত" else "টোকেন নেই"
            textSize = 12f; setTextColor(if (u.hasToken) Ui.NEON else Ui.AMBER)
        })
        card.setOnClickListener { actionDialog(u) }
        return card
    }

    // ---------------- ইউজারের কমান্ড ----------------
    private fun actionDialog(u: User) {
        val c: Activity = this
        val d = Dialog(c)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = Ui.dcard(c)
        box.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        box.addView(Ui.dtext(c, u.name, 19f, true, Ui.NEON))
        box.addView(Ui.dtext(c, u.phone, 14f, false, Ui.TXT2))
        if (!u.hasToken) box.addView(Ui.dtext(c, "এই ইউজারের টোকেন নেই, কমান্ড পৌঁছাবে না।", 13f, false, Ui.AMBER))

        fun item(label: String, outline: Boolean = true, danger: Boolean = false, f: () -> Unit) {
            box.addView(Ui.dbutton(c, label, outline, danger) { d.dismiss(); f() })
        }

        item("অনুমোদন দিন (মেয়াদসহ)", outline = false) {
            pickExpiry { extra, label ->
                confirm("${u.name} (${u.phone})\nঅনুমোদন দেওয়া হবে। মেয়াদ: $label", "পাঠান") { send(u, "APPROVE", extra) }
            }
        }
        item("মেয়াদ বদলান") {
            pickExpiry { extra, label ->
                confirm("${u.name} (${u.phone})\nনতুন মেয়াদ: $label", "পাঠান") { send(u, "SET_EXPIRY", extra) }
            }
        }
        item("বার্তা পাঠান") { messageDialog(u) }
        item("সুইচ বন্ধ করুন") {
            val names = arrayOf("Facebook Reels", "Instagram Reels", "YouTube Shorts", "ছবি ও ভিডিও সার্চ")
            val ids = arrayOf("fb", "ig", "yt", "media")
            AlertDialog.Builder(c).setTitle("কোনটি বন্ধ হবে?")
                .setItems(names) { _, i -> send(u, "SWITCH_OFF", mapOf("target" to ids[i])) }.show()
        }
        item("লক খুলে দিন") {
            confirm("${u.name}-এর মেইন লক খুলে দেওয়া হবে।", "খুলুন") { send(u, "LOCK_OFF") }
        }
        item("অফলাইন পাসওয়ার্ড রিসেট") {
            confirm("${u.name}-এর অফলাইন পাসওয়ার্ড মুছে যাবে।", "রিসেট") { send(u, "RESET_PIN") }
        }
        item("অ্যাক্সেস বন্ধ করুন", danger = true) {
            confirm("${u.name}-এর অ্যাক্সেস বন্ধ হবে, সব সুরক্ষা বন্ধ হয়ে ইউজার লগআউট হবে। নিশ্চিত?", "বন্ধ করুন") {
                send(u, "REVOKE")
            }
        }
        box.addView(Ui.dbutton(c, "ফিরে যান", outline = true) { d.dismiss() }.apply {
            setTextColor(Ui.TXT2)
        })

        val wrap = FrameLayout(c).apply {
            val p = Ui.dp(c, 14); setPadding(p, p, p, p)
            addView(ScrollView(c).apply { addView(box) })
        }
        d.setContentView(wrap)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---------------- মেয়াদ বাছাই ----------------
    private fun fmt(ms: Long): String {
        val f = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        f.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        return f.format(ms) + " (বাংলাদেশ সময়)"
    }

    private fun pickExpiry(onPick: (Map<String, String>, String) -> Unit) {
        val labels = arrayOf("৭ দিন", "১৫ দিন", "৩০ দিন", "৬০ দিন", "৯০ দিন", "নিজে তারিখ-সময় বাছুন")
        val days = intArrayOf(7, 15, 30, 60, 90)
        AlertDialog.Builder(this).setTitle("মেয়াদ কত দিনের?").setItems(labels) { _, i ->
            if (i < days.size) onPick(mapOf("days" to days[i].toString()), "${labels[i]} (এখন থেকে)")
            else pickCustom(onPick)
        }.show()
    }

    private fun pickCustom(onPick: (Map<String, String>, String) -> Unit) {
        val tz = TimeZone.getTimeZone("Asia/Dhaka")
        val now = Calendar.getInstance(tz)
        DatePickerDialog(this, { _, y, mo, d ->
            TimePickerDialog(this, { _, h, mi ->
                val cal = Calendar.getInstance(tz)
                cal.clear()
                cal.set(y, mo, d, h, mi, 0)
                val ms = cal.timeInMillis
                onPick(mapOf("expiry" to ms.toString()), fmt(ms))
            }, 22, 0, false).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ---------------- বার্তা ----------------
    private fun messageDialog(u: User?) {
        val c: Activity = this
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 20); setPadding(p, p, p, 0)
        }
        val t = EditText(c).apply { hint = "শিরোনাম"; setSingleLine() }
        val b = EditText(c).apply { hint = "বার্তা"; minLines = 3; gravity = Gravity.TOP }
        box.addView(t); box.addView(b)
        AlertDialog.Builder(c)
            .setTitle(if (u == null) "সবাইকে বার্তা" else "বার্তা: ${u.name}")
            .setView(box)
            .setPositiveButton("পাঠান") { _, _ ->
                val title = t.text.toString().trim().ifEmpty { "NafsGuard" }
                val body = b.text.toString().trim()
                if (body.isEmpty()) { Toast.makeText(c, "বার্তা লিখুন", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (u == null) sendAll(title, body) else send(u, "NOTICE", mapOf("title" to title, "body" to body))
            }
            .setNegativeButton("বাতিল", null).show()
    }

    // ---------------- পাঠানো ----------------
    private fun confirm(msg: String, yes: String = "হ্যাঁ", f: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton(yes) { _, _ -> f() }
            .setNegativeButton("বাতিল", null).show()
    }

    private fun send(u: User, cmd: String, extra: Map<String, String> = emptyMap()) {
        val p = HashMap<String, String>()
        p["action"] = "admin_push"; p["key"] = Store.key(this); p["phone"] = u.phone; p["cmd"] = cmd
        p.putAll(extra)
        dispatch(p)
    }

    private fun sendAll(title: String, body: String) {
        dispatch(mapOf("action" to "admin_all", "key" to Store.key(this), "title" to title, "body" to body))
    }

    private fun dispatch(params: Map<String, String>) {
        Toast.makeText(this, "পাঠানো হচ্ছে...", Toast.LENGTH_SHORT).show()
        Thread {
            val r = Api.call(params)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (r.authFailed) { Store.clear(this); showLogin("অ্যাডমিন কী ভুল বা বদলে গেছে"); return@runOnUiThread }
                AlertDialog.Builder(this)
                    .setMessage(if (r.ok) "পাঠানো হয়েছে" else "ব্যর্থ: ${r.msg}")
                    .setPositiveButton("ঠিক আছে", null).show()
            }
        }.start()
    }
}
