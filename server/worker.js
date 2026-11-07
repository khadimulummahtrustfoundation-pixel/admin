// NafsGuard API — Cloudflare Worker + D1
// Binding: DB (D1 "nafsguard")   Secrets: FCM_PROJECT_ID, FCM_SA_JSON, ADMIN_KEY

const DEFAULT_UPDATE_URL = "https://nafsguard.netlify.app/";
const CMDS = new Set(["APPROVE", "REVOKE", "SWITCH_OFF", "LOCK_OFF", "SET_EXPIRY", "RESET_PIN", "NOTICE"]);
const TARGETS = new Set(["fb", "ig", "yt", "media"]);
const DAY = 86400000;
const TEN_YEARS = 3650 * DAY;

const json = (o) =>
  new Response(JSON.stringify(o), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (msg, extra = {}) => json({ ok: false, msg, ...extra });
const last10 = (s) => String(s || "").replace(/\D/g, "").slice(-10);

const dateFmt = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Dhaka", day: "2-digit", month: "short", year: "numeric",
  hour: "2-digit", minute: "2-digit", hour12: true,
});
const fmtDate = (ms) => dateFmt.format(new Date(ms));

async function safeEqual(a, b) {
  const enc = new TextEncoder();
  const [x, y] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b)),
  ]);
  const u = new Uint8Array(x), v = new Uint8Array(y);
  let d = 0;
  for (let i = 0; i < u.length; i++) d |= u[i] ^ v[i];
  return d === 0;
}

// ---------------- FCM (HTTP v1, service account) ----------------
const b64url = (buf) => {
  let s = "";
  const bytes = new Uint8Array(buf);
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const b64urlStr = (str) => b64url(new TextEncoder().encode(str));

let tokCache = { tok: "", exp: 0 };

async function accessToken(env) {
  if (tokCache.tok && tokCache.exp > Date.now() + 60000) return tokCache.tok;
  const sa = JSON.parse(env.FCM_SA_JSON);
  const now = Math.floor(Date.now() / 1000);
  const head = b64urlStr(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body = b64urlStr(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const pem = String(sa.private_key).replace(/\\n/g, "\n")
    .replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${head}.${body}`));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${head}.${body}.${b64url(sig)}`,
    }),
  });
  const j = await r.json();
  if (!j.access_token) throw new Error("FCM auth failed: " + (j.error_description || j.error || r.status));
  tokCache = { tok: j.access_token, exp: Date.now() + (j.expires_in || 3600) * 1000 };
  return tokCache.tok;
}

// target: {token} বা {topic}; data-র সব মান string হতে হবে
async function sendFcm(env, target, data) {
  if (!env.FCM_PROJECT_ID || !env.FCM_SA_JSON) return { ok: false, err: "সার্ভারে FCM সেট করা নেই" };
  try {
    const at = await accessToken(env);
    const strData = {};
    for (const [k, v] of Object.entries(data)) strData[k] = String(v);
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${at}`, "content-type": "application/json" },
      body: JSON.stringify({ message: { ...target, data: strData, android: { priority: "HIGH", ttl: "86400s" } } }),
    });
    if (r.ok) return { ok: true };
    const j = await r.json().catch(() => ({}));
    const status = j?.error?.status || String(r.status);
    return { ok: false, err: j?.error?.message || status, gone: status === "NOT_FOUND" || status === "UNREGISTERED" };
  } catch (e) {
    return { ok: false, err: String(e.message || e) };
  }
}

const log = (env, phone, cmd, detail, result) =>
  env.DB.prepare("INSERT INTO push_log(ts,phone,cmd,detail,result) VALUES(?,?,?,?,?)")
    .bind(Date.now(), phone, cmd, String(detail).slice(0, 300), String(result).slice(0, 300)).run().catch(() => {});

// ---------------- ইউজার অ্যাপের অ্যাকশন ----------------
async function register(env, p) {
  const name = (p.get("name") || "").trim().replace(/\s+/g, " ");
  const phone = (p.get("phone") || "").trim();
  const token = (p.get("token") || "").trim();
  const digits = phone.replace(/\D/g, "");
  if (!name || name.length > 60) return fail("সঠিক নাম দিন");
  if (digits.length < 10 || digits.length > 15) return fail("সঠিক ফোন নম্বর দিন");
  if (!token || token.length > 1024) return fail("নোটিফিকেশন token পাওয়া যায়নি");
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO users(phone10,phone,name,token,created_at,updated_at) VALUES(?,?,?,?,?,?)
     ON CONFLICT(phone10) DO UPDATE SET name=excluded.name, phone=excluded.phone,
       token=excluded.token, updated_at=excluded.updated_at`
  ).bind(last10(phone), digits, name, token, now, now).run();
  return json({ ok: true, msg: "রেজিস্ট্রেশন সম্পন্ন" });
}

async function updateToken(env, p) {
  const phone = last10(p.get("phone"));
  const token = (p.get("token") || "").trim();
  if (phone.length < 10 || !token || token.length > 1024) return fail("ভুল তথ্য");
  const r = await env.DB.prepare("UPDATE users SET token=?, updated_at=? WHERE phone10=?")
    .bind(token, Date.now(), phone).run();
  if (!r.meta.changes) return fail("রেজিস্ট্রেশন পাওয়া যায়নি");
  return json({ ok: true, msg: "ঠিক আছে" });
}

async function rules(env) {
  const { results } = await env.DB.prepare("SELECT package,reel_ids,skip_ids,reel_desc FROM rules").all();
  const out = {};
  const arr = (s) => { try { const a = JSON.parse(s); return Array.isArray(a) ? a.map(String) : []; } catch { return []; } };
  for (const r of results) out[r.package] = { reel_ids: arr(r.reel_ids), skip_ids: arr(r.skip_ids), reel_desc: arr(r.reel_desc) };
  return json({ ok: true, rules: out });
}

