package org.nmox.studio.editor.i18n;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nmox.studio.editor.fullstack.BoundedWalk;

/**
 * Where a project's source REFERENCES its translation keys (v2.177.0):
 * the lookup shapes of every framework the catalog reader knows —
 * {@code t('k')}, {@code $t('k')}, {@code i18n.t('k')}, {@code $_('k')},
 * {@code _('k')}, {@code $format('k')}, the {@code i18nKey=} /
 * {@code data-i18n=} / {@code keypath=} / {@code v-t=} attributes,
 * {@code <FormattedMessage id=…>}, the {@code formatMessage({id})} and
 * {@code defineMessages({…})} objects, and Paraglide's generated
 * {@code m.key(}. Comments are blanked before scanning; strings are NOT,
 * because the key IS inside a string.
 *
 * <p>A dynamic lookup — {@code t(`errors.${code}`)}, {@code t(prefix + k)},
 * {@code t(variable)} — cannot be resolved by reading, so it is recorded
 * as what it is: a reason the unused report can only say "possibly".
 * The walk is the shared bounded one; a census that stopped short says so,
 * and the unused check refuses to run on it.
 */
public final class I18nUsage {

    private I18nUsage() {
    }

    /** At most this many source files are read; more marks the census partial. */
    public static final int MAX_SOURCE_FILES = 400;

    /** One reference: the namespace it was scoped to (null = any), the key. */
    public record Ref(String namespace, String key) {
    }

    /**
     * The census: every reference, every dynamic lookup seen (its argument
     * text, so the report can name the prefixes), and whether the walk read
     * every source file it was shown.
     */
    public record Usage(Set<Ref> refs, List<String> dynamic, boolean complete) {

        /** True when some reference names the key, in this namespace or unscoped. */
        public boolean uses(String namespace, String key) {
            String ns = namespace == null || namespace.isEmpty() ? null : namespace;
            return refs.contains(new Ref(null, key))
                    || (ns != null && refs.contains(new Ref(ns, key)));
        }
    }

    static boolean isSourceName(String name) {
        return name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts")
                || name.endsWith(".tsx") || name.endsWith(".mjs") || name.endsWith(".cjs")
                || name.endsWith(".vue") || name.endsWith(".svelte")
                || name.endsWith(".html") || name.endsWith(".htm");
    }

    /** The whole project's references, through the bounded walk (OFF the EDT). */
    public static Usage scan(Path root) {
        Set<Ref> refs = new LinkedHashSet<>();
        List<String> dynamic = new ArrayList<>();
        List<File> files = BoundedWalk.collect(root.toFile(), I18nUsage::isSourceName, MAX_SOURCE_FILES);
        for (File f : files) {
            String text = I18nCatalogs.readSmall(f.toPath());
            if (text == null) {
                continue;
            }
            Usage one = fromSource(text);
            refs.addAll(one.refs());
            for (String d : one.dynamic()) {
                if (!dynamic.contains(d)) {
                    dynamic.add(d);
                }
            }
        }
        return new Usage(Set.copyOf(refs), List.copyOf(dynamic),
                BoundedWalk.complete(files, MAX_SOURCE_FILES));
    }

