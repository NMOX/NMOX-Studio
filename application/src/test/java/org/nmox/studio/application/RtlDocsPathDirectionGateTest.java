package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.core.util.UiLocale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A path keeps its shape inside a right-to-left sentence (ledger 121, 3.2.0).
 *
 * <p>The characters a path begins and ends with — {@code .} {@code ~}
 * {@code /} — have no direction of their own. The bidi algorithm gives a
 * neutral between a right-to-left letter and a Latin letter the paragraph's
 * direction, so in a Hebrew or Arabic sentence {@code ~/NMOX/app} is drawn
 * {@code NMOX/app/~}, {@code .env} is drawn {@code env.}, and a trailing
 * {@code /} or {@code .} ({@code ~/.nmox/devices.d/}, {@code nmox .}) jumps
 * to the far side of the name. {@link #theBidiAlgorithmDetachesTheNeutrals}
 * holds that measurement, so the decision is pinned rather than remembered.
 *
 * <p>The rule, in {@code docs/i18n/conventions.md}: an LRM (U+200E) goes
 * before a path or name that begins with {@code .} {@code ~} or {@code /}
 * when the nearest strong character before it is right to left, and after
 * one that ends with {@code /} (or, in a code span, {@code .}) when the
 * nearest strong character after it is right to left or the line ends in a
 * right-to-left paragraph. The mark goes OUTSIDE the code span, so a reader
 * who copies the path copies no invisible character.
 *
 * <p>The population is derived: every {@code docs/**}{@code /*.<lang>.md}
 * for every language {@link UiLocale#SUPPORTED} lays out right to left, and
 * in each, every path the reader sees (fenced blocks, link targets and HTML
 * are not seen). A third right-to-left language joins by being shipped.
 */
class RtlDocsPathDirectionGateTest {

    static final char LRM = '‎';
    private static final String NEUTRAL_START = "~./";
    /** What may sit directly before a path: space, markup, an opening mark, a joining hyphen. */
    private static final String OPENERS = " \t`*([«\"‑־";
    /** What ends a path written without a code span. */
    private static final String BARE_END = "`*)»\"],،؛:";
    private static final Pattern CODE_SPAN = Pattern.compile("`[^`]+`");
    private static final Pattern UNSEEN = Pattern.compile("`[^`]+`|\\]\\([^)]*\\)|<!--.*?-->|<[^>`]*>");

    /** One path whose edge would detach; {@code at} is where the LRM belongs. */
    record Crossing(int line, int at, String path, boolean leading) {
        @Override
        public String toString() {
            return (line + 1) + ": " + (leading ? "LRM before " : "LRM after ") + path;
        }
    }

    // ---------------------------------------------------------------- measurement

    /** The characters of {@code logical} in screen order, left to right. */
    static String visual(String logical) {
        Bidi bidi = new Bidi(logical, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
        int n = logical.length();
        byte[] levels = new byte[n];
        Object[] chars = new Object[n];
        for (int i = 0; i < n; i++) {
            levels[i] = (byte) bidi.getLevelAt(i);
            chars[i] = logical.charAt(i);
        }
        Bidi.reorderVisually(levels, 0, chars, 0, n);
        StringBuilder out = new StringBuilder(n);
        for (Object c : chars) {
            out.append((char) (Character) c);
        }
        return out.toString();
    }

    @Test
    @DisplayName("measured: the bidi algorithm detaches a path's neutral edges in a right-to-left sentence, and an LRM keeps them")
    void theBidiAlgorithmDetachesTheNeutrals() {
        // Leading neutrals: home, relative, parent, absolute, dotfile — Hebrew and Arabic.
        for (String rtl : List.of("שלום ", "في ")) {
            assertThat(visual(rtl + "~/NMOX/app")).contains("NMOX/app/~").doesNotContain("~/NMOX/app");
            assertThat(visual(rtl + LRM + "~/NMOX/app")).contains("~/NMOX/app");
            assertThat(visual(rtl + "./app/src")).contains("app/src/.");
            assertThat(visual(rtl + LRM + "./app/src")).contains("./app/src");
            assertThat(visual(rtl + "../shared/lib")).contains("shared/lib/..");
            assertThat(visual(rtl + LRM + "../shared/lib")).contains("../shared/lib");
            assertThat(visual(rtl + "/usr/local/bin")).contains("usr/local/bin/").doesNotContain("/usr");
            assertThat(visual(rtl + LRM + "/usr/local/bin")).contains("/usr/local/bin");
            assertThat(visual(rtl + ".env")).contains("env.");
            assertThat(visual(rtl + LRM + ".env")).contains(".env");
            assertThat(visual(rtl + "*.json")).contains("json.*");
            assertThat(visual(rtl + LRM + "*.json")).contains("*.json");
        }
        // A path that begins with a letter was never at risk.
        assertThat(visual("שלום NMOX/app")).contains("NMOX/app");
        // Trailing neutrals: the LRM before cannot hold the slash at the end...
        assertThat(visual("שלום " + LRM + "~/.nmox/devices.d/ בתיקייה"))
                .contains("/" + LRM + "~/.nmox/devices.d ");
        // ...an LRM after it does.
        assertThat(visual("שלום " + LRM + "~/.nmox/devices.d/" + LRM + " בתיקייה"))
                .contains(LRM + "~/.nmox/devices.d/" + LRM);
        assertThat(visual("כמו ש‑code . עושה")).contains(". code").doesNotContain("code .");
        assertThat(visual("כמו ש‑code ." + LRM + " עושה")).contains("code ." + LRM);
    }

    // ---------------------------------------------------------------- census

    private static byte strength(char c) {
        byte d = Character.getDirectionality(c);
        if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
            return 1;
        }
        if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
            return 2;
        }
        return 0;
    }

    private static boolean latin(char c) {
        return c < 128 && (Character.isLetter(c) || c == '_');
    }

    /**
     * Does a path begin at {@code i}: {@code ~/}, {@code ./}, {@code ../}, {@code .name},
     * {@code /name}, or a glob's {@code *.} / {@code **}.
     */
    static boolean pathStart(String s, int i) {
        char c = s.charAt(i);
        char n1 = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
        char n2 = i + 2 < s.length() ? s.charAt(i + 2) : ' ';
        return switch (c) {
            case '~' -> n1 == '/';
            case '.' -> n1 == '/' || (n1 == '.' && n2 == '/') || latin(n1) || n1 == '*';
            case '/' -> latin(n1) || n1 == '.' || n1 == '~';
            case '*' -> n1 == '.' || n1 == '/' || n1 == '*'; // a glob: *.json, **/*.ts
            default -> false;
        };
    }

    /** Blank what the reader never sees (link targets, comments, tags), keeping every offset. */
    static String seen(String line) {
        StringBuilder sb = new StringBuilder(line);
        Matcher m = UNSEEN.matcher(line);
        while (m.find()) {
            if (m.group().startsWith("`")) {
                continue;
            }
            int from = m.group().startsWith("]") ? m.start() + 1 : m.start();
            for (int k = from; k < m.end(); k++) {
                sb.setCharAt(k, ' ');
            }
        }
        return sb.toString();
    }

    private static int bareEnd(String line, int i, int limit) {
        int end = i;
        while (end < limit && !Character.isWhitespace(line.charAt(end)) && BARE_END.indexOf(line.charAt(end)) < 0) {
            end++;
        }
        return end;
    }

    /** Every path edge in a Markdown document that the bidi algorithm would detach. */
    static List<Crossing> crossings(List<String> lines) {
        List<Crossing> out = new ArrayList<>();
        boolean fenced = false;
        for (int ln = 0; ln < lines.size(); ln++) {
            String raw = lines.get(ln);
            String stripped = raw.strip();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                continue;
            }
            String line = seen(raw);
            boolean[] inCode = new boolean[line.length()];
            int[] closer = new int[line.length()];
            Arrays.fill(closer, -1);
            Matcher cm = CODE_SPAN.matcher(line);
            while (cm.find()) {
                Arrays.fill(inCode, cm.start(), cm.end(), true);
                closer[cm.start()] = cm.end() - 1;
            }
            // A table cell is a paragraph of its own (GitHub gives each one dir=auto).
            List<int[]> paragraphs = new ArrayList<>();
            if (stripped.startsWith("|")) {
                int from = 0;
                for (int k = 0; k < line.length(); k++) {
                    if (line.charAt(k) == '|' && !inCode[k] && (k == 0 || line.charAt(k - 1) != '\\')) {
                        paragraphs.add(new int[] {from, k});
                        from = k + 1;
                    }
                }
                paragraphs.add(new int[] {from, line.length()});
            } else {
                paragraphs.add(new int[] {0, line.length()});
            }
            for (int[] p : paragraphs) {
                leading(line, ln, p[0], p[1], inCode, closer, out);
                trailing(line, ln, p[0], p[1], inCode, closer, out);
            }
        }
        return out;
    }

    private static void leading(String line, int ln, int a, int b, boolean[] inCode, int[] closer,
            List<Crossing> out) {
        for (int i = a; i < b; i++) {
            char c = line.charAt(i);
            if ((NEUTRAL_START.indexOf(c) < 0 && c != '*') || !pathStart(line, i)) {
                continue;
            }
            char prev = i > a ? line.charAt(i - 1) : ' ';
            if (c == '*' && !(prev == '`' && closer[i - 1] > 0)) {
                continue; // outside a code span a star is Markdown emphasis; a glob lives in a span
            }
            if (OPENERS.indexOf(prev) < 0) {
                continue;
            }
            boolean opensSpan = prev == '`' && closer[i - 1] > 0;
            if (prev == '`' && !opensSpan) {
                continue; // a closing backtick is not where a path begins
            }
            if (!opensSpan && inCode[i] && prev != ' ' && prev != '\t') {
                continue; // inside a span a path begins after a space: `*.json` is one name
            }
            byte before = 0;
            for (int k = i - 1; k >= a && before == 0; k--) {
                before = strength(line.charAt(k));
            }
            if (before != 2) {
                continue;
            }
            int end;
            if (opensSpan) {
                end = closer[i - 1];
            } else if (inCode[i]) { // a path mid-span, after a space: `cd ~/x`
                end = i;
                while (end < b && !Character.isWhitespace(line.charAt(end)) && line.charAt(end) != '`') {
                    end++;
                }
            } else {
                end = bareEnd(line, i, b);
            }
            int at = i;
            while (at > a && (line.charAt(at - 1) == '`' || line.charAt(at - 1) == '*')) {
                at--;
            }
            out.add(new Crossing(ln, at, line.substring(i, end), true));
        }
    }

    private static void trailing(String line, int ln, int a, int b, boolean[] inCode, int[] closer,
            List<Crossing> out) {
        byte paragraph = 0;
        for (int k = a; k < b && paragraph == 0; k++) {
            paragraph = strength(line.charAt(k));
        }
        for (int i = a; i < b; i++) {
            int last;
            String path;
            boolean bare = false;
            if (closer[i] > 0 && closer[i] <= b) {
                last = closer[i];
                path = line.substring(i + 1, last);
                if (!path.endsWith("/") && !path.endsWith(".")) {
                    i = last;
                    continue;
                }
            } else if (!inCode[i] && NEUTRAL_START.indexOf(line.charAt(i)) >= 0 && pathStart(line, i)
                    && OPENERS.indexOf(i > a ? line.charAt(i - 1) : ' ') >= 0) {
                int end = bareEnd(line, i, b);
                path = line.substring(i, end);
                // A bare path's final '.' is the sentence's full stop, which belongs to the sentence.
                if (!path.endsWith("/")) {
                    i = end;
                    continue;
                }
                last = end - 1;
                bare = true;
            } else {
                continue;
            }
            byte after = 0;
            for (int k = last + 1; k < b && after == 0; k++) {
                after = strength(line.charAt(k));
            }
            if (after == 2 || (after == 0 && paragraph == 2)) {
                int at = last + 1;
                while (!bare && at < b && line.charAt(at) == '*') {
                    at++;
                }
                out.add(new Crossing(ln, at, path, false));
            }
            i = last;
        }
    }

    // ---------------------------------------------------------------- the gate

    static List<Path> rightToLeftDocs() throws IOException {
        List<String> suffixes = UiLocale.SUPPORTED.stream()
                .map(UiLocale.Choice::code)
                .filter(code -> !code.isEmpty() && TextDirection.isRightToLeft(Locale.forLanguageTag(code)))
                .map(code -> "." + code + ".md")
                .toList();
        assertThat(suffixes).as("the right-to-left languages UiLocale ships").contains(".he.md", ".ar.md");
        try (Stream<Path> s = Files.walk(Path.of("..", "docs"))) {
            return s.filter(p -> suffixes.stream().anyMatch(sfx -> p.getFileName().toString().endsWith(sfx)))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> lines(Path p) throws IOException {
        return Arrays.asList(Files.readString(p).replace("\r\n", "\n").split("\n", -1));
    }

    @Test
    @DisplayName("every path in a Hebrew or Arabic document keeps its edges: an LRM where a neutral would detach")
    void rightToLeftDocsKeepPathsWhole() throws IOException {
        List<Path> docs = rightToLeftDocs();
        assertThat(docs).as("the right-to-left documents").hasSizeGreaterThanOrEqualTo(50);
        List<String> wrong = new ArrayList<>();
        int held = 0;
        for (Path doc : docs) {
            List<String> ls = lines(doc);
            for (Crossing c : crossings(ls)) {
                wrong.add(doc.getFileName() + ":" + c);
            }
            for (String l : ls) {
                for (int k = l.indexOf(LRM); k >= 0; k = l.indexOf(LRM, k + 1)) {
                    held++;
                }
            }
        }
        // The census must be looking at something: the marks it holds number in the hundreds.
        assertThat(held).as("LRMs the right-to-left documents carry").isGreaterThan(200);
        assertThat(wrong)
                .as("a path beginning with . ~ / after a right-to-left word takes an LRM before it, and one ending "
                        + "with / or . before a right-to-left word takes one after it, outside the code span "
                        + "(docs/i18n/conventions.md)")
                .isEmpty();
    }

    @Test
    @DisplayName("an LRM never sits inside a code span, so a copied path carries no invisible character")
    void marksStayOutsideCodeSpans() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path doc : rightToLeftDocs()) {
            List<String> ls = lines(doc);
            boolean fenced = false;
            for (int i = 0; i < ls.size(); i++) {
                String l = ls.get(i);
                if (l.strip().startsWith("```")) {
                    fenced = !fenced;
                }
                Matcher m = CODE_SPAN.matcher(l);
                while (!fenced && m.find()) {
                    if (m.group().indexOf(LRM) >= 0 || m.group().indexOf('‏') >= 0) {
                        wrong.add(doc.getFileName() + ":" + (i + 1) + "  " + m.group().replace(LRM, '⁅'));
                    }
                }
                if (fenced && (l.indexOf(LRM) >= 0 || l.indexOf('‏') >= 0)) {
                    wrong.add(doc.getFileName() + ":" + (i + 1) + "  (a directional mark inside a fenced block)");
                }
            }
        }
        assertThat(wrong).as("directional marks inside code").isEmpty();
    }

    @Test
    @DisplayName("the census finds both edges, and leaves alone what the algorithm already places")
    void theCensusReadsBothEdges() {
        assertThat(crossings(List.of("התיקייה `~/NMOX` נוצרת"))).extracting(Crossing::path).containsExactly("~/NMOX");
        assertThat(crossings(List.of("התיקייה " + LRM + "`~/NMOX` נוצרת"))).isEmpty();
        assertThat(crossings(List.of("في ملف .env كده"))).extracting(Crossing::path).containsExactly(".env");
        assertThat(crossings(List.of("ב‑`/c` בלבד"))).extracting(Crossing::path).containsExactly("/c");
        assertThat(crossings(List.of("ב‑‏`.npmrc` בלבד"))).as("an RLM is right to left").hasSize(1);
        // The trailing edge, and where the mark goes: after the closing backtick and the bold.
        List<Crossing> both = crossings(List.of("בתיקייה **`~/.nmox/devices.d/`** נשמר"));
        assertThat(both).extracting(Crossing::leading).containsExactly(true, false);
        String fixed = "בתיקייה **`~/.nmox/devices.d/`** נשמר";
        fixed = fixed.substring(0, both.get(1).at()) + LRM + fixed.substring(both.get(1).at());
        fixed = fixed.substring(0, both.get(0).at()) + LRM + fixed.substring(both.get(0).at());
        assertThat(fixed).isEqualTo("בתיקייה " + LRM + "**`~/.nmox/devices.d/`**" + LRM + " נשמר");
        assertThat(crossings(List.of(fixed))).isEmpty();
        assertThat(crossings(List.of("ממסוף, `nmox .` פותח"))).extracting(Crossing::path).containsExactly("nmox .");
        assertThat(crossings(List.of("שימו `*.json` בתיקייה"))).extracting(Crossing::path).containsExactly("*.json");
        assertThat(crossings(List.of("שימו **הדגשה** כאן"))).as("a star outside a span is emphasis").isEmpty();
        // Left alone: a Latin context, a sentence's full stop, a link target, a fenced block, a table cell of its own.
        assertThat(crossings(List.of("Run `~/NMOX` now"))).isEmpty();
        assertThat(crossings(List.of("התיקייה נוצרת. אחר כך"))).isEmpty();
        assertThat(crossings(List.of("ראו [המדריך](../user-guide.he.md) עכשיו"))).isEmpty();
        assertThat(crossings(List.of("```", "שלום ~/NMOX", "```"))).isEmpty();
        assertThat(crossings(List.of("| הסבר | `~/NMOX` |"))).isEmpty();
        assertThat(crossings(List.of("שלום `src/app.js` עכשיו"))).isEmpty();
    }
}
