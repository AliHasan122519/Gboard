# لوحتي · My Keyboard

لوحة مفاتيح أندرويد (عربي + إنجليزي) بتصميم مطابق لمظهر Gboard الداكن (Material You)، مع **تغيير الخط وإضافة خطوط جديدة من لوحة المفاتيح نفسها**.

Android IME (Kotlin, no third-party dependencies) with an Arabic and an English layout styled after Gboard's dark theme. Fonts are chosen — and new ones imported — from the keyboard itself.

## الميزات / Features
- تخطيط عربي مطابق للمرجع (الأرقام الهندية فوق الصف الأول، ‎«؟٣٢١»‎، مفتاح الفاصلة/الإيموجي، مفتاح النقطة مع الحركات) وتخطيط إنجليزي QWERTY مع Shift وCaps Lock.
- الضغط المطوّل: الحروف المشتقة (أ إ آ ء، ئ، ؤ، ة، پ چ ڤ گ …) والحركات (َ ً ُ ٌ ِ ٍ ّ ْ) والأرقام والحروف اللاتينية المشكّلة.
- أيقونة اللوحة الملوّنة 🎨 فوق المفاتيح ← **الخطوط**: معاينة حيّة، اختيار السماكة (الخطوط المتغيرة وعائلة Marlik)، و«إضافة خط» لاستيراد ‎.ttf / .otf‎ من الجهاز (وحذف ما أضفته).
- الحافظة (سجل + تثبيت، محلي فقط)، الإيموجي، قائمة الأدوات، تبديل المظهر (نظام / داكن / فاتح)، الاهتزاز والصوت، ارتفاع المفاتيح.
- حقول كلمات المرور/البريد/الروابط تتحول للإنجليزية تلقائيًا، وحقول الأرقام تفتح صفحة الأرقام.

## مكتبة الخطوط (35 خطًا) / Font library
تُعرض في لوحة الخطوط مرتبة بتصنيفات يمكن التصفية بها من شريط الأزرار أعلى اللوحة: **الكل · المفضلة ★ · خطوطي · عصرية · كوفي · نسخ · رقعة وخط يد · عناوين وزخرفة · مرحة · إنجليزية فقط**. اضغط ☆ على أي خط لإضافته إلى المفضلة.

| التصنيف | الخطوط |
|---|---|
| عصرية | **Dubai** (خطك)، Cairo, Tajawal, Almarai, Noto Sans Arabic, Readex Pro, IBM Plex Sans Arabic, Alexandria, Vazirmatn, Changa, Mada, Rubik, El Messiri, Zain |
| كوفي | Noto Kufi Arabic, Reem Kufi, Kufam |
| نسخ | **Marlik** (خطك، 9 أوزان – TRIAL)، Noto Naskh Arabic, Amiri, Scheherazade New, Lateef, Harmattan, Markazi Text |
| رقعة وخط يد | Aref Ruqaa, Katibeh |
| عناوين وزخرفة | Lalezar, Rakkas, Blaka |
| مرحة | Baloo Bhaijaan 2, Lemonada, Playpen Sans Arabic |
| إنجليزية فقط | Inter, Poppins, Space Grotesk |

الخطوط غير المذكورة بأنها «خطك» مرخّصة SIL OFL 1.1 (من Google Fonts). نصوص تراخيص OFL في `app/src/main/assets/fonts/licenses/`. قائمة الخطوط في `app/src/main/assets/fonts/catalog.json` — أضف خطًا جديدًا بنسخ الملف وإضافة سطر هناك.

> ⚠️ **تراخيص**: *Marlik Web TRIAL* نسخة تجريبية من Rosetta ويلزم ترخيص كامل قبل النشر. تأكد كذلك أن ترخيص خط **Dubai** يسمح بتضمينه في تطبيق.

## البناء / Build
```bash
# JDK 17+ and Android SDK (platform 35, build-tools 35.0.0); set sdk.dir in local.properties
./gradlew assembleDebug testDebugUnitTest
# → app/build/outputs/apk/debug/app-debug.apk   (also copied to dist/MyKeyboard-debug.apk)
# signed release for distribution: tools/make-keystore.sh once, then tools/release.sh
```
التثبيت: ثبّت الـAPK ثم افتح التطبيق واتبع خطوتي «تفعيل لوحة المفاتيح» و«اختيارها».


## التحديث التلقائي من داخل التطبيق / In-app updates
- التطبيق يفحص **آخر إصدار منشور** في صفحة Releases للمستودع (`update_repo` في `app/src/main/res/values/config.xml`) مرة كل يوم في الخلفية، **وعند كل فتح للتطبيق تظهر نافذة «يتوفر تحديث جديد» (تحديث الآن / لاحقًا)** إن وُجد إصدار أحدث، ويرسل إشعارًا عند وجود نسخة أحدث، ويظهر شريط «يتوفر تحديث» داخل قائمة أدوات لوحة المفاتيح.
- من بطاقة «تحديثات التطبيق» يضغط المستخدم «تنزيل وتثبيت»: يُنزَّل الـAPK ويُتحقق من **SHA-256** (إن وفّرته GitHub) ومن أن الحزمة هي نفس التطبيق **وموقّعة بنفس المفتاح**، ثم يعرض أندرويد نافذة تأكيد التثبيت (لا يوجد تثبيت صامت). يحتاج المستخدم مرة واحدة أن يسمح للتطبيق بتثبيت التحديثات.
- الاتصال بالإنترنت يقتصر على github.com وعلى كود التحديث فقط؛ خدمة لوحة المفاتيح لا تستخدم الشبكة.

### نشر تحديث (مرة واحدة للإعداد ثم ثلاث خطوات لكل تحديث)
1. **مرة واحدة:** `tools/make-keystore.sh` ينشئ مفتاح التوقيع `release.jks` و`keystore.properties` (مستثنيان من git). **احتفظ بنسخة احتياطية آمنة**: أي تحديث لازم يُوقَّع بنفس المفتاح وإلا يرفضه أندرويد، وضياعه يعني أن يحذف المستخدمون التطبيق ويعيدوا تثبيته.
2. ارفع `versionCode` و`versionName` في `app/build.gradle.kts` ثم شغّل `tools/release.sh` (يبني ويوقّع `MyKeyboard-<version>.apk`).
3. في GitHub: **Releases ← Draft a new release** ← وسم `v<versionName>` ← أرفق ملف الـAPK ← **Publish release**. (الإصدارات المسودة والتجريبية pre-release تُتجاهل.)

> النسخة المثبّتة حاليًا من `dist/` موقّعة بمفتاح debug مؤقت، لذا **لن تتحدث** من إصدار موقّع بمفتاحك. أول نسخة توزّعها على الناس يجب أن تكون النسخة الموقّعة بمفتاحك.

## الحدود المعروفة / Known limitations
- لا يوجد اقتراح كلمات أو تصحيح تلقائي أو كتابة بالسحب.
- الميكروفون يحوّل إلى لوحة إدخال صوتي مثبّتة على الجهاز (إن وُجدت)؛ لا يوجد تعرف صوتي مدمج.
- أيقونة الملصقات تفتح الإيموجي (لا توجد ملصقات/GIF).
- لا يوجد دعم قارئات الشاشة (TalkBack) للمفاتيح المرسومة بعد.
- بُني كـ APK تجريبي (debug-signed)؛ لم يُختبر على جهاز أو محاكي حقيقي.
