# لوحتي · My Keyboard

لوحة مفاتيح أندرويد (عربي + إنجليزي) بتصميم مطابق لمظهر Gboard الداكن (Material You)، مع **تغيير الخط وإضافة خطوط جديدة من لوحة المفاتيح نفسها**.

Android IME (Kotlin, no third-party dependencies) with an Arabic and an English layout styled after Gboard's dark theme. Fonts are chosen — and new ones imported — from the keyboard itself.

## الميزات / Features
- تخطيط عربي مطابق للمرجع (الأرقام الهندية فوق الصف الأول، ‎«؟٣٢١»‎، مفتاح الفاصلة/الإيموجي، مفتاح النقطة مع الحركات) وتخطيط إنجليزي QWERTY مع Shift وCaps Lock.
- الضغط المطوّل: الحروف المشتقة (أ إ آ ء، ئ، ؤ، ة، پ چ ڤ گ …) والحركات (َ ً ُ ٌ ِ ٍ ّ ْ) والأرقام والحروف اللاتينية المشكّلة.
- أيقونة اللوحة الملوّنة 🎨 فوق المفاتيح ← **الخطوط**: معاينة حيّة، اختيار السماكة (الخطوط المتغيرة وعائلة Marlik)، و«إضافة خط» لاستيراد ‎.ttf / .otf‎ من الجهاز (وحذف ما أضفته).
- الحافظة (سجل + تثبيت، محلي فقط)، الإيموجي، قائمة الأدوات، تبديل المظهر (نظام / داكن / فاتح)، الاهتزاز والصوت، ارتفاع المفاتيح.
- حقول كلمات المرور/البريد/الروابط تتحول للإنجليزية تلقائيًا، وحقول الأرقام تفتح صفحة الأرقام.

## الخطوط المضمّنة / Bundled fonts (35)
| المصدر | الخطوط |
|---|---|
| قدّمتها أنت | **Dubai** (Bold)، **Marlik** (9 أوزان – نسخة TRIAL) |
| SIL OFL 1.1 (Google Fonts) | Cairo, Tajawal, Almarai, Noto Sans/Kufi/Naskh Arabic, Readex Pro, IBM Plex Sans Arabic, Alexandria, Vazirmatn, Changa, Mada, Rubik, El Messiri, Reem Kufi, Kufam, Baloo Bhaijaan 2, Lemonada, Playpen Sans Arabic, Amiri, Scheherazade New, Lateef, Harmattan, Markazi Text, Aref Ruqaa, Lalezar, Rakkas, Zain, Katibeh, Blaka, Inter, Poppins, Space Grotesk |

نصوص تراخيص OFL في `app/src/main/assets/fonts/licenses/`. قائمة الخطوط في `app/src/main/assets/fonts/catalog.json` — أضف خطًا جديدًا بنسخ الملف وإضافة سطر هناك.

> ⚠️ **تراخيص**: *Marlik Web TRIAL* نسخة تجريبية من Rosetta ويلزم ترخيص كامل قبل النشر. تأكد كذلك أن ترخيص خط **Dubai** يسمح بتضمينه في تطبيق.

## البناء / Build
```bash
# JDK 17+ and Android SDK (platform 35, build-tools 35.0.0); set sdk.dir in local.properties
./gradlew assembleDebug testDebugUnitTest
# → app/build/outputs/apk/debug/app-debug.apk   (also copied to dist/MyKeyboard-debug.apk)
```
التثبيت: ثبّت الـAPK ثم افتح التطبيق واتبع خطوتي «تفعيل لوحة المفاتيح» و«اختيارها».

## الحدود المعروفة / Known limitations
- لا يوجد اقتراح كلمات أو تصحيح تلقائي أو كتابة بالسحب.
- الميكروفون يحوّل إلى لوحة إدخال صوتي مثبّتة على الجهاز (إن وُجدت)؛ لا يوجد تعرف صوتي مدمج.
- أيقونة الملصقات تفتح الإيموجي (لا توجد ملصقات/GIF).
- لا يوجد دعم قارئات الشاشة (TalkBack) للمفاتيح المرسومة بعد.
- بُني كـ APK تجريبي (debug-signed)؛ لم يُختبر على جهاز أو محاكي حقيقي.
