# NafsGuard Admin অ্যাপ (Cloudflare Worker + D1)

এই অ্যাপ দিয়ে ইউজারদের অনুমোদন, মেয়াদ বদল, সুইচ বন্ধ, লক খোলা, পাসওয়ার্ড রিসেট, অ্যাক্সেস বন্ধ ও বার্তা (একজন/সবাইকে) পাঠানো যায়।

## ধাপ ১ — D1 ডাটাবেজ
1. Cloudflare Dashboard -> Storage & Databases -> D1 -> Create database, নাম: `nafsguard`
2. ডাটাবেজের Console ট্যাবে `server/schema.sql`-এর ৫টি অংশ একটা একটা করে চালান (১. users, ২. rules, ৩. app_config, ৪. push_log, ৫. INSERT)।

## ধাপ ২ — Worker
1. Workers & Pages -> Create -> Worker, নাম অবশ্যই `nafsguard-api`।
2. Edit code-এ সব মুছে `server/worker.js`-এর পুরো লেখা বসিয়ে Deploy করুন।
3. Worker -> Settings -> Bindings -> Add -> D1 database: Variable name `DB`, ডাটাবেজ `nafsguard`।
4. Worker -> Settings -> Variables and Secrets -> Add (type: Secret):
   - `FCM_PROJECT_ID` : Firebase প্রজেক্ট আইডি
   - `FCM_SA_JSON` : Firebase service account-এর পুরো JSON ফাইলের লেখা
   - `ADMIN_KEY` : নিজের বানানো লম্বা গোপন কী (এটাই অ্যাডমিন অ্যাপের পাসওয়ার্ড)
5. Deploy করুন। ব্রাউজারে `<worker-লিংক>?action=version` খুললে `{"ok":true,...}` আসলে ঠিক আছে।

## ধাপ ৩ — APK বানানো
1. GitHub-এ নতুন রিপো খুলে এই ফোল্ডারের সব ফাইল আপলোড করুন (`server/` ফোল্ডার থাকলেও সমস্যা নেই)।
2. Actions ট্যাবে `Build Admin APK` চললে শেষে Artifacts থেকে `NafsGuardAdmin-apk` নামান।
3. Worker-এর লিংক আলাদা হলে `Api.kt`-এর `DEFAULT_ENDPOINT` বদলান, অথবা অ্যাপের লগইন পর্দায় লিংক ঘরে সরাসরি লিখুন।

## ধাপ ৪ — ব্যবহার
অ্যাপ খুলে সার্ভার লিংক ঠিক আছে কিনা দেখে `ADMIN_KEY` দিয়ে ঢুকুন। ইউজারের কার্ডে চাপ দিলে কমান্ডের তালিকা আসে।
- "টোকেন নেই" দেখালে ইউজারকে ইউজার অ্যাপ একবার খুলতে বলুন।
- কী ভুল হলে অ্যাপ নিজে থেকে লগইন পর্দায় ফেরে।
- প্রতিটি কমান্ড D1-এর `push_log` টেবিলে জমা হয়: `SELECT * FROM push_log ORDER BY id DESC LIMIT 50;`

## নতুন ভার্সন প্রকাশ (ইউজার অ্যাপ)
D1 Console-এ চালান:
```sql
UPDATE app_config SET value='5' WHERE key='latest_version_code';
UPDATE app_config SET value='2.2' WHERE key='latest_version_name';
```
