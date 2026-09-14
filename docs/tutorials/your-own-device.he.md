# מדריך: כתבו מכשיר ראק משלכם

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · **עברית** · [العربية](your-own-device.ar.md)
<!-- /languages -->

*ישיבה אחת. תוסיפו מכשיר לראק בעזרת עורך טקסט, תלחצו על הכפתור שלו,
תראו אותו מריץ פקודה אמיתית ותחווטו את הפלט שלו אל MONITOR — בלי
לכתוב שורה אחת של Java.*

חדש בגרסה 2.0.0. הראק הגיע עם חמישים ושלושה מכשירים, ועד עכשיו הייתה
רק דרך אחת להוסיף את החמישים וארבעה: לכתוב תוסף NetBeans. זו הדרך
האחרת.

![ראק המשימות: מדף המכשירים משמאל הוא המקום שבו מכשיר מ‑~/.nmox/devices.d מופיע, לצד המכשירים המובנים](../images/tabs/the-task-rack.png)

## 1. צרו את התיקייה

```bash
mkdir -p ~/.nmox/devices.d
```

זה כל שלב ההתקנה. הראק קורא את התיקייה רק כשצריך, כך שאין מה להפעיל
מחדש.

## 2. כתבו את המכשיר

שימו את זה ב‑`~/.nmox/devices.d/counter.json`:

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

לכל דבר כאן יש תפקיד: ה**חוגה** הופכת ל‑`{{kind}}` בפקודה, התפקיד
**QUERY** צובע את הכפתור בכחול (חוק הצבעים: כחול שואל, ירוק עושה, אדום
עוצר), ושלוש היציאות מאפשרות לחווט אותו.

## 3. הרכיבו אותו

פתחו את **ראק המשימות** (`⌘9`, או הלשונית ראק המשימות) והסתכלו במגירת
**תצפית** של המדף. COUNTER נמצא שם, עם שורת התיאור שלכם מתחתיו. גררו
אותו אל מסילה.

לחצו עליו לחיצה ימנית ובחרו **איך משתמשים ב‑COUNTER…** — זה הטקסט של `usage` שלכם, ובגלל זה
הפורמט מתעקש על שתי שורות אמיתיות.

## 4. לחצו עליו

> שימו לב שאין שורת `units`: המדף מודד את החזית ובוחר את הגובה הקטן
> ביותר שמתאים (כאן צריך 2U בשביל החוגה). הצהירו על `units` רק כשאתם
> רוצים מקום נוסף.

כוונו את הראק אל פרויקט git, סובבו את **KIND** אל `js` ולחצו
**COUNT**.

הלחיצה הראשונה מעלה את בקשת **אמון בסביבת העבודה**, כי קובץ מכשיר מריץ
פקודות אמיתיות, והמארח שומר על כל הפעלה בדיוק כמו במכשיר מובנה. אשרו,
והצג מראה את הפקודה ואחר כך את שורת הפלט האחרונה. שקע DONE מהבהב בירוק.

סרבו במקום זאת, ושום דבר לא מופעל — הסירוב הוא התכונה.

## 5. חווטו אותו

גררו כבל מ‑**OUT** של COUNTER אל **IN** של MONITOR. לחצו שוב COUNT:
כל שורה נוחתת על המוניטור, כי יציאה מוצהרת מסוג `OUT`/`DATA` מקבלת את
הפלט של ההרצה בלי שום הגדרה נוספת.

עכשיו גררו מה‑tick של TEMPO אל כניסת **COUNT** של COUNTER. המכשיר
שכתבתם בעורך טקסט רץ עכשיו לפי שעון.

## 6. שברו אותו בכוונה

ערכו את הקובץ ושנו את הפקודה למשהו עם צינור (pipe):

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

שמרו, ו‑COUNTER *נעלם* מהמדף. זה הפורמט שמסרב לשורת shell: פקודה היא
מערך argv, כדי שמי שקורא את הקובץ — אתם בעוד חצי שנה, או עמית שסוקר
אותו — יראה בדיוק מה ירוץ. יומן ה‑IDE אומר איזה קובץ דולג ולמה:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

החזירו את צורת המערך, והמכשיר חוזר. אותו דבר נכון לכלי שנקרא לפי נתיב
(`./x.sh`), ל‑`{{variable}}` לא מוכר או ל‑`usage` של שורה אחת: הקובץ
מדולג כולו ולא נטען חצי, כי מכשיר שהתווית שלו משקרת גרוע ממכשיר שאינו
קיים.

## מה למדתם עכשיו

- מכשיר הוא **קובץ**: `~/.nmox/devices.d/*.json`, נקרא רק כשצריך, בלי
  הפעלה מחדש ובלי בנייה.
- חוגות הופכות ל‑`{{variables}}`; תפקידים בוחרים צבעים; יציאות מאפשרות
  לחווט אותו ולקרוא את הפלט שלו.
- **המארח שומר על החוקים** — אמון בסביבת העבודה בכל הפעלה, חוק הצבעים,
  אוצר המילים של היציאות, חוק המדף — כך שקובץ מכשיר אינו יכול לבטא פקודה
  בלי שער או GO אדום, גם אם ינסה.
- סירובים רועשים ביומן ומוחלטים בתוצאה.

## הלאה

- [device-files.md](../device-files.md) — המדריך המלא לפורמט
- [ראק המשימות](the-task-rack.he.md) — חיווט, שערים והגדרות מוכנות
- [device-spi.md](../device-spi.md) — ה‑SPI של Java, למכשירים שצריכים
  מצב אמיתי: ציור מותאם, תשאול חוזר, חיבורים ארוכים
