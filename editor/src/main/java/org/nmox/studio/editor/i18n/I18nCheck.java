package org.nmox.studio.editor.i18n;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.nmox.studio.editor.i18n.I18nCatalogs.Catalog;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;
import org.nmox.studio.editor.i18n.I18nCatalogs.Entry;
import org.nmox.studio.editor.i18n.I18nCatalogs.Format;

/**
 * The three things a project's translations can disagree about
 * (v2.177.0), each pure over the {@link Catalogs} record and the
 * {@link I18nUsage.Usage} census, each with the traps of the real
 * ecosystem built in:
 *
 * <ul>
 * <li><b>Missing</b> — a source key absent in a locale, compared on the
 * BASE key after CLDR plural and context suffixes are stripped, so a
 * Polish {@code items_few} beside an English {@code items_one} is a
 * translation, not a gap; an XLIFF target in state {@code new} /
 * {@code initial} or a PO {@code msgstr ""} counts as missing on ITS
 * line. A warning: this is a translation report, not a compile error.</li>
 * <li><b>Identical to source</b> — a value copied from the source, which
 * is a report and never a gate ({@code HalfTranslatedRowGateTest}
 * measured most identical values to be honest): values with no letters,
 * ICU-bearing values, vue-i18n pipe plurals and the source locale
 * itself are never counted, and the count published per locale is
 * capped at {@value #IDENTICAL_CAP} with the total kept for the
 * sentence. A per-project bless list is NOT in v1: the report is
 * deliberately only a report.</li>
 * <li><b>Placeholder mismatch</b> — the one that IS a bug: the argument
 * set of a translation differs from its source's, the
 * {@code LocaleBundleParityTest} law over a user's catalogs.</li>
 * </ul>
 *
 * <p>And the fourth, guarded twice: <b>unused</b> — a source key no
 * source file references — runs only on a COMPLETE census (a walk that
 * stopped short must not accuse a key it never reached) and downgrades
 * to "possibly unused" the moment any dynamic lookup exists, naming the
 * prefixes seen. Angular units are judged extract-against-target instead
 * (an id in a translation absent from {@code messages.xlf}); Lingui keys
 * are source text and the check is skipped, said once.
 */
public final class I18nCheck {

    private I18nCheck() {
    }

    /** What a finding is about. */
    public enum Kind {
        MISSING, IDENTICAL, MISMATCH, UNUSED, POSSIBLY_UNUSED, YAML_REFUSED
    }

    /**
     * One finding: kind, the locale it concerns (null for unused and the
     * YAML refusal), the key, the file and 1-based line a click lands on,
     * and a detail the message may quote (a state, the two argument sets,
     * the dynamic prefixes).
     */
    public record Finding(Kind kind, String locale, String key, Path file, int line, String detail) {

        /** Only a placeholder mismatch is a bug; the rest are a report. */
        public boolean error() {
            return kind == Kind.MISMATCH;
        }
    }

    /**
     * The run: the findings to publish (identical ones capped per locale),
     * the uncapped identical total per locale, why the unused check did
     * not run (null when it did), and the dynamic lookups seen.
     */
    public record Report(List<Finding> findings, Map<String, Integer> identicalTotals,
            String unusedSkipped, List<String> dynamic) {

        /** Findings of one kind. */
        public List<Finding> of(Kind kind) {
            List<Finding> out = new ArrayList<>();
            for (Finding f : findings) {
                if (f.kind() == kind) {
                    out.add(f);
                }
            }
            return out;
        }
    }

    /** Identical findings published per locale; the total still counts. */
    public static final int IDENTICAL_CAP = 50;

    /** The unused check did not run: the census stopped short. */
    public static final String SKIPPED_PARTIAL = "partial";
    /** The unused check did not run: Lingui keys are source text. */
    public static final String SKIPPED_LINGUI = "lingui";

    private static final Set<String> PLURAL_SUFFIXES =
            Set.of("zero", "one", "two", "few", "many", "other", "plural");

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern PIPE = Pattern.compile("\\S\\s*\\|\\s*\\S");

