package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.UiLocale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each translation follows its own language's typography, not English's.
 *
 * <p>A value can be right word for word and still read like a translation.
 * The tell is rarely vocabulary. It is the conventions a native reader never
 * thinks about: German „Anführungszeichen“, the no-break space French puts
 * before a colon, the full-width {@code ：} after Chinese, one form of address
 * throughout. Before v2.150.0 every language got some of these right and some
 * wrong. German used „ 23 times and a straight {@code "} 62 times, Spanish
 * addressed the reader as <i>usted</i> in one module and <i>tú</i> in all the
 * others, and Chinese and Hindi put the mnemonic after the ellipsis
 * ({@code 浏览...(&B)}), which no Chinese software does. None of it was wrong
 * enough for any other gate to see.
 *
 * <p>The rules live in {@code docs/i18n/conventions.md}, one section per
 * language, and this gate holds the mechanical half of each section. Two
 * laws are written down here. The population is the ASSEMBLED cluster:
 * every shipped product bundle and every branding overlay. The languages
 * come from {@code UiLocale.SUPPORTED}, so a new language fails the build
 * until it declares its conventions.
 *
 * <p>Markup, placeholders, entities and backticked code are masked before
 * any rule runs. A straight quote inside {@code <font color='…'>} or
 * {@code `npm "run"`} belongs to the code, not the sentence.
 */
class NativeTypographyGateTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");
    private static final Path CONVENTIONS = Path.of("..", "docs", "i18n", "conventions.md");

    /** One language's typographic decisions, as {@code conventions.md} states them. */
    private record Convention(String open, String close, boolean appendedMnemonic, Pattern wrongRegister) {
        boolean straightQuotesAreNative() {
            return "\"".equals(open);
        }
    }

    private static final String NB = "\u00a0";

    private static final Map<String, Convention> CONVENTION = new LinkedHashMap<>();

    static {
        CONVENTION.put("es", new Convention("«", "»", false,
                word("(?i)", "pulse|haga|elija|seleccione|apunte|indique|usted"
                        // usted imperatives with an object pronoun (v2.153.0: «guárdelo primero»)
                        + "|guárdel[oa]s?|ábral[oa]s?|cámbiel[oa]s?|selecciónel[oa]s?|instálel[oa]s?|actualícel[oa]s?|reinícial[oa]s?|cópiel[oa]s?|pruébel[oa]s?|ciérrel[oa]s?")));
        CONVENTION.put("fr", new Convention("«" + NB, NB + "»", false,
                word("", "tu|toi|ton|ta|tes")));
        CONVENTION.put("de", new Convention("„", "“", false,
                word("", "du|dich|dir|dein|deine|deinen|deinem|deiner|deines")));
        CONVENTION.put("ru", new Convention("«", "»", false, null));
        CONVENTION.put("uk", new Convention("«", "»", false, null));
        CONVENTION.put("pl", new Convention("„", "”", false, null));
        CONVENTION.put("pt", new Convention("“", "”", false, null));
        CONVENTION.put("id", new Convention("“", "”", false, null));
        CONVENTION.put("tl", new Convention("“", "”", false, null));
        CONVENTION.put("vi", new Convention("“", "”", false, null));
        CONVENTION.put("zh", new Convention("“", "”", true, null));
        CONVENTION.put("hi", new Convention("“", "”", true, null));
        // Hebrew keeps the straight mark on purpose: curly quotes are not
        // mirrored by the bidi algorithm and render backwards in an RTL line.
        CONVENTION.put("he", new Convention("\"", "\"", true, null));
        // Egyptian Arabic addresses the reader in the plural imperative, like
        // Hebrew and for the same reason: the singular imperative has a gender.
        // Only forms that cannot be read as a noun: unvowelled شغل is also
        // "work" (شغل حلو, "nice work"), so it is not on the list.
        CONVENTION.put("ar", new Convention("«", "»", true,
                word("", "افتح|اختار|اضغط|دوس|اكتب|جرب|روح|خلي|استخدم|اسأل")));
    }

    private static Pattern word(String flags, String alternatives) {
        return Pattern.compile(flags + "(?<![\\p{L}\\-/.])(" + alternatives + ")(?!\\p{L})");
    }

    private static final Pattern OPAQUE = Pattern.compile("<[^>]*>|\\{[^{}]*\\}|&[a-z]+;|`[^`]*`");
    private static final String PH = "\uE000";
    private static final String CODE = "\uE001";
    /** Platform keyword lists are split on ASCII commas by code; examples are typed input. */
    private static final Pattern SKIP_KEY = Pattern.compile("^KW_|\\.example$");

    private static final Pattern ELLIPSIS = Pattern.compile("(?<!\\.)\\.\\.\\.(?!\\.)");
    private static final Pattern STRAIGHT_PAIR = Pattern.compile(
            "(?<![\\w=\\\\/\"])\"([^\"\\n]{1,60}?)\"(?![\\w\"])", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern FR_PLAIN_SPACE = Pattern.compile(
            "(?<=\\S) [:;!?»](?=\\s|$|[)\"«" + PH + CODE + "])|« ");
    private static final String HAN = "\\u4e00-\\u9fff";
    private static final Pattern ZH_ASCII_PUNCT = Pattern.compile(
            "[" + HAN + "）”][,:;?!]"
            + "|(?<=[\\w" + PH + CODE + ")])[,:;?!]\\s*(?=[" + HAN + "“（])"
            + "|[" + HAN + "] +[“”]|[“”] +[" + HAN + "]",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MNEMONIC_AFTER_ELLIPSIS = Pattern.compile("…\\(&.\\)");

    private record Value(String lang, String where, String key, String text) {
        String masked() {
            Matcher m = OPAQUE.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, m.group().startsWith("{") ? PH : CODE);
            }
            m.appendTail(sb);
            return sb.toString();
        }

        String name() {
            return lang + " " + where + " " + key + " = " + text;
        }
    }

    @Test
    @DisplayName("every shipped language declares its conventions, here and in conventions.md")
    void everyLanguageDeclaresItsConventions() throws IOException {
        String doc = Files.readString(CONVENTIONS, StandardCharsets.UTF_8);
        Set<String> documented = new TreeSet<>();
        Matcher h = Pattern.compile("(?m)^## (.+)$").matcher(doc);
        while (h.find()) {
            for (String part : h.group(1).split("·")) {
                documented.add(part.trim().split("\\s+", 2)[0]);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String lang : shippedLanguages()) {
            if (!CONVENTION.containsKey(lang)) {
                missing.add(lang + ": no Convention in this gate");
            }
            if (!documented.contains(lang)) {
                missing.add(lang + ": no section in docs/i18n/conventions.md");
            }
        }
        assertThat(missing).as("a language that ships without written conventions "
                + "reads however its translator happened to type").isEmpty();
    }

    @Test
    @DisplayName("the ellipsis is one character in every language")
    void ellipsisIsOneCharacter() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if (ELLIPSIS.matcher(v.masked()).find()) {
                wrong.add(v.name());
            }
        }
        assertThat(wrong).as("three periods where the language writes … (U+2026)").isEmpty();
    }

    /**
     * U+02BC MODIFIER LETTER APOSTROPHE. It looks like an apostrophe and is
     * not one: Unicode classes it as a LETTER, so it joins the word it sits
     * inside. A word spelled with it tokenizes as one token including the
     * mark, which is why a search for the word without it does not match —
     * the v2.114.0 class, where a mark the folder preserved was the very
     * character the tokenizer split on.
     */
    private static final Pattern MODIFIER_APOSTROPHE = Pattern.compile("\u02bc");

    @Test
    @DisplayName("an apostrophe is punctuation, never the modifier LETTER U+02BC")
    void apostrophesAreNotModifierLetters() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if (MODIFIER_APOSTROPHE.matcher(v.text()).find()) {
                wrong.add(v.name());
            }
        }
        assertThat(wrong).as("U+02BC where the language writes \u2019 (U+2019): a modifier "
                + "letter is a LETTER, so it joins its word and the word stops matching "
                + "a search for its ordinary spelling").isEmpty();
    }

    @Test
    @DisplayName("quotation marks are the language's own")
    void quotesAreTheLanguagesOwn() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            Convention c = CONVENTION.get(v.lang());
            if (c.straightQuotesAreNative()) {
                continue;
            }
            Matcher m = STRAIGHT_PAIR.matcher(v.masked());
            while (m.find()) {
                if (isProseQuote(m.group(1))) {
                    wrong.add(v.name() + "   (write " + c.open() + "…" + c.close() + ")");
                    break;
                }
            }
        }
        assertThat(wrong).as("a straight \" pair around a name, in a language with its own quotation marks")
                .isEmpty();
    }

    @Test
    @DisplayName("French puts a no-break space before : ; ! ? and inside « »")
    void frenchSpacesDoNotBreak() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if (v.lang().equals("fr") && FR_PLAIN_SPACE.matcher(v.masked()).find()) {
                wrong.add(v.name());
            }
        }
        assertThat(wrong).as("an ordinary space where a line wrap can strand the punctuation").isEmpty();
    }

    @Test
    @DisplayName("Chinese punctuation next to Chinese is full-width")
    void chinesePunctuationIsFullWidth() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if (v.lang().equals("zh") && ZH_ASCII_PUNCT.matcher(v.masked()).find()) {
                wrong.add(v.name());
            }
        }
        assertThat(wrong).as("half-width , : ; ? ! or a spaced quote beside Chinese text").isEmpty();
    }

    @Test
    @DisplayName("an appended mnemonic comes before the ellipsis")
    void appendedMnemonicPrecedesEllipsis() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if (CONVENTION.get(v.lang()).appendedMnemonic()
                    && MNEMONIC_AFTER_ELLIPSIS.matcher(v.text()).find()) {
                wrong.add(v.name());
            }
        }
        assertThat(wrong).as("…(&X) — Chinese, Hindi, Hebrew and Arabic software writes (&X)…").isEmpty();
    }

    /**
     * An ASCII comma, semicolon or question mark attached to a word beside Arabic.
     * A mark standing alone between spaces is a symbol the user types (the search
     * form's {@code ? = any character}), not the sentence's punctuation.
     */
    private static final Pattern AR_ASCII_PUNCT = Pattern.compile(
            "(?<=[\\u0600-\\u06ff])[,;?]|(?<=\\S)[,;?](?=\\s*[\\u0600-\\u06ff])");

    @Test
    @DisplayName("Arabic writes its own comma, semicolon and question mark")
    void arabicPunctuationIsArabic() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if ("ar".equals(v.lang()) && AR_ASCII_PUNCT.matcher(v.masked()).find()) {
                wrong.add(v.name());
            }
        }
        assertThat(wrong).as("`,` `;` `?` beside Arabic where the language writes ، ؛ ؟").isEmpty();
    }

    /** A Latin word or digit, whitespace, then a keyboard chord — with no RLM between. */
    private static final Pattern RTL_CHORD_AFTER_LATIN = Pattern.compile("[A-Za-z0-9)\\]]\\s+[⌘⌥⇧⌃]");
    /** A value that opens with a neutral mark glued to a Latin run: `.well-known`, `/api`. */
    private static final Pattern RTL_NEUTRAL_OPENS_LATIN = Pattern.compile("^\\s*[.\\/\\-_~#@]+[A-Za-z]");
    /** A dotfile name after a right-to-left word, no LRM before its dot: `في ملف .env`. */
    private static final Pattern RTL_DOTFILE_AFTER_RTL_WORD =
            // 3.2.0 widened it from a dotfile's dot to any path beginning with a
            // neutral (`~/`, `./`, `../`), the rule the documents' gate
            // (RtlDocsPathDirectionGateTest) measured: each detaches the same way
            Pattern.compile("[\\u0590-\\u05ff\\u0600-\\u06ff][\\s«\"(]+(\\.[A-Za-z]|~/|\\.{1,2}/)");
    /** A keyboard chord glyph anywhere in the value. */
    private static final Pattern CHORD = Pattern.compile("[⌘⌥⇧⌃]");

    /**
     * Whether Swing will lay this text out through bidi at all. It does only
     * for text its font utilities call complex; measured on JDK 25, a Hebrew
     * letter or an RLE..PDF pair qualifies and an RLM or RLI..PDI does not.
     */
    static boolean swingLaysOutBidi(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u202a' && c <= '\u202e') {
                return true;
            }
            byte d = Character.getDirectionality(c);
            if (c != '\u200f' && (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("right to left, a Latin run keeps its place beside a chord and its opening mark")
    void rightToLeftRunsKeepTheirOrder() throws IOException {
        // The first Hebrew walk read `API ⌥⌘8–אולפן ה` on the Welcome: a
        // Latin name and the chord after it are one left-to-right run, so the
        // chord landed inside the name. And `.well-known/security.txt` lost
        // its dot to the far end of the line — a neutral mark opening a
        // right-to-left label takes the label's direction. An RLM after the
        // name and an LRM before the dot are the fixes; this is the law.
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            if (!org.nmox.studio.core.util.TextDirection.isRightToLeft(
                    java.util.Locale.forLanguageTag(v.lang()))) {
                continue;
            }
            String t = v.masked();
            if (RTL_CHORD_AFTER_LATIN.matcher(t).find()) {
                wrong.add(v.name() + "   (RLM U+200F after the Latin run, before the chord)");
            }
            if (RTL_NEUTRAL_OPENS_LATIN.matcher(t).find()) {
                wrong.add(v.name() + "   (LRM U+200E before the opening mark)");
            }
            // The same dot in mid-sentence: after a right-to-left word the dot
            // of `.env` takes the sentence's direction and is drawn after the
            // name (the Arabic walk). An LRM before the dot keeps it with `env`.
            if (RTL_DOTFILE_AFTER_RTL_WORD.matcher(t).find()) {
                wrong.add(v.name() + "   (LRM U+200E before the path's leading neutral)");
            }
            // The second walk found no mark at all could move `IRC  ⌥⌘3`: Swing
            // runs bidi only over text it calls complex — a right-to-left
            // letter or an embedding mark (U+202A..U+202E) — and RLM/LRM are
            // neither, so a chord value with no Hebrew or Arabic letter is
            // drawn in logical order whatever marks it carries.
            if (CHORD.matcher(v.text()).find() && !swingLaysOutBidi(v.text())) {
                wrong.add(v.name() + "   (wrap in RLE U+202B … PDF U+202C: no right-to-left letter, so Swing draws it without bidi)");
            }
        }
        assertThat(wrong).as("a right-to-left value whose Latin run the bidi algorithm will reorder").isEmpty();
    }

    @Test
    @DisplayName("one form of address per language")
    void oneRegisterPerLanguage() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Value v : values()) {
            Pattern p = CONVENTION.get(v.lang()).wrongRegister();
            if (p == null) {
                continue;
            }
            Matcher m = p.matcher(v.masked());
            if (m.find()) {
                wrong.add(v.name() + "   (\"" + m.group(1) + "\")");
            }
        }
        assertThat(wrong).as("the reader addressed in the register conventions.md does not use for this "
                + "language — two registers in one product read as two translators").isEmpty();
    }

    /**
     * A straight pair counts as prose when it wraps a placeholder alone or a
     * plain phrase. It does not count when it wraps a wildcard or a lone
     * symbol ({@code "?"}), code ({@code =} {@code ;} {@code \} {@code *}), or
     * anything that was markup.
     */
    static boolean isProseQuote(String inner) {
        if (!inner.isEmpty() && inner.chars().allMatch(ch -> ch == PH.charAt(0))) {
            return true;
        }
        return !inner.matches("(?U)[\\W\\d_]{1,3}")
                && !inner.matches(".*[=;\\\\*].*")
                && !inner.startsWith(" ") && !inner.endsWith(" ")
                && !inner.contains(CODE);
    }

    private static List<String> shippedLanguages() {
        List<String> langs = new ArrayList<>();
        for (UiLocale.Choice c : UiLocale.SUPPORTED) {
            if (!c.code().isEmpty() && !c.code().equals("en")) {
                langs.add(c.code());
            }
        }
        return langs;
    }

    private static List<Value> cache;

    private static synchronized List<Value> values() throws IOException {
        if (cache != null) {
            return cache;
        }
        List<String> langs = shippedLanguages();
        Pattern bundle = Pattern.compile("(?:^|/)Bundle(?:_nmoxstudio)?_([a-z]{2})\\.properties$");
        List<Value> out = new ArrayList<>();
        Map<String, Integer> perLanguage = new TreeMap<>();
        for (Path jar : jars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    Matcher m = bundle.matcher(e.getName());
                    if (!m.find() || !langs.contains(m.group(1))) {
                        continue;
                    }
                    Properties p = new Properties();
                    try (InputStream in = zip.getInputStream(e)) {
                        // product bundles ship raw UTF-8, overlays \\u-escape; a
                        // UTF-8 Reader reads both (the v2.129.0 decoding defect)
                        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    }
                    String where = jar.getFileName() + "!" + e.getName();
                    for (String key : p.stringPropertyNames()) {
                        if (SKIP_KEY.matcher(key).find()) {
                            continue;
                        }
                        out.add(new Value(m.group(1), where, key, p.getProperty(key)));
                        perLanguage.merge(m.group(1), 1, Integer::sum);
                    }
                }
            }
        }
        for (String lang : langs) {
            assertThat(perLanguage.getOrDefault(lang, 0))
                    .as("values read for %s from the assembled cluster — a gate that reads nothing "
                            + "finds every language perfectly typeset", lang)
                    .isGreaterThan(500);
        }
        cache = out;
        return out;
    }

    private static List<Path> jars() throws IOException {
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> s = Files.list(MODULES)) {
            s.filter(p -> p.getFileName().toString().startsWith("org-nmox-NMOX-Studio")
                    && p.getFileName().toString().endsWith(".jar")).sorted().forEach(jars::add);
        }
        try (Stream<Path> s = Files.list(MODULES.resolve("locale"))) {
            s.filter(p -> p.getFileName().toString().contains("_nmoxstudio_")
                    && p.getFileName().toString().endsWith(".jar")).sorted().forEach(jars::add);
        }
        return jars;
    }
}
