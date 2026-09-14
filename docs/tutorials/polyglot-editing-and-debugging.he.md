# מדריך: עריכה וניפוי בשפות רבות

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · **עברית** · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio עורך יותר מ‑70 שפות עם צביעת תחביר אמיתית, מתאר בנווט ואינטליגנציה של שרתי שפה — ומנפה JavaScript/TypeScript (וגם את הדפדפן) מהקופסה, עם נקודות עצירה שבאמת עוצרות. במדריך הזה נגיע לנקודת עצירה באפליקציית Node.

![נקודת עצירה ב‑JavaScript: הביצוע מושהה, מחסנית הקריאות של Node ומשתני V8 חיים](../images/debug-javascript.png)

## לפני שמתחילים

פתחו (או צרו שלד של) פרויקט Node קטן עם סקריפט שאפשר להריץ, למשל נתיב של Express או `node server.js` פשוט.

## צעדים

1. **פתחו קובץ מקור.** צביעה, התאמת סוגריים, קיפול קוד וסימון מופעים עולים מעצמם. **הנווט** מציג את מבנה הקובץ; שרתי שפה (שמותקנים לפי הרמזים של `כלים ◂ אבחון הסביבה…`) מוסיפים השלמה ואבחונים.

2. **הציבו נקודת עצירה.** לחצו בשוליים של העורך על שורה בתוך ה‑handler שלכם — מופיעה נקודה שמסמנת נקודת עצירה.

3. **נפו את הקובץ.** הריצו **ניפוי הקובץ (נקודות עצירה)** (או **ניפוי ב‑Chrome (נקודות עצירה)** לדף HTML/JS). בקשת אמון בסביבת העבודה, חד־פעמית, שומרת על ההפעלה; אחר כך המתאם המצורף `js-debug` מפעיל את התוכנית שלכם.

4. **הגיעו לנקודת העצירה.** הפעילו את מסלול הקוד (שלחו את הבקשה, או תנו לסקריפט להגיע לשורה). הביצוע **נעצר** בנקודת העצירה — בחנו משתנים, עברו על מחסנית הקריאות, התקדמו שורה אחר שורה או היכנסו לתוך פונקציה. בניפוי דפדפן נפתח Chrome עם פרופיל חד־פעמי בכתובת החיה של שרת הפיתוח, ונקודות העצירה בדף ממופות בחזרה ל‑IDE.

## מה למדתם

- העורך מתייחס ליותר מ‑70 שפות כאל אזרחיות מן המניין (דקדוקי TextMate + ‏CSL + ‏LSP); גם קובצי תצורה (YAML, ‏TOML, ‏Dockerfile, ‏nginx…) מכוסים.
- ניפוי JS/TS מובנה — מרבב סשנים משטח את סשני הצאצא של js-debug, כדי שהמנפה של הפלטפורמה, שתומך בסשן יחיד, יוכל להפעיל אותו.
- כל הפעלה של ניפוי עוברת דרך שער האמון, ובעצירה נהרג עץ התהליכים כולו (בלי יתומים).

## הלאה

- **הרצת הבדיקה הממוקדת** מריצה מתודת בדיקה אחת, בכל שפה.
- אבחונים מכלי הראק (eslint/tsc/phpstan) נוחתים בחלון Action Items של הפלטפורמה.
