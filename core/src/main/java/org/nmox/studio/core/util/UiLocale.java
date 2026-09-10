package org.nmox.studio.core.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The language the IDE's own chrome speaks — menus, dialogs, tooltips,
 * status lines, the Welcome, Options. Chosen in Options ▸ General and
 * remembered where the LAUNCHER reads it: the per-user
 * {@code <userdir>/etc/nmoxstudio.conf}, which the launcher script sources
 * after the install's own conf, so a {@code --locale} appended to
 * {@code default_options} reaches the platform's {@code CLIOptions} on the
 * next start. A locale is a boot-time property (bundles are cached per
 * default locale), so the switch takes effect after a restart — said out
 * loud, never pretended.
 *
 * <p><b>Shell safety is the law here.</b> The conf is SOURCED by a shell,
 * so this class writes only codes matching {@link #CODE} (letters and one
 * optional country) inside a marker block it owns, and never a string a
 * user typed. Anything else in the file is preserved byte for byte.
 *
 * <p>Pure: no disk, no prefs. The Options panel reads and writes the file.
 */
public final class UiLocale {

    /** One offered language: the launcher code and its own name for itself. */
    public record Choice(String code, String nativeName) {

        /** True for the "follow the operating system" row (an empty code). */
        public boolean isSystem() {
            return code.isEmpty();
        }
    }

    /** The system-default row: no {@code --locale}, the JVM's own choice. */
    public static final Choice SYSTEM = new Choice("", "System default");

    /**
     * The languages the product ships bundles for: English, Spanish, French,
     * German, Russian, Hindi (v2.97.0), Ukrainian (v2.98.0), and Polish,
     * Portuguese, Indonesian, Filipino, Vietnamese and Simplified Chinese
     * (v2.99.0, the offshore-market tranche) — each named in itself so a user
     * who landed in the wrong language can still find their own. Ukrainian
     * sits beside Russian rather than inside it: the two are separate
     * languages with separate bundles, and a Ukrainian speaker is never served
     * Russian by fallback (an absent key falls back to ENGLISH, the bundle's
     * own parent).
     *
     * <p>Two are written for a market rather than a country: {@code pt} is
     * Brazilian Portuguese (named so in the menu) because Brazil is the market
     * this serves, and {@code zh} is Simplified Chinese. Neither carries a
     * country code — a plain language bundle is what every regional variant
     * falls back to, so pt_PT and zh_SG get a usable IDE instead of English.
     * Add a country bundle the day the variants must diverge, not before.
     */
    public static final List<Choice> SUPPORTED = List.of(
            SYSTEM,
            new Choice("en", "English"),
            new Choice("es", "Español"),
            new Choice("fr", "Français"),
            new Choice("de", "Deutsch"),
            new Choice("ru", "Русский"),
            new Choice("uk", "Українська"),
            new Choice("pl", "Polski"),
            new Choice("pt", "Português (Brasil)"),
            new Choice("id", "Bahasa Indonesia"),
            new Choice("tl", "Filipino"),
            new Choice("vi", "Tiếng Việt"),
            new Choice("zh", "简体中文"),
            new Choice("hi", "हिन्दी"));

    /** A launcher locale code: {@code fr} or {@code fr:CA}. Nothing else is ever written. */
    static final Pattern CODE = Pattern.compile("[a-z]{2}(?::[A-Z]{2})?");
    static final String BEGIN = "# nmox-locale-begin (Options > General > Language; edit there)";
    static final String END = "# nmox-locale-end";
    private static final Pattern BLOCK = Pattern.compile(
            "(?ms)^" + Pattern.quote(BEGIN) + "\\n.*?^" + Pattern.quote(END) + "\\n?");
    private static final Pattern LOCALE_LINE = Pattern.compile(
            "--locale (" + CODE.pattern() + ")");

    private UiLocale() {
    }

    /** The per-user conf the launcher sources: {@code <userdir>/etc/nmoxstudio.conf}. */
    public static Path userConf(Path userdir) {
        return userdir.resolve("etc").resolve("nmoxstudio.conf");
    }

