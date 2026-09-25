# درس: اكتبوا جهاز الراك بتاعكم

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · **العربية**
<!-- /languages -->

*قعدة واحدة. هتضيفوا جهاز للراك بمحرر نصوص، وتدوسوا على زراره،
وتشوفوه بيشغّل أمر حقيقي، وتوصّلوا مخرجاته بـ MONITOR — من غير ما
تكتبوا سطر Java واحد.*

جديد في 2.0.0. الراك كان جاي بتلاتة وخمسين جهاز، ولحد دلوقتي كان فيه
طريقة واحدة بس تضيفوا بيها الجهاز الرابع والخمسين: تكتبوا إضافة
NetBeans. ده الطريق التاني.

![راك المهام: رف الأجهزة هو المكان اللي بيظهر فيه أي جهاز من ‎~/.nmox/devices.d، جنب الأجهزة المدمجة](../images/ar/tabs/the-task-rack.png)

## 1. اعملوا المجلد

```bash
mkdir -p ~/.nmox/devices.d
```

ده كل التثبيت. الراك بيقرا المجلد وقت ما يحتاجه، فمفيش حاجة
تتعاد تشغيلها.

## 2. اكتبوا الجهاز

حطّوا ده في ‎`~/.nmox/devices.d/counter.json`:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

كل حاجة فيه ليها شغلانة: **المقبض** بيبقى `{{kind}}` جوه الأمر، ودور
**QUERY** بيلوّن الزرار أزرق (قاعدة الألوان: الأزرق بيسأل، والأخضر
بيعمل، والأحمر بيوقف)، والتلات منافذ بيخلّوه ينفع يتوصّل.

## 3. ركّبوه

افتحوا **راك المهام** (`⌘9`، أو تبويب راك المهام) وبصّوا في درج
**مراقبة** في الرف. COUNTER هناك، والسطر التعريفي بتاعكم تحته. اسحبوه
على قضيب.

اعملوا عليه كليك يمين واختاروا **طريقة استخدام COUNTER…** — ده نص `usage` بتاعكم،
وعشان كده الصيغة مصمّمة على سطرين حقيقيين.

## 4. دوسوا عليه

> لاحظوا إن مفيش سطر `units`: الرف بيقيس الواجهة وبيختار أصغر ارتفاع
> يكفيها (الجهاز ده محتاج 2U عشان المقبض). اكتبوا `units` بس لما تكونوا
> عايزين مساحة زيادة.

وجّهوا الراك على مشروع git، ولفّوا **KIND** على `js`، ودوسوا
**COUNT**.

أول دوسة بتطلّع سؤال **الثقة في مساحة العمل**، لأن ملف الجهاز بيشغّل
أوامر حقيقية، والمضيف بيحرس كل تشغيل بنفس الطريقة اللي بيحرس بيها
الجهاز المدمج. وافقوا، والشاشة هتعرض الأمر، وبعده آخر سطر في المخرجات.
منفذ DONE بينوّر أخضر.

ولو رفضتوا، مفيش حاجة بتشتغل — الرفض هو الميزة.

## 5. وصّلوه

اسحبوا كابل من **OUT** في COUNTER لـ **IN** في MONITOR. دوسوا COUNT
تاني: كل سطر بيوصل للشاشة، لأن أي منفذ متعرّف `OUT`/`DATA` بياخد مخرجات
التشغيل من غير أي إعداد زيادة.

دلوقتي اسحبوا من نبضة TEMPO لمدخل **COUNT** في COUNTER. الجهاز اللي
كتبتوه بمحرر نصوص بقى ماشي على ساعة.

## 6. بوّظوه عن قصد

عدّلوا الملف وغيّروا الأمر لحاجة فيها pipe:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

احفظوا، وCOUNTER *هيختفي* من الرف. ده الفورمات وهو بيرفض سطر shell:
الأمر مصفوفة argv، عشان أي حد بيقرا — إنتوا بعد ست شهور، أو زميل بيراجع
الملف — يشوف بالظبط إيه اللي هيشتغل. سجل الـ IDE بيقول أنهي ملف اتفوّت
وليه:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

رجّعوا شكل المصفوفة والجهاز هيرجع. نفس الكلام لأداة متسمّية بمسار
(`./x.sh`)، أو `{{variable}}` مش معروف، أو `usage` من سطر واحد: الملف
بيتفوّت كله بدل ما يتحمّل نصه، لأن جهاز الليبل بتاعه بيكدب أوحش من
مفيش جهاز خالص.

## اللي اتعلمتوه دلوقتي

- الجهاز **ملف**: ‎`~/.nmox/devices.d/*.json`، بيتقري وقت الحاجة، من غير
  إعادة تشغيل، ومن غير بناء.
- المقابض بتبقى `{{variables}}`؛ والأدوار بتختار الألوان؛ والمنافذ بتخلّيه
  ينفع يتوصّل وتخلّي مخرجاته تتقري.
- **المضيف هو اللي ماسك القواعد** — الثقة في مساحة العمل على كل تشغيل،
  وقاعدة الألوان، وقاموس المنافذ، وقاعدة الرف — فملف الجهاز ميقدرش يعبّر
  عن أمر من غير حراسة أو زرار GO أحمر حتى لو حاول.
- الرفض صوته عالي في السجل وكامل في أثره.

## الخطوة الجاية

- [device-files.md](../device-files.md) — المرجع الكامل
- [راك المهام](the-task-rack.ar.md) — التوصيل، والبوابات، والإعدادات الجاهزة
- [device-spi.md](../device-spi.md) — الـ SPI بتاع Java، للأجهزة اللي محتاجة
  حالة حقيقية: رسم مخصص، أو polling، أو اتصالات عايشة لمدة طويلة
