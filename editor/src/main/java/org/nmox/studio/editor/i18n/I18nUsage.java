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

    // the call names whose first argument is a key
    private static final Pattern QUOTED_CALL = Pattern.compile(
            "(?<![\\w$])(?:[\\w$]+\\.)?(?:\\$?tc?|\\$_|_|\\$format)\\s*\\(\\s*(['\"`])");
    private static final Pattern DYNAMIC_CALL = Pattern.compile(
            "(?<![\\w$])(?:this\\.|i18n\\.|i18next\\.)?(?:\\$?tc?|\\$_|\\$format)\\s*\\(\\s*(?!['\"`)\\s])([^)\\n]{1,40})");
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

        Matcher m = QUOTED_CALL.matcher(src);
        while (m.find()) {
            char quote = m.group(1).charAt(0);
            int start = m.end();
            int end = src.indexOf(quote, start);
            if (end < 0 || src.lastIndexOf('\n', end) >= start) {
                continue;             // unterminated on its line: not a key
            }
            String key = src.substring(start, end);
            if (quote == '`' && key.contains("${")) {
                dynamic.add(key.substring(0, key.indexOf("${")) + "${…}");
                continue;
            }
            addRef(refs, key, fileNamespaces);
        }
        m = DYNAMIC_CALL.matcher(src);
        while (m.find()) {
            String arg = m.group(1).strip();
            if (!arg.isEmpty() && !dynamic.contains(arg)) {
                dynamic.add(arg);
            }
        }
        m = ATTRIBUTE.matcher(src);
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
        while (m.find()) {
            int close = balanced(src, m.end() - 1);
            String body = src.substring(m.end(), close < 0 ? src.length() : close);
            Matcher id = ID_FIELD.matcher(body);
            while (id.find()) {
                refs.add(new Ref(null, id.group(2)));
            }
        }
        m = PARAGLIDE.matcher(src);
        while (m.find()) {
            refs.add(new Ref(null, m.group(1)));
        }
        return new Usage(Set.copyOf(refs), List.copyOf(dynamic), true);
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

    /** Index of the paren closing the one at {@code open}, or -1. */
    private static int balanced(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
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
