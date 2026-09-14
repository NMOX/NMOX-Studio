# درس: مساحات التعلم

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · **العربية**
<!-- /languages -->

![نافذة اختيار مساحة التعلم الجديدة — بحث في الدروس المدمجة، وفحص التوفّر بيقولكم من الأول الجهاز ده عليه أداة المساحة ولا لأ](../images/tabs/learning-spaces.png)

مساحة التعلم ساحة تجربة مستقلة لتعلم لغة، أو framework، أو مكتبة: NMOX
Studio بيولّد كود مثال، ودرس خطوة بخطوة، وراك متوصّل من الأول فيه **REPL
حقيقي جوه الراك** بتكتبوا فيه. فيه 93 مساحة مدمجة.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## افتحوها

`ملف ◂ مساحة تعلم جديدة…` (المشغّل بيعرض كل مساحة مدمجة).

## الخطوات

1. **اختاروا مساحة.** اختاروا واحدة — Python، أو Rust، أو Solid، أو htmx،
   أو Solidity، أو Elm، أو REPL للغة أنظمة، أو مساحة E2E/Playwright،
   وغيرهم. نافذة الاختيار بتفحص الأول المفسّر أو سلسلة الأدوات موجودين ولا
   لأ.

2. **سيبوها تتولّد.** NMOX Studio بيعمل المساحة تحت
   `~/.nmox/learn/<slug>`: مثال صغير شغال ومعاه درس بيمشي معاكم فيه خطوة
   بخطوة، وبيشاور على الكونسول أو الجهاز المناسب.

3. **اكتبوا في الـ REPL.** الراك المتوصّل من الأول فيه جهاز **REPL** مقبض
   الـ ENGINE بتاعه مظبوط على لغة المساحة (26 محرك، كل واحد معاه flags
   الوضع التفاعلي الإجباري جاهزة). اكتبوا تعبير، ودوسوا Enter — المخرجات
   بتنزل على شاشة الـ REPL. المفسّر ناقص؟ زرار **INSTALL** بيثبّته من
   الراك.

4. **امشوا على الدرس.** اشتغلوا على الخطوات؛ كود المثال حقيقي وبيشتغل،
   والمساحة بتاعتكم، غيّروا فيها براحتكم.

## اللي اتعلمتوه

- مساحة التعلم مشروع كامل ودرس وراك متوصّل، مش مجرد snippet.
- الـ REPL عملية تفاعلية حقيقية، مش تسجيل بيتعاد.
- تقدروا تضيفوا بتاعتكم: حطوا ملف `*.json` في `~/.nmox/learn-catalog.d/`
  وهينضم لنافذة الاختيار (شوفوا [learning-spaces.md](../learning-spaces.md)
  عشان الـ schema).

## بعد كده

- مساحات الـ frameworks ‏(Astro/SvelteKit/Nuxt/Next) بتشاور على كونسول
  الراك بتاعها (COSMOS/KINETIC/NIMBUS/NEXUS).