async function version(env) {
  const { results } = await env.DB.prepare(
    "SELECT key,value FROM app_config WHERE key IN ('latest_version_code','latest_version_name','update_url')").all();
  const m = Object.fromEntries(results.map((r) => [r.key, r.value]));
  const code = parseInt(m.latest_version_code, 10);
  return json({
    ok: true, code: Number.isFinite(code) ? code : 0,
    name: m.latest_version_name || "", url: m.update_url || DEFAULT_UPDATE_URL,
  });
}

// ---------------- অ্যাডমিন অ্যাকশন ----------------
async function adminUsers(env) {
  const { results } = await env.DB.prepare(
    "SELECT phone,name,token,created_at FROM users ORDER BY created_at DESC").all();
  return json({
    ok: true,
    users: results.map((u) => ({ name: u.name, phone: u.phone, date: fmtDate(u.created_at), hasToken: !!u.token })),
  });
}

function expiryFrom(p, now) {
  const days = p.get("days");
  if (days) {
    const d = parseInt(days, 10);
    if (!(d >= 1 && d <= 3650)) return { err: "মেয়াদের দিন ১ থেকে ৩৬৫০-এর মধ্যে হতে হবে" };
    return { ms: now + d * DAY };
  }
  const ms = parseInt(p.get("expiry") || "", 10);
  if (!(ms > now)) return { err: "মেয়াদ ভবিষ্যতের হতে হবে" };
  if (ms > now + TEN_YEARS) return { err: "মেয়াদ ১০ বছরের বেশি হতে পারে না" };
  return { ms };
}

async function adminPush(env, p) {
  const cmd = (p.get("cmd") || "").trim().toUpperCase();
  const phone = last10(p.get("phone"));
  if (!CMDS.has(cmd)) return fail("অজানা কমান্ড");
  const u = await env.DB.prepare("SELECT phone,name,token FROM users WHERE phone10=?").bind(phone).first();
  if (!u) return fail("ইউজার পাওয়া যায়নি");

  const now = Date.now();
  const data = { cmd, id: crypto.randomUUID().replace(/-/g, "").slice(0, 16), phone: u.phone, serverNow: now };
  let detail = "";

  if (cmd === "APPROVE" || cmd === "SET_EXPIRY") {
    const e = expiryFrom(p, now);
    if (e.err) return fail(e.err);
    data.expiry = e.ms; detail = `expiry=${fmtDate(e.ms)}`;
  } else if (cmd === "SWITCH_OFF") {
    const t = (p.get("target") || "").toLowerCase();
    if (!TARGETS.has(t)) return fail("ভুল target");
    data.target = t; detail = `target=${t}`;
  } else if (cmd === "NOTICE") {
    const body = (p.get("body") || "").trim().slice(0, 500);
    if (!body) return fail("বার্তা লিখুন");
    data.title = (p.get("title") || "").trim().slice(0, 100) || "NafsGuard";
    data.body = body; detail = data.title;
  }

  if (!u.token) { await log(env, u.phone, cmd, detail, "no token"); return fail("এই ইউজারের notification token নেই"); }
  const r = await sendFcm(env, { token: u.token }, data);
  if (r.gone) await env.DB.prepare("UPDATE users SET token='' WHERE phone10=?").bind(phone).run();
  await log(env, u.phone, cmd, detail, r.ok ? "ok" : r.err);
  if (!r.ok) return fail(r.gone ? "ইউজারের token মেয়াদোত্তীর্ণ; অ্যাপ আবার খুলতে বলুন" : `FCM ব্যর্থ: ${r.err}`);
  return json({ ok: true, msg: "পাঠানো হয়েছে" });
}

async function adminAll(env, p) {
  const body = (p.get("body") || "").trim().slice(0, 500);
  if (!body) return fail("বার্তা লিখুন");
  const title = (p.get("title") || "").trim().slice(0, 100) || "NafsGuard";
  const data = { cmd: "NOTICE", id: crypto.randomUUID().replace(/-/g, "").slice(0, 16), title, body, serverNow: Date.now() };
  const r = await sendFcm(env, { topic: "all" }, data);
  await log(env, "ALL", "NOTICE", title, r.ok ? "ok" : r.err);
  return r.ok ? json({ ok: true, msg: "পাঠানো হয়েছে" }) : fail(`FCM ব্যর্থ: ${r.err}`);
}

// ---------------- রাউটার ----------------
export default {
  async fetch(request, env) {
    try {
      if (request.method !== "GET" && request.method !== "HEAD") return fail("শুধু GET চলবে");
      const p = new URL(request.url).searchParams;
      const action = p.get("action") || "";
      switch (action) {
        case "register": return await register(env, p);
        case "token": return await updateToken(env, p);
        case "rules": return await rules(env);
        case "version": return await version(env);
        case "admin_users": case "admin_push": case "admin_all": {
          const given = request.headers.get("x-admin-key") || p.get("key") || "";
          if (!env.ADMIN_KEY || !given || !(await safeEqual(given, env.ADMIN_KEY)))
            return fail("অ্যাডমিন কী ভুল", { auth: false });
          if (action === "admin_users") return await adminUsers(env);
          if (action === "admin_push") return await adminPush(env, p);
          return await adminAll(env, p);
        }
        default: return fail("অজানা অ্যাকশন");
      }
    } catch (e) {
      return fail("সার্ভারে সমস্যা: " + String(e.message || e).slice(0, 120));
    }
  },
};
