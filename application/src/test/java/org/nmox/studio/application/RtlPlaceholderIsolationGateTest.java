package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Bidi;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
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
 * An argument that lands in a right-to-left sentence is isolated.
 *
 * <p>The defect this exists to catch: a {@code {0}} carrying a file name after
 * an Arabic or Hebrew word lays out as {@code nmoxrack.json.} — the leading dot
 * takes the sentence's direction and is drawn AFTER the name. {@code
 * NativeTypographyGateTest} has held that rule since v2.151.0 for a dotfile
 * written LITERALLY in the value, and it cannot see this one, because a
 * placeholder has no dot to match. The English enters below the gate, which is
 * the ledger-88 shape one layer over.
 *
 * <p>The population is DERIVED, never listed: for each argument-only element in
 * each right-to-left value, walk back to the first strong directional character.
 * If it is right-to-left — or if nothing strong precedes it in a value that does
 * contain right-to-left letters, so the paragraph direction governs — the
 * element must be wrapped in {@code U+2066 LRI … U+2069 PDI}.
 *
 * <p>The rule is unconditional rather than a ledger of "which arguments can
 * carry a path", and {@link #theGuardIsInertWhereItIsNotNeeded()} is why: the
 * guard is measurably identical to bare for a number, for right-to-left text,
 * for a Latin word and for a Latin phrase. Nothing has to reason about what an
 * argument holds at run time, which is just as well — of 927 such arguments,
 * exactly one carried the {@code # {0} - …} comment that would have said.
 *
 * <p>Three populations are deliberately out, each measured rather than assumed:
 * <ul>
 *   <li>a {@code {n,choice,…}} element carries prose of its own, often
 *       right-to-left prose, and a LEFT-to-right isolate around it would lay
 *       that prose out in a left-to-right paragraph. Its BRANCHES are swept;
 *       the element is not.
 *   <li>a placeholder inside an HTML tag is markup, not prose. The real one is
 *       {@code <body style='width: {0}'>} — an invisible character inside a CSS
 *       declaration corrupts the declaration (the v2.128.0 dialog, again).
 *   <li>a value with no right-to-left letter at all is drawn in logical order:
 *       {@code Bidi.requiresBidi} is false for it, so no bidi acts and there is
 *       nothing to get wrong.
 * </ul>
 */
class RtlPlaceholderIsolationGateTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");

    static final char LRI = '⁦';
    static final char PDI = '⁩';

    /** A real HTML tag, so ChoiceFormat's {@code 1<} limit is not read as one. */
    private static final Pattern TAG = Pattern.compile("<\\s*/?\\s*[A-Za-z][^<>]*>");
    private static final Pattern RTL_LETTER = Pattern.compile("[\\u0590-\\u05ff\\u0600-\\u06ff]");
    private static final Pattern ELEMENT = Pattern.compile("\\{(\\d+)\\s*([,}])");

    private record Slot(int start, int end, String kind) { }

    /** Outermost format elements; a choice is entered so its branches are seen. */
    static List<Slot> elements(String value) {
        List<Slot> out = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            Matcher m = ELEMENT.matcher(value).region(i, value.length())
                    .useAnchoringBounds(false).useTransparentBounds(true);
            if (!m.lookingAt()) {
                i++;
                continue;
            }
            if ("}".equals(m.group(2))) {
                out.add(new Slot(m.start(), m.end(), "simple"));
                i = m.end();
                continue;
            }
            String rest = value.substring(m.end(), Math.min(value.length(), m.end() + 7));
            String kind = rest.startsWith("number") ? "number"
                    : rest.startsWith("choice") ? "choice" : "other";
            int depth = 1;
            int j = m.end();
            while (j < value.length() && depth > 0) {
                if (value.charAt(j) == '{') {
                    depth++;
                } else if (value.charAt(j) == '}') {
                    depth--;
                }
                j++;
            }
            out.add(new Slot(m.start(), j, kind));
            i = "choice".equals(kind) ? m.end() : j;
        }
        return out;
    }

    /**
     * The last strong direction before {@code pos}. An isolate is OPAQUE: what
     * is inside one cannot be the strong character the outside sees, which is
     * exactly why the guard does not change the context of what follows it.
     */
    static String contextAt(String value, int pos) {
        int i = pos - 1;
        while (i >= 0) {
            char ch = value.charAt(i);
            if (ch == PDI) {
                int depth = 1;
                i--;
                while (i >= 0 && depth > 0) {
                    char c = value.charAt(i);
                    if (c == PDI) {
                        depth++;
                    } else if (c == LRI || c == '⁧' || c == '⁨') {
                        depth--;
                    }
                    i--;
                }
                continue;
            }
            byte d = Character.getDirectionality(ch);
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT
                    || d == Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
                    || d == Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
                    || ch == LRI) {
                return "LTR";
            }
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
                    || ch == '⁧') {
                return "RTL";
            }
            i--;
        }
        return "PARAGRAPH";
    }

    static boolean insideTag(String value, int pos) {
        Matcher m = TAG.matcher(value);
        while (m.find()) {
            if (m.start() < pos && pos < m.end()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this element must carry a guard, by the derived rule.
     *
     * <p>The context is read from BEFORE the element's own guard, not from the
     * character next to the element. Reading it next to the element asks "is
     * this guarded?" and answers "yes, so it does not need to be" — the first
     * cut did exactly that and would have checked only the values that were
     * already wrong.
     */
    static boolean mustBeIsolated(String value, Slot slot) {
        if ("choice".equals(slot.kind()) || "other".equals(slot.kind())) {
            return false;
        }
        if (insideTag(value, slot.start())) {
            return false;
        }
        int before = slot.start();
        if (before > 0 && (value.charAt(before - 1) == LRI
                || value.charAt(before - 1) == '‎')) {
            before--;                      // step over this element's own guard
        }
        String ctx = contextAt(value, before);
        if ("LTR".equals(ctx)) {
            return false;
        }
        if ("PARAGRAPH".equals(ctx) && !RTL_LETTER.matcher(value).find()) {
            return false;
        }
        return true;
    }

    @Test
    @DisplayName("every argument landing in a right-to-left sentence is isolated")
    void everyRtlPlaceholderIsIsolated() throws IOException {
        List<String> unguarded = new ArrayList<>();
        int checked = 0;
        for (Value v : rtlValues()) {
            for (Slot slot : elements(v.text())) {
                if (!mustBeIsolated(v.text(), slot)) {
                    continue;
                }
                checked++;
                boolean open = slot.start() > 0 && v.text().charAt(slot.start() - 1) == LRI;
                boolean close = slot.end() < v.text().length()
                        && v.text().charAt(slot.end()) == PDI;
                if (!open || !close) {
                    unguarded.add(v.where() + " " + v.key()
                            + "  argument " + v.text().substring(slot.start(),
                                    Math.min(slot.end(), slot.start() + 6))
                            + "  (wrap it in U+2066 … U+2069)");
                }
            }
        }
        assertThat(checked)
                .as("arguments examined in the assembled cluster's right-to-left bundles — "
                        + "a gate that reads nothing finds every sentence perfectly laid out")
                .isGreaterThan(1500);
        assertThat(unguarded)
                .as("an argument that lands after a right-to-left word and carries a file "
                        + "name, a path, a glob or a flag has its leading character drawn on "
                        + "the wrong side; the literal-dot rule cannot see it, because a "
                        + "placeholder has no dot to match")
                .isEmpty();
    }

    /** An isolate wraps exactly one element, opens before it closes, and nests. */
    private static final Pattern ISOLATED = Pattern.compile(
            "\\u2066(\\{\\d+(?:\\}|,number,[^{}]*\\}))\\u2069");

    @Test
    @DisplayName("every isolate is well formed and wraps exactly one argument")
    void everyIsolateIsWellFormed() throws IOException {
        List<String> malformed = new ArrayList<>();
        int carrying = 0;
        for (Value v : rtlValues()) {
            String text = v.text();
            if (text.indexOf(LRI) < 0 && text.indexOf(PDI) < 0) {
                continue;
            }
            carrying++;
            int depth = 0;
            boolean ok = true;
            for (int i = 0; i < text.length() && ok; i++) {
                char c = text.charAt(i);
                if (c == LRI) {
                    depth++;
                } else if (c == PDI && --depth < 0) {
                    ok = false;
                }
            }
            if (!ok || depth != 0) {
                malformed.add(v.where() + " " + v.key() + "  (isolates do not balance)");
                continue;
            }
            // every opened isolate must close around one element and nothing else
            int opens = (int) text.chars().filter(c -> c == LRI).count();
            int wraps = 0;
            Matcher m = ISOLATED.matcher(text);
            while (m.find()) {
                wraps++;
            }
            if (opens != wraps) {
                malformed.add(v.where() + " " + v.key()
                        + "  (an isolate does not wrap exactly one argument)");
            }
        }
        assertThat(carrying).as("values carrying an isolate").isGreaterThan(1000);
        assertThat(malformed)
                .as("two ADJACENT arguments put the close of the first and the open of the "
                        + "second at one offset; swapping them yields LRI{1}LRI PDI{2}PDI, "
                        + "which MessageFormat parses happily and bidi does not")
                .isEmpty();
    }

    /**
     * The property the unconditional rule rests on. If this ever fails, the
     * sweep stops being safe and the rule has to become a ledger of which
     * arguments can carry a path — a question only a call site can answer.
     */
    @Test
    @DisplayName("the guard is inert where it is not needed, and fixes what it is for")
    void theGuardIsInertWhereItIsNotNeeded() {
        String head = "تم الحفظ في ";
        String tail = " خلاص";
        Locale ar = Locale.forLanguageTag("ar-u-nu-latn");
        MessageFormat bare = new MessageFormat(head + "{0}" + tail, ar);
        MessageFormat guarded = new MessageFormat(head + LRI + "{0}" + PDI + tail, ar);

        Map<String, Object> inert = new TreeMap<>();
        inert.put("a number", 9216);
        inert.put("right-to-left text", "جهاز");
        inert.put("a Latin word", "npm");
        inert.put("a Latin phrase", "Unexpected end of input");
        for (Map.Entry<String, Object> e : inert.entrySet()) {
            Object[] a = {e.getValue()};
            assertThat(visual(guarded.format(a)))
                    .as("the guard must change nothing for %s — the whole unconditional "
                            + "rule rests on this", e.getKey())
                    .isEqualTo(visual(bare.format(a)));
        }

        // and the two shapes it exists for, one of which an LRM cannot fix
        for (String carried : List.of(".env", ".nmoxrack.json", "~/.nmox/devices.d",
                "/usr/local/bin/node", "*.js", "--watch", "http://localhost:8080/")) {
            Object[] a = {carried};
            assertThat(visual(bare.format(a)))
                    .as("%s must be the defect this gate exists for", carried)
                    .doesNotContain(carried);
            assertThat(visual(guarded.format(a)))
                    .as("the isolate must lay %s out as it was written", carried)
                    .contains(carried);
        }
    }

    @Test
    @DisplayName("the guard does not switch bidi on for a value that had none")
    void theGuardDoesNotSwitchBidiOn() {
        String noRtl = "9216 ms";
        assertThat(requiresBidi(noRtl)).isFalse();
        assertThat(requiresBidi(LRI + noRtl + PDI))
                .as("an isolate must not make Swing lay out a value it was drawing in "
                        + "logical order — that is why the sweep skips values with no "
                        + "right-to-left letter instead of guarding them blindly")
                .isFalse();
        assertThat(requiresBidi("‫" + noRtl + "‬"))
                .as("an EMBEDDING does switch it on, which is why the guard is an isolate")
                .isTrue();
    }

    private static boolean requiresBidi(String s) {
        return Bidi.requiresBidi(s.toCharArray(), 0, s.length());
    }

    /** Screen order, left to right; right-to-left runs reversed into place. */
    private static String visual(String logical) {
        Bidi bidi = new Bidi(logical, Bidi.DIRECTION_RIGHT_TO_LEFT);
        int n = bidi.getRunCount();
        byte[] levels = new byte[n];
        Integer[] order = new Integer[n];
        String[] runs = new String[n];
        for (int i = 0; i < n; i++) {
            levels[i] = (byte) bidi.getRunLevel(i);
            runs[i] = logical.substring(bidi.getRunStart(i), bidi.getRunLimit(i));
            order[i] = i;
        }
        Bidi.reorderVisually(levels.clone(), 0, order, 0, n);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int r = order[i];
            String run = runs[r].replace(String.valueOf(LRI), "").replace(String.valueOf(PDI), "");
            sb.append((levels[r] & 1) != 0 ? new StringBuilder(run).reverse() : run);
        }
        return sb.toString();
    }

    private record Value(String lang, String where, String key, String text) { }

    /** The right-to-left languages, derived from what the bundles actually hold. */
    private static List<Value> rtlValues() throws IOException {
        Pattern bundle = Pattern.compile("(?:^|/)Bundle(?:_nmoxstudio)?_([a-z]{2})\\.properties$");
        List<String> langs = new ArrayList<>();
        for (UiLocale.Choice c : UiLocale.SUPPORTED) {
            if (!c.code().isEmpty() && !c.code().equals("en")) {
                langs.add(c.code());
            }
        }
        Map<String, Integer> rtlHits = new TreeMap<>();
        List<Value> all = new ArrayList<>();
        for (Path jar : jars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    Matcher m = bundle.matcher(e.getName());
                    if (!m.find() || !langs.contains(m.group(1))) {
                        continue;
                    }
                    Properties p = new Properties();
                    try (InputStream in = zip.getInputStream(e)) {
                        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    }
                    for (String key : p.stringPropertyNames()) {
                        String text = p.getProperty(key);
                        all.add(new Value(m.group(1), jar.getFileName() + "!" + e.getName(),
                                key, text));
                        if (RTL_LETTER.matcher(text).find()) {
                            rtlHits.merge(m.group(1), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        List<String> rtlLangs = rtlHits.entrySet().stream()
                .filter(e -> e.getValue() > 100).map(Map.Entry::getKey).toList();
        assertThat(rtlLangs)
                .as("right-to-left languages derived from the bundles themselves, never listed — "
                        + "a fifteenth language that writes right to left joins this gate by "
                        + "shipping, not by being remembered")
                .isNotEmpty();
        return all.stream().filter(v -> rtlLangs.contains(v.lang())).toList();
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
