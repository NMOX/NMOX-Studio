package org.nmox.studio.editor.i18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A web project's translation catalogs as data (v2.177.0). The editor
 * completes the project's stylesheet classes and env keys; this core
 * reads the project's catalogs so the same editor can complete a
 * translation key, jump to where it is declared, and report what the
 * translations disagree about. Pure over {@link Path} and
 * {@link String}: no Swing, no EDT, no bundle, no spawn — a catalog is
 * read, never executed, so no Workspace Trust question arises.
 *
 * <p>Detection is a fixed order of rules, first match wins, each pure
 * over the file tree: Angular ({@code angular.json} with an
 * {@code i18n} block or {@code src/locale/messages*.xlf}), Lingui
 * ({@code lingui.config.*} or a {@code lingui} key in package.json),
 * inlang/Paraglide ({@code project.inlang/settings.json}), i18next,
 * vue-i18n, svelte-i18n and react-intl by dependency, and last the I18n
 * Kit's own shape ({@code locales/*.json} beside an {@code i18n.js}
 * that applies {@code data-i18n}). The source locale is {@code en}
 * when a catalog is named so, else the one the framework's config
 * names, else the largest catalog — and the record says which rule
 * chose it, because a report that compares everything against the
 * wrong source is wrong everywhere at once.
 *
 * <p>Formats read: nested JSON, flat JSON, gettext {@code .po}, XLIFF
 * 1.2 and 2.0. YAML catalogs are refused honestly (a vue-i18n project
 * whose catalogs are {@code .yaml} gets ONE finding on its package.json
 * line and nothing guessed). Every read is bounded: at most
 * {@value #MAX_CATALOGS} catalog files, each at most
 * {@link org.nmox.studio.editor.fullstack.BoundedWalk#MAX_FILE_BYTES};
 * a census that stopped short says so through {@code censusComplete}.
 */
public final class I18nCatalogs {

    private I18nCatalogs() {
    }

    /** The catalog family, which decides the parser and the lookup shapes. */
    public enum Format {
        /** i18next: {@code public/locales/<lng>/<ns>.json}, nested, namespaces per file. */
        I18NEXT_NESTED,
        /** vue-i18n: {@code src/locales/<lng>.json}, nested, {@code a | b} plurals. */
        VUE_NESTED,
        /** Angular: {@code src/locale/messages.xlf} + {@code messages.<lng>.xlf}. */
        ANGULAR_XLIFF,
        /** Lingui: {@code <catalog path>/<lng>/messages.po}, keys are source text. */
        LINGUI_PO,
        /** inlang/Paraglide: {@code messages/<lng>.json}, flat, {@code $schema} key. */
        PARAGLIDE_FLAT,
        /** svelte-i18n: nested JSON holding ICU values. */
        NESTED_ICU,
        /** react-intl / FormatJS: flat {@code id → ICU string}. */
        FLAT_ICU,
        /** The I18n Kit's {@code locales/<tag>.json}, flat dot-named keys. */
        KIT_FLAT
    }

    /**
     * One catalog value: the text, the 1-based line it sits on (1 when
     * the line could not be found — never invented), and a translation
     * state for the formats that carry one ({@code new} / {@code initial}
     * in XLIFF, {@code untranslated} for an empty PO msgstr; null for
     * JSON, whose only states are present and absent).
     */
    public record Entry(String value, int line, String state) {

        /** True when the format itself says this value is not translated yet. */
        public boolean untranslated() {
            return state != null && !state.isEmpty();
        }
    }

    /**
     * One catalog file: its locale tag as the file or directory names it,
     * its namespace (i18next's per-file key space — the file's base name;
     * empty for every other format), and its flattened keys.
     */
    public record Catalog(String locale, Path file, String namespace,
            Map<String, Entry> entries) {
    }

    /**
     * Everything detection found: the format, the source locale and the
     * RULE that chose it, every catalog, whether the census was complete,
     * and — when YAML catalogs were met — the file and line that carries
     * the honest refusal. {@code catalogHome} names the directory the
     * catalogs live in, relative to the root, for messages.
     */
    public record Catalogs(Format format, Path root, String catalogHome,
            String sourceLocale, String sourceRule, List<Catalog> catalogs,
            boolean censusComplete, Path yamlRefusal, int yamlRefusalLine) {

        /** The source locale's catalogs (one per namespace). */
        public List<Catalog> source() {
            return ofLocale(sourceLocale);
        }

        /** The catalogs of one locale. */
        public List<Catalog> ofLocale(String locale) {
            List<Catalog> out = new ArrayList<>();
            for (Catalog c : catalogs) {
                if (c.locale().equals(locale)) {
                    out.add(c);
                }
            }
            return out;
        }

        /** Every locale tag seen, in catalog order. */
        public Set<String> locales() {
            Set<String> out = new LinkedHashSet<>();
            for (Catalog c : catalogs) {
                out.add(c.locale());
            }
            return out;
        }
    }

    /**
     * A catalog that could not be read: the file and the parser's reason.
     * The action publishes NOTHING on this (the slither rule — a crash is
     * not an all-clear) and puts the reason on the status line.
     */
    public static final class ParseFailure extends Exception {

        private final transient Path file;

        public ParseFailure(Path file, String message) {
            super(message);
            this.file = file;
        }

        public Path file() {
            return file;
        }
    }

    /** At most this many catalog files are read; more marks the census partial. */
    public static final int MAX_CATALOGS = 200;

    static final long MAX_FILE_BYTES =
            org.nmox.studio.editor.fullstack.BoundedWalk.MAX_FILE_BYTES;

    // ---- detection --------------------------------------------------------

    /**
     * The project's catalogs, or null when no rule matches. Throws a
     * {@link ParseFailure} when ANY catalog cannot be read: a report
     * comparing a complete source against a half-parsed target would
     * accuse every key past the syntax error.
     */
    public static Catalogs detect(Path root) throws ParseFailure {
        if (root == null || !Files.isDirectory(root)) {
            return null;
        }
        String pkg = readSmall(root.resolve("package.json"));
        JSONObject packageJson = null;
        if (pkg != null) {
            try {
                packageJson = new JSONObject(pkg);
            } catch (JSONException malformed) {
                packageJson = null;   // a broken manifest is not a catalog problem
            }
        }
        Catalogs found = angular(root);
        if (found != null) {
            return found;
        }
        found = lingui(root, packageJson);
        if (found != null) {
            return found;
        }
        found = inlang(root);
        if (found != null) {
            return found;
        }
        if (hasDependency(packageJson, "i18next")) {
            found = i18next(root);
            if (found != null) {
                return found;
            }
        }
        if (hasDependency(packageJson, "vue-i18n")) {
            found = vueI18n(root, pkg);
            if (found != null) {
                return found;
            }
        }
        if (hasDependency(packageJson, "svelte-i18n")) {
            found = nestedByDirs(root, Format.NESTED_ICU,
                    List.of("src/lib/i18n/locales", "src/i18n", "src/locales", "src/lib/i18n"));
            if (found != null) {
                return found;
            }
        }
        if (hasDependency(packageJson, "react-intl")
                || hasDependencyPrefix(packageJson, "@formatjs/")) {
            found = flatByDirs(root, Format.FLAT_ICU,
                    List.of("src/lang", "lang", "src/locales", "src/i18n"));
            if (found != null) {
                return found;
            }
        }
        return kit(root);
    }

    /** True when package.json names the dependency (either section). */
    static boolean hasDependency(JSONObject packageJson, String name) {
        if (packageJson == null) {
            return false;
        }
        for (String section : List.of("dependencies", "devDependencies")) {
            JSONObject deps = packageJson.optJSONObject(section);
            if (deps != null && deps.has(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDependencyPrefix(JSONObject packageJson, String prefix) {
        if (packageJson == null) {
            return false;
        }
        for (String section : List.of("dependencies", "devDependencies")) {
            JSONObject deps = packageJson.optJSONObject(section);
            if (deps != null) {
                for (String k : deps.keySet()) {
                    if (k.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Angular -------------------------------------------------------------

    private static final Pattern MESSAGES_TARGET =
            Pattern.compile("messages\\.([A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*)\\.xlf");

    private static Catalogs angular(Path root) throws ParseFailure {
        String angularJson = readSmall(root.resolve("angular.json"));
        Path localeDir = root.resolve("src").resolve("locale");
        Path extract = localeDir.resolve("messages.xlf");
        if (angularJson == null && !Files.isRegularFile(extract)) {
            return null;
        }
        String configSource = null;
        boolean i18nBlock = false;
        if (angularJson != null) {
            try {
                JSONObject cfg = new JSONObject(angularJson);
                JSONObject projects = cfg.optJSONObject("projects");
                if (projects != null) {
                    for (String name : projects.keySet()) {
                        JSONObject p = projects.optJSONObject(name);
                        JSONObject i18n = p == null ? null : p.optJSONObject("i18n");
                        if (i18n != null) {
                            i18nBlock = true;
                            Object src = i18n.opt("sourceLocale");
                            if (src instanceof String s) {
                                configSource = s;
                            } else if (src instanceof JSONObject o && o.opt("code") instanceof String s) {
                                configSource = s;
                            }
                        }
                    }
                }
            } catch (JSONException malformed) {
                i18nBlock = false;
            }
        }
        if (!i18nBlock && !Files.isRegularFile(extract)) {
            return null;
        }
        if (!Files.isRegularFile(extract)) {
            // an i18n block with no extract yet: nothing to compare against
            return new Catalogs(Format.ANGULAR_XLIFF, root, "src/locale/",
                    configSource == null ? "en" : configSource, "angular.json sourceLocale",
                    List.of(), true, null, 0);
        }
        String sourceLocale = configSource == null ? "en" : configSource;
        String rule = configSource == null ? "en assumed (no sourceLocale in angular.json)"
                : "angular.json sourceLocale";
        List<Catalog> catalogs = new ArrayList<>();
        catalogs.add(new Catalog(sourceLocale, extract, "", xliff(extract, true)));
        List<Path> targets = listFiles(localeDir, MAX_CATALOGS);
        boolean complete = targets.size() < MAX_CATALOGS;
        for (Path f : targets) {
            Matcher m = MESSAGES_TARGET.matcher(f.getFileName().toString());
            if (m.matches() && !f.equals(extract)) {
                catalogs.add(new Catalog(m.group(1), f, "", xliff(f, false)));
            }
        }
        return new Catalogs(Format.ANGULAR_XLIFF, root, "src/locale/", sourceLocale, rule,
                List.copyOf(catalogs), complete, null, 0);
    }

    // Lingui --------------------------------------------------------------

    private static Catalogs lingui(Path root, JSONObject packageJson) throws ParseFailure {
        JSONObject config = null;
        boolean isLingui = false;
        String json = readSmall(root.resolve("lingui.config.json"));
        if (json != null) {
            isLingui = true;
            try {
                config = new JSONObject(json);
            } catch (JSONException malformed) {
                config = null;
            }
        } else if (Files.isRegularFile(root.resolve("lingui.config.js"))
                || Files.isRegularFile(root.resolve("lingui.config.ts"))
                || Files.isRegularFile(root.resolve("lingui.config.mjs"))) {
            isLingui = true;
        } else if (packageJson != null && packageJson.optJSONObject("lingui") != null) {
            isLingui = true;
            config = packageJson.optJSONObject("lingui");
        }
        if (!isLingui) {
            return null;
        }
        String pathPattern = null;
        String configSource = null;
        if (config != null) {
            configSource = config.optString("sourceLocale", null);
            JSONArray cats = config.optJSONArray("catalogs");
            if (cats != null && cats.length() > 0 && cats.optJSONObject(0) != null) {
                pathPattern = cats.optJSONObject(0).optString("path", null);
            }
        }
        // a JS config cannot be read without running it; the default layout
        // is assumed and the rule says so
        String home = pathPattern == null ? "src/locales/{locale}/messages" : pathPattern;
        home = home.startsWith("<rootDir>/") ? home.substring("<rootDir>/".length()) : home;
        home = home.startsWith("./") ? home.substring(2) : home;
        int marker = home.indexOf("{locale}");
        if (marker < 0) {
            return null;
        }
        String before = home.substring(0, marker);
        String after = home.substring(marker + "{locale}".length());
        Path base = root.resolve(before.endsWith("/") ? before.substring(0, before.length() - 1) : before);
        if (!Files.isDirectory(base)) {
            return null;
        }
        List<Catalog> catalogs = new ArrayList<>();
        List<Path> dirs = listFiles(base, MAX_CATALOGS);
        boolean complete = dirs.size() < MAX_CATALOGS;
        for (Path localeDir : dirs) {
            String tag = localeDir.getFileName().toString();
            if (!Files.isDirectory(localeDir) || !isLocaleTag(tag)) {
                continue;
            }
            Path po = base.resolve(tag + after + ".po");
            if (after.isEmpty()) {
                po = base.resolve(tag).resolve("messages.po");
            }
            if (Files.isRegularFile(po)) {
                catalogs.add(new Catalog(tag, po, "", po(po)));
            }
        }
        if (catalogs.isEmpty()) {
            return null;
        }
        String[] chosen = chooseSource(catalogs, configSource,
                pathPattern == null && config == null ? "lingui default layout, "
                        : "", "lingui sourceLocale");
        return new Catalogs(Format.LINGUI_PO, root, before, chosen[0], chosen[1],
                List.copyOf(catalogs), complete, null, 0);
    }

    // inlang / Paraglide --------------------------------------------------

    private static Catalogs inlang(Path root) throws ParseFailure {
        Path settings = root.resolve("project.inlang").resolve("settings.json");
        String text = readSmall(settings);
        if (text == null) {
            return null;
        }
        String pathPattern = null;
        String configSource = null;
        try {
            JSONObject cfg = new JSONObject(text);
            configSource = cfg.optString("sourceLanguageTag", null);
            if (configSource == null) {
                configSource = cfg.optString("baseLocale", null);
            }
            JSONObject plugin = cfg.optJSONObject("plugin.inlang.messageFormat");
            if (plugin != null) {
                pathPattern = plugin.optString("pathPattern", null);
            }
            if (pathPattern == null) {
                pathPattern = cfg.optString("pathPattern", null);
            }
        } catch (JSONException malformed) {
            throw new ParseFailure(settings, malformed.getMessage());
        }
        if (pathPattern == null) {
            pathPattern = "./messages/{languageTag}.json";
        }
        pathPattern = pathPattern.startsWith("./") ? pathPattern.substring(2) : pathPattern;
        String marker = pathPattern.contains("{languageTag}") ? "{languageTag}" : "{locale}";
        int at = pathPattern.indexOf(marker);
        if (at < 0) {
            return null;
        }
        String before = pathPattern.substring(0, at);
        String after = pathPattern.substring(at + marker.length());
        Path dir = root.resolve(before.endsWith("/") ? before.substring(0, before.length() - 1) : before);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Catalog> catalogs = new ArrayList<>();
        List<Path> files = listFiles(dir, MAX_CATALOGS);
        boolean complete = files.size() < MAX_CATALOGS;
        for (Path f : files) {
            String name = f.getFileName().toString();
            if (!name.endsWith(after) || !Files.isRegularFile(f)) {
                continue;
            }
            String tag = name.substring(0, name.length() - after.length());
            if (isLocaleTag(tag)) {
                catalogs.add(new Catalog(tag, f, "", json(f, false)));
            }
        }
        if (catalogs.isEmpty()) {
            return null;
        }
        String[] chosen = chooseSource(catalogs, configSource, "", "inlang sourceLanguageTag");
        return new Catalogs(Format.PARAGLIDE_FLAT, root, before, chosen[0], chosen[1],
                List.copyOf(catalogs), complete, null, 0);
    }

    // i18next -------------------------------------------------------------

    private static final Pattern FALLBACK_LNG =
            Pattern.compile("fallbackLng\\s*:\\s*['\"]([A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*)['\"]");
    private static final Pattern VUE_LOCALE =
            Pattern.compile("createI18n\\s*\\(\\s*\\{[^}]*?\\blocale\\s*:\\s*['\"]([A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*)['\"]",
                    Pattern.DOTALL);

    private static Catalogs i18next(Path root) throws ParseFailure {
        for (String home : List.of("public/locales", "src/locales", "locales", "src/i18n/locales", "src/i18n")) {
            Path dir = root.resolve(home);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Catalog> catalogs = new ArrayList<>();
            List<Path> children = listFiles(dir, MAX_CATALOGS);
            boolean complete = children.size() < MAX_CATALOGS;
            for (Path child : children) {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child) && isLocaleTag(name)) {
                    // <lng>/<ns>.json — one key space per FILE
                    List<Path> nsFiles = listFiles(child, MAX_CATALOGS - catalogs.size());
                    complete &= nsFiles.size() < MAX_CATALOGS - catalogs.size();
                    for (Path ns : nsFiles) {
                        String n = ns.getFileName().toString();
                        if (n.endsWith(".json") && Files.isRegularFile(ns)) {
                            catalogs.add(new Catalog(name, ns,
                                    n.substring(0, n.length() - 5), json(ns, true)));
                        }
                    }
                } else if (name.endsWith(".json") && Files.isRegularFile(child)
                        && isLocaleTag(name.substring(0, name.length() - 5))) {
                    catalogs.add(new Catalog(name.substring(0, name.length() - 5), child,
                            "", json(child, true)));
                }
            }
            if (catalogs.isEmpty()) {
                continue;
            }
            String config = null;
            if (!hasLocale(catalogs, "en")) {
                config = firstMatch(root, FALLBACK_LNG);
            }
            String[] chosen = chooseSource(catalogs, config, "", "i18next fallbackLng");
            return new Catalogs(Format.I18NEXT_NESTED, root, home + "/", chosen[0], chosen[1],
                    List.copyOf(catalogs), complete, null, 0);
        }
        return null;
    }

    // vue-i18n ------------------------------------------------------------

    private static Catalogs vueI18n(Path root, String packageJsonText) throws ParseFailure {
        for (String home : List.of("src/locales", "src/i18n/locales", "src/i18n", "locales")) {
            Path dir = root.resolve(home);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            boolean yaml = false;
            boolean any = false;
            for (Path f : listFiles(dir, MAX_CATALOGS)) {
                String name = f.getFileName().toString();
                if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                    yaml = true;
                    any = true;
                } else if (name.endsWith(".json")) {
                    any = true;
                }
            }
            if (!any) {
                continue;
            }
            if (yaml) {
                // the honest refusal: nothing is guessed about a project whose
                // catalogs this reader cannot read
                Path pkg = root.resolve("package.json");
                return new Catalogs(Format.VUE_NESTED, root, home + "/", "en",
                        "refused: YAML catalogs", List.of(), true, pkg,
                        lineOf(packageJsonText, "\"vue-i18n\""));
            }
            Catalogs found = nestedByDirs(root, Format.VUE_NESTED, List.of(home));
            if (found != null) {
                if (!hasLocale(found.catalogs(), "en")) {
                    String config = firstMatch(root, VUE_LOCALE);
                    String[] chosen = chooseSource(found.catalogs(), config, "", "vue-i18n locale option");
                    return new Catalogs(found.format(), root, found.catalogHome(), chosen[0], chosen[1],
                            found.catalogs(), found.censusComplete(), null, 0);
                }
                return found;
            }
        }
        return null;
    }

    // nested / flat JSON by directory --------------------------------------

    private static Catalogs nestedByDirs(Path root, Format format, List<String> homes) throws ParseFailure {
        return byDirs(root, format, homes, true);
    }

    private static Catalogs flatByDirs(Path root, Format format, List<String> homes) throws ParseFailure {
        return byDirs(root, format, homes, false);
    }

    private static Catalogs byDirs(Path root, Format format, List<String> homes, boolean nested)
            throws ParseFailure {
        for (String home : homes) {
            Path dir = root.resolve(home);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Catalog> catalogs = new ArrayList<>();
            List<Path> files = listFiles(dir, MAX_CATALOGS);
            boolean complete = files.size() < MAX_CATALOGS;
            for (Path f : files) {
                String name = f.getFileName().toString();
                if (name.endsWith(".json") && Files.isRegularFile(f)
                        && isLocaleTag(name.substring(0, name.length() - 5))) {
                    catalogs.add(new Catalog(name.substring(0, name.length() - 5), f, "",
                            json(f, nested)));
                }
            }
            if (catalogs.isEmpty()) {
                continue;
            }
            String[] chosen = chooseSource(catalogs, null, "", "");
            return new Catalogs(format, root, home + "/", chosen[0], chosen[1],
                    List.copyOf(catalogs), complete, null, 0);
        }
        return null;
    }

    // the Kit ---------------------------------------------------------------

    private static Catalogs kit(Path root) throws ParseFailure {
        String helper = readSmall(root.resolve("i18n.js"));
        if (helper == null || !helper.contains("data-i18n")) {
            return null;
        }
        return flatByDirs(root, Format.KIT_FLAT, List.of("locales"));
    }

    // ---- the source-locale rule ---------------------------------------------

    /**
     * {sourceLocale, rule}: {@code en} when present (or an {@code en-*}
     * variant), else the locale the config named (matched exactly, then
     * by language), else the largest catalog — each answer naming the
     * rule that produced it.
     */
    static String[] chooseSource(List<Catalog> catalogs, String configLocale,
            String rulePrefix, String configRule) {
        if (hasLocale(catalogs, "en")) {
            return new String[] {"en", rulePrefix + "en present"};
        }
        for (Catalog c : catalogs) {
            if (c.locale().startsWith("en-") || c.locale().startsWith("en_")) {
                return new String[] {c.locale(), rulePrefix + "en variant present"};
            }
        }
        if (configLocale != null) {
            String exact = null;
            String byLanguage = null;
            String lang = language(configLocale);
            for (Catalog c : catalogs) {
                if (c.locale().equals(configLocale)) {
                    exact = c.locale();
                } else if (byLanguage == null && language(c.locale()).equals(lang)) {
                    byLanguage = c.locale();
                }
            }
            if (exact != null) {
                return new String[] {exact, rulePrefix + configRule};
            }
            if (byLanguage != null) {
                return new String[] {byLanguage, rulePrefix + configRule + " (by language)"};
            }
        }
        Map<String, Integer> sizes = new TreeMap<>();
        for (Catalog c : catalogs) {
            sizes.merge(c.locale(), c.entries().size(), Integer::sum);
        }
        String largest = null;
        int most = -1;
        for (Map.Entry<String, Integer> e : sizes.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                largest = e.getKey();
            }
        }
        return new String[] {largest, rulePrefix + "largest catalog"};
    }

    private static boolean hasLocale(List<Catalog> catalogs, String locale) {
        for (Catalog c : catalogs) {
            if (c.locale().equals(locale)) {
                return true;
            }
        }
        return false;
    }

    private static String language(String tag) {
        int cut = tag.indexOf('-');
        int cut2 = tag.indexOf('_');
        if (cut2 >= 0 && (cut < 0 || cut2 < cut)) {
            cut = cut2;
        }
        return (cut < 0 ? tag : tag.substring(0, cut)).toLowerCase(Locale.ROOT);
    }

    private static final Pattern LOCALE_TAG =
            Pattern.compile("[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*");

    /** A BCP-47-shaped tag: {@code en}, {@code en-US}, {@code pt_BR}, {@code zh-Hans-CN}. */
    static boolean isLocaleTag(String s) {
        return LOCALE_TAG.matcher(s).matches();
    }

    // ---- the parsers, cached per file ----------------------------------------

    private record CacheEntry(long mtime, long size, Map<String, Entry> entries) {
    }

    /** One entry per catalog path, replaced when the file changes (the v1.333.0 shape). */
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private interface Parser {
        Map<String, Entry> parse(String text, Path file) throws ParseFailure;
    }

    private static Map<String, Entry> cached(Path file, Parser parser) throws ParseFailure {
        String key = file.toAbsolutePath().toString();
        long mtime;
        long size;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException unreadable) {
            throw new ParseFailure(file, unreadable.getMessage());
        }
        CacheEntry hit = CACHE.get(key);
        if (hit != null && hit.mtime() == mtime && hit.size() == size) {
            return hit.entries();
        }
        if (size > MAX_FILE_BYTES) {
            throw new ParseFailure(file, "larger than " + (MAX_FILE_BYTES / 1024) + " KiB");
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | OutOfMemoryError unreadable) {
            throw new ParseFailure(file, unreadable.getMessage());
        }
        Map<String, Entry> entries = Map.copyOf(parser.parse(text, file));
        CACHE.put(key, new CacheEntry(mtime, size, entries));
        return entries;
    }

    static Map<String, Entry> json(Path file, boolean nested) throws ParseFailure {
        return cached(file, (text, f) -> parseJson(text, f, nested));
    }

    static Map<String, Entry> po(Path file) throws ParseFailure {
        return cached(file, (text, f) -> parsePo(text, f));
    }

    static Map<String, Entry> xliff(Path file, boolean sourceSide) throws ParseFailure {
        return cached(file, (text, f) -> parseXliff(text, f, sourceSide));
    }

    // JSON ------------------------------------------------------------------

    /**
     * A JSON catalog flattened to dot keys. Nested: every object level
     * joins the key with {@code .} and arrays with their index, the
     * i18next / vue-i18n convention. Flat: top-level keys only, a FormatJS
     * {@code {defaultMessage}} object read for its message, other objects
     * and arrays skipped (inlang's variant shapes are not v1's business).
     * {@code $schema} is never a key. Lines come from a scan of the text
     * beside the parse, because org.json keeps no offsets.
     */
    static Map<String, Entry> parseJson(String text, Path file, boolean nested) throws ParseFailure {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException malformed) {
            throw new ParseFailure(file, malformed.getMessage());
        }
        Map<String, Integer> lines = JsonKeyLines.scan(text);
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String key : sortedKeys(root)) {
            if (key.equals("$schema")) {
                continue;
            }
            Object v = root.get(key);
            if (nested) {
                flatten(v, key, lines, out);
            } else if (v instanceof String s) {
                out.put(key, new Entry(s, lineOr1(lines, key), null));
            } else if (v instanceof JSONObject o && o.opt("defaultMessage") instanceof String s) {
                out.put(key, new Entry(s, lineOr1(lines, key), null));
            } else if (v instanceof Number || v instanceof Boolean) {
                out.put(key, new Entry(String.valueOf(v), lineOr1(lines, key), null));
            }
        }
        return out;
    }

    private static void flatten(Object v, String path, Map<String, Integer> lines,
            Map<String, Entry> out) {
        if (v instanceof JSONObject o) {
            for (String key : sortedKeys(o)) {
                flatten(o.get(key), path + "." + key, lines, out);
            }
        } else if (v instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                flatten(a.get(i), path + "." + i, lines, out);
            }
        } else if (v instanceof String s) {
            out.put(path, new Entry(s, lineOr1(lines, path), null));
        } else if (v instanceof Number || v instanceof Boolean) {
            out.put(path, new Entry(String.valueOf(v), lineOr1(lines, path), null));
        }
        // JSONObject.NULL: a null value is no translation and no key
    }

    private static List<String> sortedKeys(JSONObject o) {
        List<String> keys = new ArrayList<>(o.keySet());
        keys.sort(null);   // org.json keeps hash order; a report should not
        return keys;
    }

    private static int lineOr1(Map<String, Integer> lines, String key) {
        Integer line = lines.get(key);
        return line == null ? 1 : line;
    }

    /**
     * The 1-based line of every key in a JSON text by its flattened
     * path, found by walking the nesting with a string-aware scan —
     * org.json loses offsets, and a finding needs a line a click can
     * land on. Never guesses: a key the scan cannot place is simply
     * absent, and the caller reports line 1 with the key named.
     */
    static final class JsonKeyLines {

        private JsonKeyLines() {
        }

        private static final class Frame {
            final boolean object;
            String key;
            int index;
            boolean expectKey;
            boolean valueSeen;

            Frame(boolean object) {
                this.object = object;
                this.expectKey = object;
            }

            String segment() {
                return object ? key : String.valueOf(index);
            }
        }

        static Map<String, Integer> scan(String text) {
            Map<String, Integer> out = new HashMap<>();
            Deque<Frame> stack = new ArrayDeque<>();
            int line = 1;
            int i = 0;
            int n = text.length();
            while (i < n) {
                char c = text.charAt(i);
                if (c == '\n') {
                    line++;
                    i++;
                    continue;
                }
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                Frame top = stack.peek();
                if (c == '"') {
                    int end = stringEnd(text, i);
                    if (top != null && top.object && top.expectKey) {
                        top.key = unescape(text.substring(i + 1, end));
                        top.expectKey = false;
                        out.putIfAbsent(path(stack), line);
                    } else if (top != null && !top.object && !top.valueSeen) {
                        top.valueSeen = true;
                        out.putIfAbsent(path(stack), line);
                    }
                    i = end + 1;
                    continue;
                }
                if (c == '{' || c == '[') {
                    if (top != null && !top.object && !top.valueSeen) {
                        top.valueSeen = true;
                        out.putIfAbsent(path(stack), line);
                    }
                    stack.push(new Frame(c == '{'));
                    i++;
                    continue;
                }
                if (c == '}' || c == ']') {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                    i++;
                    continue;
                }
                if (c == ',') {
                    if (top != null) {
                        if (top.object) {
                            top.expectKey = true;
                            top.key = null;
                        } else {
                            top.index++;
                            top.valueSeen = false;
                        }
                    }
                    i++;
                    continue;
                }
                if (c == ':') {
                    i++;
                    continue;
                }
                // a bare scalar (number, true, false, null)
                if (top != null && !top.object && !top.valueSeen) {
                    top.valueSeen = true;
                    out.putIfAbsent(path(stack), line);
                }
                i++;
            }
            return out;
        }

        private static String path(Deque<Frame> stack) {
            StringBuilder sb = new StringBuilder();
            // the deque iterates top-first; the path reads root-first
            List<Frame> frames = new ArrayList<>(stack);
            for (int k = frames.size() - 1; k >= 0; k--) {
                String seg = frames.get(k).segment();
                if (seg == null) {
                    return "";
                }
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(seg);
            }
            return sb.toString();
        }

        /** Index of the closing quote of the string opening at {@code at}. */
        private static int stringEnd(String s, int at) {
            int i = at + 1;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    return i;
                }
                i++;
            }
            return s.length() - 1;
        }

        private static String unescape(String raw) {
            if (raw.indexOf('\\') < 0) {
                return raw;
            }
            try {
                return new JSONObject("{\"k\":\"" + raw + "\"}").getString("k");
            } catch (JSONException malformed) {
                return raw;
            }
        }
    }

    // PO --------------------------------------------------------------------

    /**
     * A gettext catalog: {@code msgctxt} / {@code msgid} / {@code msgstr}
     * with continuation strings, plural {@code msgstr[0]} taken as the
     * value, obsolete ({@code #~}) entries skipped, the header (empty
     * msgid) skipped. The key is the msgid, prefixed {@code ctxt|} when a
     * context is set — Lingui's explicit ids ride the msgid, so this is
     * the id in both of its modes. An empty msgstr is an entry in state
     * {@code untranslated}, on its own line. A string that never closes its
     * quote is a {@link ParseFailure} naming the line: gettext refuses such a
     * file, and reading past it would key the report on garbage (the
     * 2026-09-17 arc review found {@code msgid "hello} becoming the key
     * {@code "hello}).
     */
    static Map<String, Entry> parsePo(String text, Path file) throws ParseFailure {
        Map<String, Entry> out = new LinkedHashMap<>();
        String ctxt = null;
        String id = null;
        StringBuilder str = null;
        String field = null;
        int idLine = 0;
        int strLine = 0;
        int line = 0;
        boolean obsolete = false;
        for (String raw : text.split("\n", -1)) {
            line++;
            String l = raw.strip();
            if (l.startsWith("#~")) {
                obsolete = true;
                continue;
            }
            if (l.isEmpty()) {
                if (!obsolete && id != null) {
                    flushPo(out, ctxt, id, str, idLine, strLine);
                }
                ctxt = null;
                id = null;
                str = null;
                field = null;
                obsolete = false;
                continue;
            }
            if (l.startsWith("#")) {
                continue;
            }
            if (obsolete) {
                continue;
            }
            if (l.startsWith("msgctxt ")) {
                field = "ctxt";
                ctxt = poStringAt(l.substring(8), file, line);
            } else if (l.startsWith("msgid_plural ")) {
                field = "plural";
            } else if (l.startsWith("msgid ")) {
                field = "id";
                id = poStringAt(l.substring(6), file, line);
                idLine = line;
            } else if (l.startsWith("msgstr[")) {
                int close = l.indexOf(']');
                boolean first = close > 0 && l.startsWith("msgstr[0]");
                field = first ? "str" : "strN";
                if (first) {
                    str = new StringBuilder(poStringAt(l.substring(close + 1).strip(), file, line));
                    strLine = line;
                }
            } else if (l.startsWith("msgstr ")) {
                field = "str";
                str = new StringBuilder(poStringAt(l.substring(7), file, line));
                strLine = line;
            } else if (l.startsWith("\"") && field != null) {
                String piece = poStringAt(l, file, line);
                switch (field) {
                    case "ctxt" -> ctxt = (ctxt == null ? "" : ctxt) + piece;
                    case "id" -> id = (id == null ? "" : id) + piece;
                    case "str" -> {
                        if (str != null) {
                            str.append(piece);
                        }
                    }
                    default -> { }
                }
            }
        }
        if (!obsolete && id != null) {
            flushPo(out, ctxt, id, str, idLine, strLine);
        }
        return out;
    }

    private static void flushPo(Map<String, Entry> out, String ctxt, String id,
            StringBuilder str, int idLine, int strLine) {
        if (id.isEmpty()) {
            return;               // the header
        }
        String key = ctxt == null ? id : ctxt + "|" + id;
        String value = str == null ? "" : str.toString();
        if (value.isEmpty()) {
            out.put(key, new Entry("", strLine > 0 ? strLine : idLine, "untranslated"));
        } else {
            out.put(key, new Entry(value, idLine, null));
        }
    }

    /** {@link #poString} for a line the catalog must have closed: an unterminated quote refuses by line. */
    private static String poStringAt(String quoted, Path file, int line) throws ParseFailure {
        String s = quoted.strip();
        int trailingBackslashes = 0;
        for (int k = s.length() - 2; k >= 1 && s.charAt(k) == '\\'; k--) {
            trailingBackslashes++;
        }
        // an odd run of backslashes before the last quote escapes it: still open
        if (s.length() < 2 || !s.startsWith("\"") || !s.endsWith("\"") || (trailingBackslashes & 1) == 1) {
            throw new ParseFailure(file, "unterminated string at line " + line);
        }
        return poString(s);
    }

    /** The content of one quoted PO string, escapes resolved. */
    static String poString(String quoted) {
        String s = quoted.strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char e = s.charAt(++i);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append('\\').append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // XLIFF -----------------------------------------------------------------

    /**
     * An XLIFF 1.2 ({@code trans-unit}) or 2.0 ({@code unit}/{@code segment})
     * catalog by unit id. The source side reads {@code <source>}; a
     * translation reads {@code <target>}, an absent target or one in state
     * {@code new} / {@code initial} being an entry in that state. Inline
     * elements render as their {@code equiv-text} (Angular puts the
     * {@code {{name}}} interpolation there), else as {@code {id}}, so the
     * argument check sees the placeholders. The DOM factory is hardened:
     * secure processing on, DOCTYPE refused, external entities off — a
     * catalog is data and must never fetch a file.
     */
    static Map<String, Entry> parseXliff(String text, Path file, boolean sourceSide)
            throws ParseFailure {
        Document doc;
        try {
            // the hardening sits in the SAME method as the parse: find-sec-bugs'
            // XXE detector reads one method at a time, and a helper it cannot
            // see into reads as an unhardened parse (the v2.177.0 verify find)
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(text)));
        } catch (Exception malformed) {
            throw new ParseFailure(file, malformed.getMessage());
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        Map<String, Integer> lines = idLines(text);
        Element root = doc.getDocumentElement();
        boolean v2 = "2.0".equals(root.getAttribute("version")) || root.getElementsByTagName("unit").getLength() > 0;
        NodeList units = root.getElementsByTagName(v2 ? "unit" : "trans-unit");
        for (int i = 0; i < units.getLength(); i++) {
            Element unit = (Element) units.item(i);
            String id = unit.getAttribute("id");
            if (id.isEmpty()) {
                continue;
            }
            Element holder = unit;
            String state = null;
            if (v2) {
                Element segment = firstChild(unit, "segment");
                if (segment == null) {
                    continue;
                }
                holder = segment;
                state = segment.getAttribute("state");
            }
            Element source = firstChild(holder, "source");
            Element target = firstChild(holder, "target");
            int line = lines.getOrDefault(id, 1);
            if (sourceSide) {
                if (source != null) {
                    out.put(id, new Entry(inlineText(source), line, null));
                }
                continue;
            }
            if (target == null) {
                out.put(id, new Entry("", line, "missing target"));
                continue;
            }
            if (!v2) {
                state = target.getAttribute("state");
            }
            String value = inlineText(target);
            boolean untranslated = "new".equals(state) || "initial".equals(state) || value.isBlank();
            out.put(id, new Entry(value, line, untranslated ? (state == null || state.isEmpty() ? "empty" : state) : null));
        }
        return out;
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    static String inlineText(Element e) {
        StringBuilder sb = new StringBuilder();
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(n.getNodeValue());
            } else if (n instanceof Element child) {
                String equiv = child.getAttribute("equiv-text");
                if (!equiv.isEmpty()) {
                    sb.append(equiv);
                } else if (child.hasChildNodes()) {
                    sb.append(inlineText(child));
                } else {
                    sb.append('{').append(child.getAttribute("id")).append('}');
                }
            }
        }
        return sb.toString().strip();
    }

    private static final Pattern UNIT_ID = Pattern.compile("<(?:trans-unit|unit)\\b[^>]*\\sid=\"([^\"]*)\"");

    private static Map<String, Integer> idLines(String text) {
        Map<String, Integer> out = new HashMap<>();
        int line = 1;
        int from = 0;
        Matcher m = UNIT_ID.matcher(text);
        while (m.find()) {
            for (int i = from; i < m.start(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            from = m.start();
            out.putIfAbsent(m.group(1), line);
        }
        return out;
    }

    // ---- small file helpers --------------------------------------------------

    /** The file's text, or null when absent, unreadable, or over the size cap. */
    static String readSmall(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                return null;
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | OutOfMemoryError unreadable) {
            return null;
        }
    }

    /** Direct children of a directory, sorted by name, at most {@code cap}. */
    static List<Path> listFiles(Path dir, int cap) {
        List<Path> out = new ArrayList<>();
        if (cap <= 0 || !Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.sorted().limit(cap).forEach(out::add);
        } catch (IOException unreadable) {
            // an unreadable directory holds no catalogs we can report on
        }
        return out;
    }

    /** The 1-based line holding {@code needle}, or 1 when absent. */
    static int lineOf(String text, String needle) {
        if (text == null) {
            return 1;
        }
        int at = text.indexOf(needle);
        if (at < 0) {
            return 1;
        }
        int line = 1;
        for (int i = 0; i < at; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** The first capture of {@code p} across the project's bounded source walk, or null. */
    private static String firstMatch(Path root, Pattern p) {
        for (java.io.File f : org.nmox.studio.editor.fullstack.BoundedWalk.collect(
                root.toFile(), I18nUsage::isSourceName, I18nUsage.MAX_SOURCE_FILES)) {
            String text = readSmall(f.toPath());
            if (text == null) {
                continue;
            }
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }
}
