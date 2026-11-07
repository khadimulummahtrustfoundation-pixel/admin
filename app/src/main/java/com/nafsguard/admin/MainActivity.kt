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
    private var loading = false

    // ---- একসাথে বাছাই (সিলেকশন মোড) ----
    private var selecting = false
    private val selected = LinkedHashSet<String>()          // নির্বাচিত ইউজারের ফোন নম্বর
    private class CardRef(val card: LinearLayout, val check: ImageView)
    private val cards = HashMap<String, CardRef>()
    private var selBar: LinearLayout? = null
    private var selCount: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.bars(this)
        // অ্যাপ খুললে সার্ভারে কোনো রিকোয়েস্ট যায় না; ফোনে সেভ করা তালিকা দেখায়। নতুন তালিকা শুধু রিফ্রেশ চাপলে আসবে
        if (Store.key(this).isEmpty()) showLogin() else { showUsers(); users = cachedUsers(); renderList() }
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
        val urlEt = Ui.dedit(c, "সার্ভার লিংক (Worker)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Store.endpoint(c))
        }
        root.addView(urlEt); root.addView(keyEt); root.addView(status)
        lateinit var btn: Button
        btn = Ui.dbutton(c, "প্রবেশ করুন") {
            val k = keyEt.text.toString().trim()
            if (k.isEmpty()) { status.setTextColor(Ui.RED); status.text = "অ্যাডমিন কী লিখুন"; return@dbutton }
            val ep = urlEt.text.toString().trim().trimEnd('/')
            if (!ep.startsWith("https://")) { status.setTextColor(Ui.RED); status.text = "সার্ভার লিংক https:// দিয়ে শুরু হতে হবে"; return@dbutton }
            btn.isEnabled = false
            status.setTextColor(Ui.NEON); status.text = "যাচাই হচ্ছে..."
            Thread {
                val r = Api.users(ep, k)
                runOnUiThread {
                    if (r.ok) {
                        Store.setEndpoint(c, ep)
                        Store.setKey(c, k)
                        users = parseUsers(r); saveCache(r)
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
        selecting = false; selected.clear(); cards.clear()
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

        // বাছাই বার: লম্বা চাপে ইউজার বাছলে এটা দেখা যায়
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            visibility = android.view.View.GONE
            val pd = Ui.dp(c, 10); setPadding(pd, pd, pd, pd)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14513A")); setStroke(Ui.dp(c, 1), Ui.NEON); cornerRadius = Ui.dp(c, 16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 8) }
        }
        val sc = TextView(c).apply { textSize = 14f; setTextColor(Ui.TXT); setTypeface(Typeface.DEFAULT, Typeface.BOLD) }
        selCount = sc
        bar.addView(sc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(chip("সব", false) { toggleAllShown() })
        bar.addView(chip("কমান্ড", true) { if (selected.isNotEmpty()) commandDialog(selectedUsers()) })
        bar.addView(chip("বাতিল", false) { exitSelect() })
        selBar = bar
        root.addView(bar)

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

    private fun saveCache(r: Api.Res) {
        val arr = r.json?.optJSONArray("users") ?: return
        Store.setUsersCache(this, arr.toString())
    }

    private fun cachedUsers(): List<User> = try {
        val s = Store.usersCache(this)
        if (s.isEmpty()) emptyList() else parseArray(org.json.JSONArray(s))
    } catch (e: Exception) { emptyList() }

    private fun parseUsers(r: Api.Res): List<User> {
        val arr = r.json?.optJSONArray("users") ?: return emptyList()
        return parseArray(arr)
    }

    private fun parseArray(arr: org.json.JSONArray): List<User> {
        val out = ArrayList<User>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(User(o.optString("name"), o.optString("phone"), o.optString("date"), o.optBoolean("hasToken")))
        }
        return out
    }

    // শুধু রিফ্রেশ বাটনে চাপ দিলে চলে (নিজে নিজে কখনো চলে না)। আগের রিকোয়েস্ট চলতে থাকলে নতুন যায় না
    private fun loadUsers() {
        if (loading) return
        loading = true
        countTx?.setTextColor(Ui.TXT2); countTx?.text = "লোড হচ্ছে..."
        val k = Store.key(this)
        val ep = Store.endpoint(this)
        Thread {
            val r = Api.users(ep, k)
            runOnUiThread {
                loading = false
                when {
                    r.authFailed -> { Store.clear(this); showLogin("অ্যাডমিন কী ভুল বা বদলে গেছে") }
                    r.ok -> { users = parseUsers(r); saveCache(r); renderList() }
                    else -> { countTx?.setTextColor(Ui.RED); countTx?.text = r.msg }
                }
            }
        }.start()
    }

    private fun updatedLabel(): String {
        val t = Store.usersAt(this)
        if (t == 0L) return ""
        val f = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
        f.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        return " • শেষ রিফ্রেশ: " + f.format(t)
    }

    private fun renderList() {
        val c: Activity = this
        val lb = listBox ?: return
        lb.removeAllViews()
        cards.clear()
        // তালিকায় নেই এমন ইউজার (মোছা/রিফ্রেশের পর) বাছাই থেকে বাদ
        selected.retainAll(users.map { it.phone }.toSet())
        if (selecting && selected.isEmpty()) selecting = false
        val q = query.trim().lowercase()
        val m = if (q.isEmpty()) users else users.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
        countTx?.setTextColor(Ui.TXT2)
        countTx?.text = "মোট ${users.size} জন" + (if (q.isNotEmpty()) ", মিলেছে ${m.size} জন" else "") + updatedLabel()
        if (m.isEmpty()) {
            val empty = if (users.isEmpty()) (if (Store.usersAt(c) == 0L) "তালিকা দেখতে উপরের রিফ্রেশ বাটনে চাপ দিন" else "কোনো ইউজার নেই") else "কিছু মেলেনি"
            lb.addView(Ui.dtext(c, empty, 14f, false, Ui.TXT2)
                .apply { gravity = Gravity.CENTER })
            applySelMode()
            return
        }
        for (u in m.take(200)) lb.addView(userCard(u))
        if (m.size > 200) lb.addView(Ui.dtext(c, "আরও দেখতে নাম বা নম্বর লিখে খুঁজুন", 13f, false, Ui.TXT2)
            .apply { gravity = Gravity.CENTER })
        applySelMode()
    }

    private fun filtered(): List<User> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) users else users.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
    }

    private fun selectedUsers(): List<User> = users.filter { it.phone in selected }

    private fun chip(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 13.5f; gravity = Gravity.CENTER
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (primary) Color.parseColor("#04281C") else Ui.NEON)
        val ph = Ui.dp(this@MainActivity, 14); val pv = Ui.dp(this@MainActivity, 8)
        setPadding(ph, pv, ph, pv)
        background = GradientDrawable().apply {
            cornerRadius = Ui.dp(this@MainActivity, 20).toFloat()
            if (primary) setColor(Ui.NEON) else { setColor(Color.TRANSPARENT); setStroke(Ui.dp(this@MainActivity, 1), Ui.NEON) }
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { leftMargin = Ui.dp(this@MainActivity, 6) }
        setOnClickListener { onClick() }
    }

    private fun styleCard(phone: String) {
        val ref = cards[phone] ?: return
        val sel = phone in selected
        ref.card.background = GradientDrawable().apply {
            setColor(if (sel) Color.parseColor("#1A5A41") else Ui.CARD)
            setStroke(if (sel) Ui.dp(this@MainActivity, 2) else Ui.dp(this@MainActivity, 1), if (sel) Ui.NEON else Ui.CARD_STROKE)
            cornerRadius = Ui.dp(this@MainActivity, 20).toFloat()
        }
        ref.check.visibility = if (selecting) android.view.View.VISIBLE else android.view.View.GONE
        ref.check.setImageResource(if (sel) R.drawable.ic_check else R.drawable.ic_circle)
        ref.check.setColorFilter(if (sel) Ui.NEON else Ui.CARD_STROKE)
    }

    // বাছাই মোড চালু/বন্ধ বা বদলালে বার ও সব কার্ডের চেহারা ঠিক করে
    private fun applySelMode() {
        selBar?.visibility = if (selecting) android.view.View.VISIBLE else android.view.View.GONE
        selCount?.text = "${selected.size} জন বাছাই"
        for (p in cards.keys) styleCard(p)
    }

    private fun toggle(phone: String) {
        if (!selected.remove(phone)) selected.add(phone)
        if (selected.isEmpty()) { exitSelect(); return }
        styleCard(phone)
        selCount?.text = "${selected.size} জন বাছাই"
    }

    private fun toggleAllShown() {
        val shown = filtered().map { it.phone }
        if (shown.isEmpty()) return
        if (selected.containsAll(shown)) selected.removeAll(shown.toSet()) else selected.addAll(shown)
        if (selected.isEmpty()) { exitSelect(); return }
        applySelMode()
    }

    private fun exitSelect() {
        selecting = false; selected.clear(); applySelMode()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selecting) exitSelect() else super.onBackPressed()
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
        val check = ImageView(c).apply {
            setImageResource(R.drawable.ic_circle); visibility = android.view.View.GONE
        }
        card.addView(check, LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26)).apply { leftMargin = Ui.dp(c, 10) })
        cards[u.phone] = CardRef(card, check)
        styleCard(u.phone)
        // সাধারণ চাপ: বাছাই মোডে না থাকলে আগের মতো একজনের মেনু; বাছাই মোডে থাকলে বাছা/বাদ
        card.setOnClickListener { if (selecting) toggle(u.phone) else actionDialog(u) }
        // লম্বা চাপ: বাছাই মোড শুরু
        card.setOnLongClickListener {
            if (!selecting) { selecting = true; selected.add(u.phone); applySelMode() } else toggle(u.phone)
            true
        }
        return card
    }

    // ---------------- ইউজারের কমান্ড ----------------
    private fun actionDialog(u: User) = commandDialog(listOf(u))

    // targets একজন হলে আগের মতোই; একাধিক হলে একসাথে (প্রতি ৪০ জনে ১টা Worker রিকোয়েস্ট)
    private fun commandDialog(targets: List<User>) {
        if (targets.isEmpty()) return
        val c: Activity = this
        val multi = targets.size > 1
        val u = targets[0]
        val d = Dialog(c)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = Ui.dcard(c)
        box.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (multi) {
            box.addView(Ui.dtext(c, "${targets.size} জন ইউজার বাছাই", 19f, true, Ui.NEON))
            box.addView(Ui.dtext(c, "যে কমান্ড দেবেন সেটা সবার কাছে যাবে।", 14f, false, Ui.TXT2))
            val nt = targets.count { !it.hasToken }
            if (nt > 0) box.addView(Ui.dtext(c, "$nt জনের টোকেন নেই, তাদের কাছে কমান্ড পৌঁছাবে না।", 13f, false, Ui.AMBER))
        } else {
            box.addView(Ui.dtext(c, u.name, 19f, true, Ui.NEON))
            box.addView(Ui.dtext(c, u.phone, 14f, false, Ui.TXT2))
            if (!u.hasToken) box.addView(Ui.dtext(c, "এই ইউজারের টোকেন নেই, কমান্ড পৌঁছাবে না।", 13f, false, Ui.AMBER))
        }

        fun item(label: String, outline: Boolean = true, danger: Boolean = false, f: () -> Unit) {
            box.addView(Ui.dbutton(c, label, outline, danger) { d.dismiss(); f() })
        }

        // একজন হলে আগের মতো নিশ্চিতকরণ + পাঠানো; অনেকজন হলে exec (সংখ্যাসহ নিশ্চিতকরণ + একসাথে পাঠানো)
        fun run(cmd: String, label: String, single: String, yes: String, extra: Map<String, String> = emptyMap()) {
            if (multi) exec(targets, cmd, label, extra) else confirm(single, yes) { send(u, cmd, extra) }
        }

        item("অনুমোদন দিন (মেয়াদসহ)", outline = false) {
            pickExpiry { extra, label ->
                if (multi) exec(targets, "APPROVE", "অনুমোদন, মেয়াদ: $label", extra)
                else confirm("${u.name} (${u.phone})\nঅনুমোদন দেওয়া হবে। মেয়াদ: $label", "পাঠান") { send(u, "APPROVE", extra) }
            }
        }
        item("মেয়াদ বদলান") {
            pickExpiry { extra, label ->
                if (multi) exec(targets, "SET_EXPIRY", "নতুন মেয়াদ: $label", extra)
                else confirm("${u.name} (${u.phone})\nনতুন মেয়াদ: $label", "পাঠান") { send(u, "SET_EXPIRY", extra) }
            }
        }
        item("বার্তা পাঠান") { messageDialog(targets) }
        item("সুইচ বন্ধ করুন") {
            val names = arrayOf("Facebook Reels", "Instagram Reels", "YouTube Shorts", "ছবি ও ভিডিও সার্চ")
            val ids = arrayOf("fb", "ig", "yt", "media")
            AlertDialog.Builder(c).setTitle("কোনটি বন্ধ হবে?")
                .setItems(names) { _, i ->
                    if (multi) exec(targets, "SWITCH_OFF", "${names[i]} ব্লক বন্ধ", mapOf("target" to ids[i]))
                    else send(u, "SWITCH_OFF", mapOf("target" to ids[i]))
                }.show()
        }
        item("লক খুলে দিন") {
            run("LOCK_OFF", "মেইন লক খুলে দেওয়া", "${u.name}-এর মেইন লক খুলে দেওয়া হবে।", "খুলুন")
        }
        item("অফলাইন পাসওয়ার্ড রিসেট") {
            run("RESET_PIN", "অফলাইন পাসওয়ার্ড রিসেট", "${u.name}-এর অফলাইন পাসওয়ার্ড মুছে যাবে।", "রিসেট")
        }
        item("ইউজার মুছে ফেলুন", danger = true) {
            if (multi) exec(targets, "DELETE", "ইউজার মুছে ফেলা (মোছার আগে ফোনে অ্যাক্সেস বন্ধের কমান্ড যাবে)", emptyMap())
            else confirm("${u.name} (${u.phone})\nইউজারকে তালিকা থেকে মুছে ফেলা হবে। মোছার আগে তার ফোনে অ্যাক্সেস বন্ধের কমান্ড পাঠানো হবে (পৌঁছালে)। নিশ্চিত?", "মুছুন") {
                dispatch(mapOf("action" to "admin_delete", "phone" to u.phone)) { removeLocal(u) }
            }
        }
        item("অ্যাক্সেস বন্ধ করুন", danger = true) {
            run("REVOKE", "অ্যাক্সেস বন্ধ (সব সুরক্ষা বন্ধ হয়ে লগআউট)",
                "${u.name}-এর অ্যাক্সেস বন্ধ হবে, সব সুরক্ষা বন্ধ হয়ে ইউজার লগআউট হবে। নিশ্চিত?", "বন্ধ করুন")
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
    private fun messageDialog(targets: List<User>?) {
        val c: Activity = this
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 20); setPadding(p, p, p, 0)
        }
        val t = EditText(c).apply { hint = "শিরোনাম"; setSingleLine() }
        val b = EditText(c).apply { hint = "বার্তা"; minLines = 3; gravity = Gravity.TOP }
        box.addView(t); box.addView(b)
        AlertDialog.Builder(c)
            .setTitle(when {
                targets == null -> "সবাইকে বার্তা"
                targets.size == 1 -> "বার্তা: ${targets[0].name}"
                else -> "বার্তা: ${targets.size} জন বাছাই"
            })
            .setView(box)
            .setPositiveButton("পাঠান") { _, _ ->
                val title = t.text.toString().trim().ifEmpty { "NafsGuard" }
                val body = b.text.toString().trim()
                if (body.isEmpty()) { Toast.makeText(c, "বার্তা লিখুন", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val extra = mapOf("title" to title, "body" to body)
                when {
                    targets == null -> sendAll(title, body)
                    targets.size == 1 -> send(targets[0], "NOTICE", extra)
                    else -> batchDispatch(targets, "NOTICE", extra)   // এখানে "পাঠান" চাপাই নিশ্চিতকরণ
                }
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
        p["action"] = "admin_push"; p["phone"] = u.phone; p["cmd"] = cmd
        p.putAll(extra)
        dispatch(p)
    }

    private fun sendAll(title: String, body: String) {
        dispatch(mapOf("action" to "admin_all", "title" to title, "body" to body))
    }

    // মোছা ইউজারকে তালিকা ও ফোনের ক্যাশ থেকে সরায় (সার্ভারে নতুন রিকোয়েস্ট ছাড়াই)
    private fun removeLocal(u: User) = removeLocalMany(listOf(u))

    private fun removeLocalMany(list: List<User>) {
        val gone = list.map { it.phone }.toSet()
        users = users.filter { it.phone !in gone }
        val arr = org.json.JSONArray()
        for (x in users) arr.put(org.json.JSONObject().apply {
            put("name", x.name); put("phone", x.phone); put("date", x.date); put("hasToken", x.hasToken)
        })
        Store.updateUsersCache(this, arr.toString())
        renderList()
    }

    // অনেকজনকে একই কমান্ড: সংখ্যাসহ নিশ্চিতকরণ চায়, তারপর একসাথে পাঠায়
    private fun exec(targets: List<User>, cmd: String, label: String, extra: Map<String, String>) {
        confirm("${targets.size} জন ইউজারকে এই কমান্ড যাবে:\n$label\n\nনিশ্চিত?", "পাঠান") {
            batchDispatch(targets, cmd, extra)
        }
    }

    // প্রতি ৪০ জনে মাত্র ১টা Worker রিকোয়েস্ট (Worker-এর BATCH_MAX-এর সাথে একই রাখুন)
    private val BATCH = 40

    private fun batchDispatch(targets: List<User>, cmd: String, extra: Map<String, String>) {
        val chunks = targets.chunked(BATCH)
        Toast.makeText(this, "পাঠানো হচ্ছে (${targets.size} জন)...", Toast.LENGTH_SHORT).show()
        val ep = Store.endpoint(this); val k = Store.key(this)
        Thread {
            var sent = 0; var failed = 0; var noToken = 0; var notFound = 0
            var done = 0; var reqs = 0; var err = ""; var auth = false
            for (ch in chunks) {
                val p = HashMap<String, String>(extra)
                p["action"] = "admin_batch"; p["cmd"] = cmd
                p["phones"] = ch.joinToString(",") { it.phone }
                val r = Api.call(ep, k, p)
                reqs++
                if (r.authFailed) { auth = true; break }
                val j = r.json
                if (j == null || !j.has("sent")) { err = r.msg; break }   // নেটওয়ার্ক বা যাচাইয়ের ত্রুটি
                sent += j.optInt("sent"); failed += j.optInt("failed")
                noToken += j.optInt("noToken"); notFound += j.optInt("notFound")
                done += ch.size
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (auth) { Store.clear(this); showLogin("অ্যাডমিন কী ভুল বা বদলে গেছে"); return@runOnUiThread }
                if (cmd == "DELETE" && done > 0) removeLocalMany(targets.take(done))
                exitSelect()
                val sb = StringBuilder()
                if (cmd == "DELETE") sb.append("মোছা হয়েছে: $done জন\n")
                sb.append("ফোনে পৌঁছেছে: $sent জন")
                if (failed > 0) sb.append("\nব্যর্থ: $failed জন")
                if (noToken > 0) sb.append("\nটোকেন নেই: $noToken জন")
                if (notFound > 0) sb.append("\nতালিকায় পাওয়া যায়নি: $notFound জন")
                if (err.isNotEmpty()) sb.append("\n\nত্রুটি: $err\nবাকিদের কাছে পাঠানো হয়নি।")
                sb.append("\n\nWorker রিকোয়েস্ট লেগেছে: $reqs টি")
                AlertDialog.Builder(this).setMessage(sb.toString())
                    .setPositiveButton("ঠিক আছে", null).show()
            }
        }.start()
    }

    private fun dispatch(params: Map<String, String>, onOk: (() -> Unit)? = null) {
        Toast.makeText(this, "পাঠানো হচ্ছে...", Toast.LENGTH_SHORT).show()
        val ep = Store.endpoint(this); val k = Store.key(this)
        Thread {
            val r = Api.call(ep, k, params)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (r.authFailed) { Store.clear(this); showLogin("অ্যাডমিন কী ভুল বা বদলে গেছে"); return@runOnUiThread }
                if (r.ok) onOk?.invoke()
                AlertDialog.Builder(this)
                    .setMessage(if (r.ok) r.msg.ifBlank { "পাঠানো হয়েছে" } else "ব্যর্থ: ${r.msg}")
                    .setPositiveButton("ঠিক আছে", null).show()
            }
        }.start()
    }
}