    /** The block this class owns for a code; empty for the system default. */
    static String block(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("not a launcher locale code: " + code);
        }
        return BEGIN + "\n"
                + "default_options=\"${default_options} --locale " + code + "\"\n"
                + END + "\n";
    }

    /**
     * The conf with the language block replaced (or removed for the system
     * default). Every other line survives untouched; a file with no block
     * gains one at the end; a code that is not a launcher locale code is
     * refused before anything is built.
     */
    public static String apply(String existingConf, String code) {
        String base = existingConf == null ? "" : existingConf;
        String replacement = block(code);
        Matcher m = BLOCK.matcher(base);
        if (m.find()) {
            return base.substring(0, m.start()) + replacement + base.substring(m.end());
        }
        if (replacement.isEmpty()) {
            return base;
        }
        if (!base.isEmpty() && !base.endsWith("\n")) {
            base += "\n";
        }
        return base + replacement;
    }

    /**
     * The JVM's own language at startup — what the "System default" row means.
     * Captured once, before anything can change it, so that row can be
     * returned to rather than approximated.
     */
    private static final Locale STARTED_AS = Locale.getDefault();

    /** Listeners told, on the caller's thread, that the language just changed. */
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    /**
     * Switch the running IDE's language, without a restart.
     *
     * <p>Measured before it was built: {@code ResourceBundle.getBundle} keys
     * its cache on the CURRENT default locale, so moving the default is
     * enough — every later lookup resolves against the new language with no
     * cache to clear. What does not follow by itself is text a component has
     * already painted, which is what the listeners are for: each surface
     * re-reads its own names.
     *
     * <p>The platform's own menus and toolbars are built once at startup from
     * the layer and keep their language until a restart. The conf is still
     * written, so the choice survives one either way.
     */
    public static void applyLive(String code) {
        Locale.setDefault(toLocale(code));
        for (Runnable l : LISTENERS) {
            l.run();
        }
    }

    /** Told after {@link #applyLive}, so a surface can re-read its own names. */
    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** Symmetric with {@link #addListener} — a surface that closes stops listening. */
    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /** The code the conf currently pins, if its block is present and well-formed. */
    public static Optional<String> current(String existingConf) {
        if (existingConf == null) {
            return Optional.empty();
        }
        Matcher m = BLOCK.matcher(existingConf);
        if (!m.find()) {
            return Optional.empty();
        }
        Matcher l = LOCALE_LINE.matcher(m.group());
        return l.find() ? Optional.of(l.group(1)) : Optional.empty();
    }

    /**
     * The user guide, in the language the IDE is currently speaking.
     *
     * <p>A reader who set the IDE to Ukrainian and then clicked "User Guide"
     * used to land on the English manual — the half of internationalization
     * that lives outside the bundles. The translated guides are named after
     * the same codes this class offers, and a build law
     * ({@code TranslatedGuideGateTest}) keeps one on disk for every language
     * here, so the name can be derived rather than looked up.
     *
     * <p>Falls back to the English guide for any language we do not ship,
     * which is also what a country variant of a language we do ship gets when
     * only the plain language bundle exists.
     *
     * @return a repository-relative path, always an existing document
     */
    public static String guideDoc(Locale locale) {
        String lang = locale == null ? "" : locale.getLanguage();
        for (Choice c : SUPPORTED) {
            if (!c.isSystem() && !"en".equals(c.code()) && c.code().equals(lang)) {
                return "docs/user-guide." + c.code() + ".md";
            }
        }
        return "docs/user-guide.md";
    }

    /** {@link #guideDoc(Locale)} for the language the IDE is speaking right now. */
    public static String guideDoc() {
        return guideDoc(Locale.getDefault());
    }

    /** The offered row for a code (the language part matches; a country is ignored). */
    public static Choice choiceFor(String code) {
        if (code == null || code.isEmpty()) {
            return SYSTEM;
        }
        String lang = code.contains(":") ? code.substring(0, code.indexOf(':')) : code;
        for (Choice c : SUPPORTED) {
            if (c.code().equals(lang)) {
                return c;
            }
        }
        return SYSTEM;
    }

    /**
     * The JVM locale a code selects.
     *
     * <p>The system row means the language the JVM STARTED in, not whatever is
     * current. Before v2.103.0 those were the same thing and this read
     * {@code Locale.getDefault()}; live switching moves the default, so that
     * reading would have made "System default" mean "whatever I last picked" —
     * a row that could never take you home.
     */
    public static Locale toLocale(String code) {
        Choice c = choiceFor(code);
        return c.isSystem() ? STARTED_AS : Locale.forLanguageTag(c.code());
    }
}
