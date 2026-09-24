# البداية السريعة: خمس دقايق لحد ما مشروعكم يشتغل

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · **العربية**
<!-- /languages -->

الصفحة دي بتشغّل مشروع من مشاريعكم جوّه NMOX Studio، وبتشرح بس اللي محتاجينه عشان كده. [دليل المستخدم](user-guide.ar.md) هو الدليل الكامل. لو بتشتغلوا على VS Code، اقروا بعدها [جايين من VS Code](coming-from-vscode.ar.md).

<a id="1-install-one-minute"></a>
## 1. التثبيت (دقيقة واحدة)

**macOS، بـ Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew بيطلب منكم تشغّلوا `brew trust` مرة واحدة بس لأي tap من طرف تالت. مش هيسأل تاني لما تحدّثوا.

**macOS وWindows وLinux، من غير Homebrew:** نزّلوا آخر إصدار لنظامكم من [صفحة الإصدارات](https://github.com/NMOX/NMOX-Studio/releases/latest):

| النظام | الملف | وبعدين |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | اسحبوا التطبيق لمجلد Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | شغّلوا الـ installer. |
| Debian وUbuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Linux تاني | `NMOX-Studio-<version>-linux.tar.gz` | فكّوا الأرشيف وشغّلوا `bin/nmoxstudio`. |

كل ملف من دول جاي ومعاه بيئة تشغيل Java بتاعته، فمفيش أي حاجة تانية تتثبت. الـ zip المحمول بس هو اللي محتاج Java 21 أو أحدث متثبتة على الجهاز من الأول.

على macOS التطبيق متوثّق (notarization) من Apple. أول مرة تفتحوه، macOS بيسأل إذا كنتوا عايزين تفتحوا تطبيق متنزّل من الإنترنت: دوسوا **فتح** (Open).

<a id="2-open-your-project-one-minute"></a>
## 2. فتح مشروعكم (دقيقة واحدة)

شغّلوا **NMOX Studio**. بيفتح بتلات تبويبات: **أهلًا بيك**، و**راك المهام**، و**المتصفح**.

عشان تفتحوا مشروعكم، اختاروا **ملف ◂ فتح مجلد…** (‏⌥⌘O على macOS، وCtrl+Alt+O على Windows وLinux) واختاروا المجلد بتاعه. وتقدروا تعملوا ده كمان من التيرمينال، زي ما بتعملوا مع `code .`:

```bash
cd ~/code/my-app
nmox .
```

الأمر بيرجع على طول. لو NMOX Studio شغال بالفعل، بياخد المجلد؛ ولو مش شغال، بيشتغل. Homebrew والـ installer بتاع Windows وحزم Linux بيحطوا `nmox` على الـ PATH بتاعكم. لو ثبّتوا من الـ DMG، شوفوا [إزاي تحطوا `nmox` على الـ PATH](user-guide.ar.md#2-first-launch).

المجلد بيتحسب مشروع لو فيه `package.json` أو `Cargo.toml` أو `go.mod` أو `pom.xml` أو `composer.json` أو `pyproject.toml` أو واحد من 57 ملف مشروع تاني. ومجلد فيه ملفات HTML عادية بيتحسب برضه.

لما تفتحوا مشروع بتحصل تلات حاجات:

- **استوديو المشاريع**، على الشمال، بيعرض ملفاتكم.
- شريط الحالة، تحت، بيعرض فرع الـ git بتاعكم وعدد الملفات اللي اتغيّرت.
- **راك المهام** بيتجهّز على حسب نوع المشروع. مشروع Vite بياخد كونسول Vite، ومشروع Cargo بياخد مسارات تشغيل وتصحيح واختبار، وهكذا.

<a id="3-run-it-one-minute"></a>
## 3. التشغيل (دقيقة واحدة)

دوسوا **▶** في شريط الأدوات، أو F6. بيشغّل مشروعكم بالطريقة اللي أدواته بتشغّله بيها: سكريبت `dev` أو `start` أو `serve` من `package.json`، ‏`cargo run`، ‏`go run`. وبيستخدم مدير الحزم بتاع المشروع نفسه: npm أو pnpm أو yarn، أو bun في مشروع Bun.

أول مرة تشغّلوا فيها أي حاجة في مشروع، NMOX Studio بيسألكم إذا كنتوا واثقين في المجلد. المشروع اللي ماوثقتوش فيه مش بيشغّل أي كود بتاعه: لا سكريبتات، ولا بناء، ولا اختبارات. للكود بتاعكم، دوسوا **الوثوق في مساحة العمل**.

لو مشروعكم خادم تطوير، عنوانه بيظهر في شريط الحالة جنب علامة **⇄**، والصفحة بتتفتح في تبويب **المتصفح**. عدّلوا ملف واحفظوه، والصفحة بتتحمّل تاني.

عشان توقفوا كل حاجة شغالة، دوسوا **■** اللي جنب ▶، أو ⌥⌘. ‏(Option وCommand والنقطة).

لو مفيش حاجة حصلت، بصّوا على تبويب **Output** تحت. بيشرح ليه التشغيل مقدرش يبدأ، مثلًا إن أداة مش متثبتة أو إن الاعتماديات لسه ما اتثبتتش، وبيعرض عليكم يصلّحها. **أدوات ◂ طبيب البيئة…** بيعرض كل أداة NMOX Studio يقدر يستخدمها وبيوريكم أنهي منهم متثبت.

<a id="4-find-anything-thirty-seconds"></a>
## 4. لاقوا أي حاجة (تلاتين ثانية)

دوسوا **⌘I** (‏Ctrl+I على Windows وLinux) واكتبوا. البحث السريع بيلاقي الملفات، وأوامر القوايم، والرموز، وأجهزة الراك، والخوادم والأوامر الشغالة، والسكريبتات اللي في `package.json` بتاعكم. Enter بيفتح النتيجة أو بيشغّلها.

دوسوا **⌘P** عشان تفتحوا ملف باسمه.

<a id="5-test-it-thirty-seconds"></a>
## 5. الاختبار (تلاتين ثانية)

دوسوا **⌃F6** (‏Ctrl+F6) عشان تشغّلوا اختبارات المشروع. وعشان تشوفوا كل اختبار في المشروع قبل ما تشغّلوا أي حاجة، افتحوا نافذة **الاختبارات** بـ ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## لو مفيش مشروع في إيدكم

- **ملف ◂ مشروع جديد…** بيعمل مشروع حقيقي من قالب (Angular، وVue، وSvelte، وReact مع Vite، وJavaScript صافي، وPHP، وPhoenix وغيرهم). بيعمل الملفات، وبيجهّز git، وبيثبّت الاعتماديات.
- **ملف ◂ مساحة تعلم جديدة…** بيفتح درس خطوة بخطوة. *أول صفحة ويب ليكم* هي أول واحدة في القايمة.

<a id="where-to-go-next"></a>
## تروحوا فين بعد كده

- **[راك المهام](user-guide.ar.md#4-the-task-rack)**. كل أداة بتشغّلوها هي جهاز في الراك، والكابلات بين الأجهزة بتربطها ببعض: مثلًا، تشغّلوا الاختبارات كل ما البناء ينجح.
- **[المحرر](user-guide.ar.md#5-the-editor)**. فيه Emmet، وعيّنات الألوان، والتصحيح بنقاط توقف لـ Node وChrome، وقوالب Angular.
- **[الاستوديوهات](user-guide.ar.md#6-the-studios)**. استوديو الـ API، واستوديو قواعد البيانات، واستوديو العقود، واستوديو البلوكات، ولوحة المهام.
- **[قاموس المصطلحات](glossary.ar.md)** بيشرح الكلمات اللي المنتج نفسه بيستخدمها: الراك، والـ patch، والمخرج والمدخل، والمسار، والتوجيه، وKVASIR.
