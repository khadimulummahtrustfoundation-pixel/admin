# NafsGuard Admin অ্যাপ (সেটআপ)

এই অ্যাপ দিয়ে শীটের মেনু ছাড়াই ইউজারদের অনুমোদন, মেয়াদ, সুইচ বন্ধ, লক খোলা, অ্যাক্সেস বন্ধ ও বার্তা পাঠানো যায়।

## ধাপ ১ — সার্ভার (Apps Script)
1. Extensions -> Apps Script -> Code.gs-এর সব কোড মুছে `Code.gs` ফাইলের পুরো লেখা বসান।
2. Project Settings -> Script Properties-এ এই তিনটি থাকতে হবে:
   - FCM_PROJECT_ID
   - FCM_SA_JSON
   - ADMIN_KEY (নিজে বানানো লম্বা গোপন কী; এটাই অ্যাডমিন অ্যাপে ঢোকার পাসওয়ার্ড)
3. Deploy -> Manage deployments -> Edit (পেনসিল) -> Version: New version -> Deploy।

## ধাপ ২ — APK বানানো
1. GitHub-এ নতুন রিপো বানিয়ে এই ZIP-এর ভেতরের সব ফাইল আপলোড করুন।
2. Actions ট্যাবে Build Admin APK চললে শেষে Artifacts থেকে NafsGuardAdmin-apk নামান।

## ধাপ ৩ — ব্যবহার
অ্যাপ খুলে ADMIN_KEY দিয়ে ঢুকুন। ইউজারের কার্ডে চাপ দিলে কমান্ডের তালিকা আসবে।