    /** The call names whose first argument is a key. */
    private static final Set<String> CALLS = Set.of("t", "tc", "$t", "$tc", "$_", "_", "$format");
    /** Of those, the ones whose unquoted argument is a dynamic lookup ({@code _(x)} is anyone's). */
    private static final Set<String> DYNAMIC_CALLS = Set.of("t", "tc", "$t", "$tc", "$_", "$format");
    /** The qualifiers a dynamic call may carry: {@code this.$t(x)}, {@code i18n.t(x)}. */
    private static final Set<String> DYNAMIC_QUALIFIERS = Set.of("this", "i18n", "i18next");
    /** The longest dynamic argument recorded, so a prefix stays readable. */
    private static final int DYNAMIC_CHARS = 40;
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\b(?:i18nKey|data-i18n|keypath)\\s*=\\s*([\"'])([^\"']+)\\1");
    private static final Pattern V_T = Pattern.compile("\\bv-t\\s*=\\s*\"'([^']+)'\"");
    private static final Pattern FORMATTED_MESSAGE = Pattern.compile(
            "<FormattedMessage\\b[^>]*?\\bid\\s*=\\s*([\"'])([^\"']+)\\1");
    private static final Pattern MESSAGE_OBJECT = Pattern.compile(
            "\\b(?:defineMessages|defineMessage|formatMessage)\\s*\\(");
    private static final Pattern ID_FIELD = Pattern.compile("\\bid\\s*:\\s*([\"'])([^\"']+)\\1");
    private static final Pattern PARAGLIDE = Pattern.compile("(?<![\\w$.])m\\.([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern USE_TRANSLATION = Pattern.compile(
            "\\buseTranslation\\s*\\(\\s*(?:([\"'])([^\"']+)\\1|\\[([^\\]]*)\\])");

    /** The references one source text makes, pure. */
    public static Usage fromSource(String text) {
        String src = org.nmox.studio.editor.design.CssTokens.blankComments(blankHtmlComments(text));
        Set<Ref> refs = new LinkedHashSet<>();
        List<String> dynamic = new ArrayList<>();
        List<String> fileNamespaces = fileNamespaces(src);

        scanCalls(src, refs, dynamic, fileNamespaces);
        Matcher m = ATTRIBUTE.matcher(src);
        while (m.find()) {
            addRef(refs, m.group(2), fileNamespaces);
        }
        m = V_T.matcher(src);
        while (m.find()) {
            addRef(refs, m.group(1), fileNamespaces);
        }
        m = FORMATTED_MESSAGE.matcher(src);
        while (m.find()) {
            refs.add(new Ref(null, m.group(2)));
        }
        m = MESSAGE_OBJECT.matcher(src);
        int[] closers = null;
        // every character of a descriptor body is read ONCE: a nested call's
        // body lies inside its parent's and an unclosed call's runs to the end,
        // so the bodies only ever nest — a watermark over the text reads each
        // once where a substring per match read 10,000 tails of one file
        int scannedTo = 0;
        while (m.find()) {
            if (closers == null) {
                closers = closers(src);   // one pass for every match, never a rescan per match
            }
            int close = closers[m.end() - 1];
            int end = close < 0 ? src.length() : close;
            int from = Math.max(m.end(), scannedTo);
            if (from >= end) {
                continue;
            }
            Matcher id = ID_FIELD.matcher(src).region(from, end);
            while (id.find()) {
                refs.add(new Ref(null, id.group(2)));
            }
            scannedTo = end;
        }
        m = PARAGLIDE.matcher(src);
        while (m.find()) {
            refs.add(new Ref(null, m.group(1)));
        }
        return new Usage(Set.copyOf(refs), List.copyOf(dynamic), true);
    }

    /**
     * Every lookup CALL in the text, by hand rather than by regex: a
     * pattern with an optional qualifier group before an alternation of
     * short names is the shape find-sec-bugs flags as ReDoS-prone, and
     * the house law since v1.32.0 is fix-by-idiom, never exclusion. For
     * each {@code (}: walk back over spaces to the callee chain
     * ({@code [\w$.]}), require its last segment to be a lookup name and
     * a non-identifier character before the chain (so {@code myt(} and
     * {@code fetch(} stay out), then walk forward over whitespace: a
     * quote opens a key, read to the closing quote on the same line
     * (a template holding {@code ${} is dynamic); anything else after a
     * {@code t}-family callee is a dynamic lookup, its argument kept.
     */
    static void scanCalls(String src, Set<Ref> refs, List<String> dynamic, List<String> fileNamespaces) {
        int n = src.length();
        for (int paren = src.indexOf('('); paren >= 0; paren = src.indexOf('(', paren + 1)) {
            int nameEnd = paren;
            while (nameEnd > 0 && src.charAt(nameEnd - 1) == ' ') {
                nameEnd--;
            }
            int nameStart = nameEnd;
            while (nameStart > 0 && (isIdentChar(src.charAt(nameStart - 1)) || src.charAt(nameStart - 1) == '.')) {
                nameStart--;
            }
            if (nameStart == nameEnd || (nameStart > 0 && isIdentChar(src.charAt(nameStart - 1)))) {
                continue;
            }
            String chain = src.substring(nameStart, nameEnd);
            int dot = chain.lastIndexOf('.');
            String callee = chain.substring(dot + 1);
            if (!CALLS.contains(callee)) {
                continue;
            }
            int arg = paren + 1;
            while (arg < n && Character.isWhitespace(src.charAt(arg))) {
                arg++;
            }
            if (arg >= n) {
                continue;
            }
            char quote = src.charAt(arg);
            if (quote == '\'' || quote == '"' || quote == '`') {
                int end = src.indexOf(quote, arg + 1);
                // lastIndexOf('\n', end) walked back to the file's START for
                // every key on a newline-free line: 30,000 keys cost 1.4 s
                // (the 2026-09-17 arc review); the span itself is the bound
                if (end < 0 || newlineBetween(src, arg, end)) {
                    continue;         // unterminated on its line: not a key
                }
                String key = src.substring(arg + 1, end);
                if (quote == '`' && key.contains("${")) {
                    dynamic.add(key.substring(0, key.indexOf("${")) + "${…}");
                } else {
                    addRef(refs, key, fileNamespaces);
                }
                continue;
            }
            if (quote == ')' || !DYNAMIC_CALLS.contains(callee)) {
                continue;
            }
            String qualifier = dot < 0 ? "" : chain.substring(0, dot);
            if (!qualifier.isEmpty() && !DYNAMIC_QUALIFIERS.contains(qualifier)) {
                continue;
            }
            int stop = arg;
            while (stop < n && stop - arg < DYNAMIC_CHARS && src.charAt(stop) != ')' && src.charAt(stop) != '\n') {
                stop++;
            }
            if (stop > arg && stop < n && Character.isHighSurrogate(src.charAt(stop - 1))) {
                stop--;   // the cap never splits a pair: a lone surrogate is the v1.149.0 class
            }
            String text = src.substring(arg, stop).strip();
            if (!text.isEmpty() && !dynamic.contains(text)) {
                dynamic.add(text);
            }
        }
    }

    /** A JavaScript identifier character, as the language spec spells it in ASCII. */
    private static boolean isIdentChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '$';
    }

