# מדריך: מרחבי למידה

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · **עברית** · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![הבורר של מרחב למידה חדש — חיפוש במדריכים המובנים, ובדיקת הזמינות אומרת מראש אם במחשב הזה יש את הכלי של המרחב](../images/tabs/learning-spaces.png)

מרחב למידה הוא ארגז חול עצמאי ללימוד שפה, מסגרת עבודה או ספרייה: NMOX Studio יוצר קוד לדוגמה, מדריך מודרך וראק שכבר מחובר אליו **REPL אמיתי בתוך הראק** שמקלידים לתוכו. יש 93 מרחבים מובנים.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## פתיחה

`קובץ ◂ מרחב למידה חדש…` (המשגר מונה את כל המרחבים המובנים).

## צעדים

1. **בחרו מרחב.** בחרו אחד — Python, ‏Rust, ‏Solid, ‏htmx, ‏Solidity, ‏Elm, ‏REPL לשפת מערכות, מרחב E2E/Playwright ועוד. הבורר בודק קודם אם המפרש או שרשרת הכלים זמינים.

2. **תנו לו להיווצר.** NMOX Studio יוצר את המרחב תחת `~/.nmox/learn/<slug>`: דוגמה מינימלית שעובדת, ומדריך שמוביל אתכם דרכה ומפנה לקונסולה או למכשיר הרלוונטיים.

3. **הקלידו ב‑REPL.** הראק המחווט מראש כולל מכשיר **REPL** שחוגת ה‑ENGINE שלו מכוונת לשפת המרחב (מנוע אחד לכל שפת REPL בקטלוג, ולכל אחד דגלים מוגדרים מראש שמכריחים מצב אינטראקטיבי). הקלידו ביטוי ולחצו Enter — הפלט זורם אל מסך ה‑REPL. חסר מפרש? הכפתור **INSTALL** מתקין אותו מהראק.

4. **עקבו אחרי המדריך.** עברו על הצעדים; קוד הדוגמה אמיתי ואפשר להריץ אותו, והמרחב שלכם לשנות כרצונכם.

## מה למדתם

- מרחב למידה הוא פרויקט מלא, מדריך וראק מחווט — לא סתם קטע קוד.
- ה‑REPL הוא תהליך אינטראקטיבי אמיתי, לא הקלטה מוכנה.
- אפשר להוסיף מרחבים משלכם: שימו `*.json` בתיקייה `~/.nmox/learn-catalog.d/`, והוא מצטרף לבורר (הסכמה מתוארת ב‑[learning-spaces.md](../learning-spaces.md)).

## הלאה

- מרחבים של מסגרות עבודה (Astro/SvelteKit/Nuxt/Next) מפנים לקונסולה שלהם בראק (COSMOS/KINETIC/NIMBUS/NEXUS).
