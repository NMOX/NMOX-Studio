package org.nmox.studio.core.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
     * German, Russian, Hindi (v2.97.0) and Ukrainian (v2.98.0) — each named in
     * itself so a user who landed in the wrong language can still find their
     * own. Ukrainian sits beside Russian rather than inside it: the two are
     * separate languages with separate bundles, and a Ukrainian speaker is
     * never served Russian by fallback (an absent key falls back to ENGLISH,
     * the bundle's own parent).
     */
    public static final List<Choice> SUPPORTED = List.of(
            SYSTEM,
            new Choice("en", "English"),
            new Choice("es", "Español"),
            new Choice("fr", "Français"),
            new Choice("de", "Deutsch"),
            new Choice("ru", "Русский"),
            new Choice("uk", "Українська"),
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

    /** The JVM locale a code selects, for tests and previews. */
    public static Locale toLocale(String code) {
        Choice c = choiceFor(code);
        return c.isSystem() ? Locale.getDefault() : Locale.forLanguageTag(c.code());
    }
}