    /**
     * {@code ns:key} is scoped by its own prefix; a bare key in a file that
     * called {@code useTranslation('ns')} is scoped to that file's
     * namespaces; a bare key anywhere else is unscoped and matches any.
     */
    private static void addRef(Set<Ref> refs, String key, List<String> fileNamespaces) {
        if (key.isEmpty()) {
            return;
        }
        int colon = key.indexOf(':');
        if (colon > 0 && colon < key.length() - 1 && !key.contains(" ")) {
            refs.add(new Ref(key.substring(0, colon), key.substring(colon + 1)));
            return;
        }
        if (fileNamespaces.isEmpty()) {
            refs.add(new Ref(null, key));
        } else {
            for (String ns : fileNamespaces) {
                refs.add(new Ref(ns, key));
            }
        }
    }

    private static List<String> fileNamespaces(String src) {
        List<String> out = new ArrayList<>();
        Matcher m = USE_TRANSLATION.matcher(src);
        while (m.find()) {
            if (m.group(2) != null) {
                out.add(m.group(2));
            } else if (m.group(3) != null) {
                Matcher q = Pattern.compile("([\"'])([^\"']+)\\1").matcher(m.group(3));
                while (q.find()) {
                    out.add(q.group(2));
                }
            }
        }
        return out;
    }

    /**
     * For every {@code (} in {@code s}, the index of the {@code )} that closes
     * it, or -1 — one stack pass over the text. The first cut rescanned from
     * each {@code defineMessages(} to the end of the file when nothing closed
     * it, which made 10,000 unclosed calls in one 160 KB file cost 4.8 s per
     * file (the 2026-09-17 arc review, hostile-input lens); the census reads
     * up to 400 such files.
     */
    static int[] closers(String s) {
        int[] close = new int[s.length()];
        java.util.Arrays.fill(close, -1);
        java.util.ArrayDeque<Integer> open = new java.util.ArrayDeque<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                open.push(i);
            } else if (c == ')' && !open.isEmpty()) {
                close[open.pop()] = i;
            }
        }
        return close;
    }

    /** True when a newline sits strictly between {@code from} and {@code to} — a scan of the span, never back to the file's start. */
    private static boolean newlineBetween(String s, int from, int to) {
        for (int k = from + 1; k < to; k++) {
            if (s.charAt(k) == '\n') {
                return true;
            }
        }
        return false;
    }

    /** {@code <!-- … -->} blanked to spaces, newlines kept, offsets preserved. */
    static String blankHtmlComments(String text) {
        StringBuilder sb = new StringBuilder(text);
        int i = 0;
        while ((i = sb.indexOf("<!--", i)) >= 0) {
            int end = sb.indexOf("-->", i + 4);
            int stop = end < 0 ? sb.length() : end + 3;
            for (int k = i; k < stop; k++) {
                if (sb.charAt(k) != '\n') {
                    sb.setCharAt(k, ' ');
                }
            }
            i = stop;
        }
        return sb.toString();
    }
}