    /** All three checks plus the guarded unused check over the census. */
    public static Report run(Catalogs catalogs, I18nUsage.Usage usage) {
        List<Finding> out = new ArrayList<>();
        Map<String, Integer> identicalTotals = new TreeMap<>();
        if (catalogs.yamlRefusal() != null) {
            out.add(new Finding(Kind.YAML_REFUSED, null, "", catalogs.yamlRefusal(),
                    catalogs.yamlRefusalLine(), ""));
            return new Report(List.copyOf(out), identicalTotals, null, List.of());
        }
        Map<String, Catalog> sources = new LinkedHashMap<>();
        for (Catalog c : catalogs.source()) {
            sources.put(c.namespace(), c);
        }
        for (String locale : catalogs.locales()) {
            if (locale.equals(catalogs.sourceLocale())) {
                continue;
            }
            Map<String, Catalog> targets = new LinkedHashMap<>();
            for (Catalog c : catalogs.ofLocale(locale)) {
                targets.put(c.namespace(), c);
            }
            int identical = 0;
            for (Map.Entry<String, Catalog> e : sources.entrySet()) {
                Catalog source = e.getValue();
                Catalog target = targets.get(e.getKey());
                Map<String, Entry> targetEntries = target == null ? Map.of() : target.entries();
                Set<String> targetBases = bases(targetEntries);
                for (Map.Entry<String, Entry> se : sortedEntries(source.entries())) {
                    String key = se.getKey();
                    Entry sv = se.getValue();
                    Entry tv = targetEntries.get(key);
                    if (tv != null && tv.untranslated()) {
                        out.add(new Finding(Kind.MISSING, locale, key, target.file(), tv.line(), tv.state()));
                        continue;
                    }
                    if (tv == null) {
                        if (!targetBases.contains(baseKey(key, source.entries().keySet()))) {
                            out.add(new Finding(Kind.MISSING, locale, key, source.file(), sv.line(), ""));
                        }
                        continue;
                    }
                    if (sv.untranslated()) {
                        continue;     // a source entry with nothing to compare against
                    }
                    Set<String> sourceArgs = IcuArgs.names(sv.value());
                    Set<String> targetArgs = IcuArgs.names(tv.value());
                    if (!sourceArgs.equals(targetArgs)) {
                        out.add(new Finding(Kind.MISMATCH, locale, key, target.file(), tv.line(),
                                sourceArgs + " ≠ " + targetArgs));
                        continue;
                    }
                    if (isIdentical(sv.value(), tv.value(), catalogs.format())) {
                        identical++;
                        if (identical <= IDENTICAL_CAP) {
                            out.add(new Finding(Kind.IDENTICAL, locale, key, target.file(), tv.line(), ""));
                        }
                    }
                }
            }
            if (identical > 0) {
                identicalTotals.put(locale, identical);
            }
        }
        String skipped = unused(catalogs, sources, usage, out);
        return new Report(List.copyOf(out), identicalTotals, skipped,
                usage == null ? List.of() : usage.dynamic());
    }

    /**
     * Identical means: the same text, in a value that HAS letters (a
     * {@code →}, an {@code OK}, a number is the same in every language),
     * carries no ICU typed argument (the categories are the language's
     * own) and, in vue-i18n, no {@code a | b} plural pipe (segment counts
     * differ by design).
     */
    static boolean isIdentical(String source, String target, Format format) {
        if (!source.equals(target)) {
            return false;
        }
        if (!LETTER.matcher(source).find()) {
            return false;
        }
        if (IcuArgs.hasIcu(source)) {
            return false;
        }
        return !(format == Format.VUE_NESTED && PIPE.matcher(source).find());
    }

    /**
     * The key with a CLDR plural suffix ({@code _zero _one _two _few _many
     * _other}, legacy {@code _plural}) stripped — or a context suffix
     * ({@code key_male}) stripped only when the bare key exists in the
     * same catalog, because {@code first_name} is a key of its own. Only
     * the last dot segment is looked at, since a nested parent is never a
     * plural form.
     */
    static String baseKey(String key, Set<String> keysInCatalog) {
        int us = key.lastIndexOf('_');
        if (us <= 0 || us == key.length() - 1) {
            return key;
        }
        if (key.lastIndexOf('.') > us) {
            return key;
        }
        String base = key.substring(0, us);
        String suffix = key.substring(us + 1);
        if (PLURAL_SUFFIXES.contains(suffix)) {
            return base;
        }
        return keysInCatalog.contains(base) ? base : key;
    }

    private static Set<String> bases(Map<String, Entry> entries) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (!e.getValue().untranslated()) {
                out.add(baseKey(e.getKey(), entries.keySet()));
            }
        }
        return out;
    }

    private static List<Map.Entry<String, Entry>> sortedEntries(Map<String, Entry> entries) {
        List<Map.Entry<String, Entry>> out = new ArrayList<>(entries.entrySet());
        out.sort(Map.Entry.comparingByKey());
        return out;
    }

    /** Adds the unused findings; returns why it could not, or null when it ran. */
    private static String unused(Catalogs catalogs, Map<String, Catalog> sources,
            I18nUsage.Usage usage, List<Finding> out) {
        if (catalogs.format() == Format.LINGUI_PO) {
            return SKIPPED_LINGUI;
        }
        if (catalogs.format() == Format.ANGULAR_XLIFF) {
            // the extract is the truth: an id a translation still carries
            // that the extract no longer has is a message the app lost
            Catalog extract = sources.get("");
            Set<String> ids = extract == null ? Set.of() : extract.entries().keySet();
            for (Catalog c : catalogs.catalogs()) {
                if (c.locale().equals(catalogs.sourceLocale())) {
                    continue;
                }
                for (Map.Entry<String, Entry> e : sortedEntries(c.entries())) {
                    if (!ids.contains(e.getKey())) {
                        out.add(new Finding(Kind.UNUSED, null, e.getKey(), c.file(),
                                e.getValue().line(), "messages.xlf"));
                    }
                }
            }
            return null;
        }
        if (usage == null || !catalogs.censusComplete() || !usage.complete()) {
            return SKIPPED_PARTIAL;
        }
        Kind kind = usage.dynamic().isEmpty() ? Kind.UNUSED : Kind.POSSIBLY_UNUSED;
        String detail = String.join(", ", usage.dynamic());
        for (Catalog source : sources.values()) {
            Set<String> keys = source.entries().keySet();
            for (Map.Entry<String, Entry> e : sortedEntries(source.entries())) {
                String key = e.getKey();
                if (usage.uses(source.namespace(), key)
                        || usage.uses(source.namespace(), baseKey(key, keys))) {
                    continue;
                }
                out.add(new Finding(kind, null, key, source.file(), e.getValue().line(), detail));
            }
        }
        return null;
    }
}
