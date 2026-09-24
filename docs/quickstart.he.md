# התחלה מהירה: חמש דקות עד שהפרויקט שלכם רץ

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · **עברית** · [العربية](quickstart.ar.md)
<!-- /languages -->

העמוד הזה מביא פרויקט אחד שלכם לרוץ בתוך NMOX Studio, ומסביר רק את מה שצריך בשביל זה. [המדריך למשתמש](user-guide.he.md) הוא המדריך המלא. אם אתם עובדים עם VS Code, קראו אחר כך את [מגיעים מ‑VS Code](coming-from-vscode.he.md).

<a id="1-install-one-minute"></a>
## 1. התקנה (דקה אחת)

**macOS, עם Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew מבקש להריץ `brew trust` פעם אחת לכל tap של צד שלישי. בעדכונים הוא לא ישאל שוב.

**macOS, ‏Windows, ‏Linux, בלי Homebrew:** הורידו את הגרסה האחרונה למערכת שלכם מ[עמוד הגרסאות](https://github.com/NMOX/NMOX-Studio/releases/latest):

| מערכת | קובץ | ואז |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | גררו את האפליקציה לתיקייה Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | הריצו את המתקין. |
| Debian, ‏Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Linux אחר | `NMOX-Studio-<version>-linux.tar.gz` | פרסו את הארכיון והריצו את `bin/nmoxstudio`. |

כל אחד מהקבצים האלה כולל סביבת ריצה של Java משלו, כך שאין שום דבר נוסף להתקין. רק ה‑zip הנייד צריך Java 21 ומעלה שכבר מותקנת במחשב.

ב‑macOS האפליקציה עברה notarization אצל Apple. בפעם הראשונה שפותחים אותה, macOS שואל אם לפתוח אפליקציה שהורדה מהאינטרנט: לחצו **פתח** (Open).

<a id="2-open-your-project-one-minute"></a>
## 2. פתיחת הפרויקט (דקה אחת)

הפעילו את **NMOX Studio**. הוא נפתח עם שלוש לשוניות: **ברוכים הבאים**, **ראק המשימות** ו**דפדפן**.

כדי לפתוח את הפרויקט שלכם, בחרו **קובץ ◂ פתיחת תיקייה…** (‏⌥⌘O ב‑macOS, ‏Ctrl+Alt+O ב‑Windows וב‑Linux) ובחרו את התיקייה שלו. אפשר לעשות את זה גם ממסוף, כמו עם `code .`:

```bash
cd ~/code/my-app
nmox .
```

הפקודה חוזרת מיד. אם NMOX Studio כבר רץ, הוא מקבל את התיקייה; אם לא, הוא מופעל. Homebrew, מתקין Windows וחבילות Linux מכניסים את `nmox` ל‑PATH שלכם. להתקנה מה‑DMG, ראו [איך מכניסים את `nmox` ל‑PATH](user-guide.he.md#2-first-launch).

תיקייה נחשבת לפרויקט אם יש בה `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` או אחד מ‑57 קובצי פרויקט אחרים. גם תיקייה של קובצי HTML פשוטים נחשבת.

כשפותחים פרויקט קורים שלושה דברים:

- **אולפן הפרויקטים**, משמאל, מציג את הקבצים שלכם.
- שורת המצב, למטה, מציגה את ענף ה‑git שלכם ואת מספר הקבצים שהשתנו.
- **ראק המשימות** מוכן לסוג הפרויקט. פרויקט Vite מקבל מסוף Vite, פרויקט Cargo מקבל מסלולי הרצה, ניפוי ובדיקה, וכן הלאה.

<a id="3-run-it-one-minute"></a>
## 3. הרצה (דקה אחת)

לחצו על **▶** בסרגל הכלים, או F6. הוא מריץ את הפרויקט כמו שהכלים שלו מריצים אותו: הסקריפט `dev`, ‏`start` או `serve` מתוך `package.json`, ‏`cargo run`, ‏`go run`. הוא משתמש במנהל החבילות של הפרויקט עצמו: npm, ‏pnpm או yarn, או bun בפרויקט Bun.

בפעם הראשונה שמריצים משהו בפרויקט, NMOX Studio שואל אם אתם סומכים על התיקייה. פרויקט שלא נתתם בו אמון לא מריץ שום קוד משלו: לא סקריפטים, לא בניות ולא בדיקות. לקוד שלכם, לחצו **אמון בסביבת העבודה**.

אם הפרויקט שלכם הוא שרת פיתוח, הכתובת שלו מופיעה בשורת המצב ליד הסימן **⇄**, והדף נפתח בלשונית **דפדפן**. ערכו קובץ ושמרו, והדף נטען מחדש.

כדי לעצור את כל מה שרץ, לחצו על **■** שליד ▶, או ⌥⌘. ‏(Option, ‏Command ונקודה).

אם שום דבר לא קורה, בדקו את לשונית **Output** למטה. היא מסבירה למה ההרצה לא יכלה להתחיל, למשל שכלי לא מותקן או שהתלויות עוד לא הותקנו, ומציעה לתקן. **כלים ◂ אבחון הסביבה…** מונה כל כלי ש‑NMOX Studio יודע להשתמש בו ומראה אילו מהם מותקנים.

<a id="4-find-anything-thirty-seconds"></a>
## 4. למצוא כל דבר (שלושים שניות)

לחצו **⌘I** (‏Ctrl+I ב‑Windows וב‑Linux) והקלידו. החיפוש המהיר מוצא קבצים, פעולות בתפריטים, סמלים, מכשירים בראק, שרתים ופקודות שרצים, ואת הסקריפטים ב‑`package.json` שלכם. Enter פותח את התוצאה או מריץ אותה.

לחצו **⌘P** כדי לפתוח קובץ לפי שמו.

<a id="5-test-it-thirty-seconds"></a>
## 5. בדיקה (שלושים שניות)

לחצו **⌃F6** (‏Ctrl+F6) כדי להריץ את הבדיקות של הפרויקט. כדי לראות כל בדיקה בפרויקט עוד לפני שמריצים משהו, פתחו את חלון **בדיקות** עם ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## אם אין לכם פרויקט בהישג יד

- **קובץ ◂ פרויקט חדש…** יוצר פרויקט אמיתי מתבנית (Angular, ‏Vue, ‏Svelte, ‏React עם Vite, ‏JavaScript נקי, PHP, ‏Phoenix ועוד). הוא יוצר את הקבצים, מאתחל git ומתקין את התלויות.
- **קובץ ◂ מרחב למידה חדש…** פותח מדריך מודרך. *דף הווב הראשון שלכם* נמצא ראשון ברשימה.

<a id="where-to-go-next"></a>
## לאן ממשיכים

- **[ראק המשימות](user-guide.he.md#4-the-task-rack)**. כל כלי שאתם מריצים הוא מכשיר בראק, וכבלים בין מכשירים משרשרים אותם: למשל, להריץ את הבדיקות בכל פעם שהבנייה עוברת.
- **[העורך](user-guide.he.md#5-the-editor)**. כולל Emmet, דוגמיות צבע, ניפוי עם נקודות עצירה ל‑Node ול‑Chrome, ותבניות Angular.
- **[האולפנים](user-guide.he.md#6-the-studios)**. אולפן ה‑API, אולפן מסדי הנתונים, אולפן החוזים ואולפן הבלוקים, ולוח המשימות.
- **[מילון המונחים](glossary.he.md)** מסביר את המילים של המוצר עצמו: ראק, patch, שקע, מסלול, כיוון, KVASIR.
