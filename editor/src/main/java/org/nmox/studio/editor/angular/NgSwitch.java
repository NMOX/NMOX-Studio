package org.nmox.studio.editor.angular;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pure half of the Angular component ↔ template switcher
 * (v1.219.0): given one side of the pair, where is the other? UI-free
 * so every rule is a plain unit test.
 *
 * <p>Resolution order mirrors how Angular projects are actually laid
 * out: the {@code templateUrl} in the {@code @Component} decorator is
 * the truth when present (it can point anywhere); the
 * {@code name.component.ts} / {@code name.component.html} sibling
 * convention is the fallback both directions.
 */
public final class NgSwitch {

    /** Matches templateUrl: './hello.component.html' (either quote). */
    private static final Pattern TEMPLATE_URL =
            Pattern.compile("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]");

    /**
     * Matches the FIRST stylesheet the decorator names, in either
     * spelling: Angular 17+ writes {@code styleUrl: './x.css'} and
     * older projects {@code styleUrls: ['./x.css', …]}. Both forms put
     * the first URL in the same capture, so one pattern serves the
     * whole ecosystem the IDE has to open (v1.313.0).
     */
    private static final Pattern STYLE_URL =
            Pattern.compile("styleUrls?\\s*:\\s*\\[?\\s*['\"]([^'\"]+)['\"]");

    /** The stylesheet extensions an Angular workspace can be configured for. */
    private static final String[] STYLE_EXTENSIONS =
            {".css", ".scss", ".sass", ".less"};

    private NgSwitch() {
    }

    /**
     * The template a component file points at: the decorator's
     * {@code templateUrl} resolved against the component's directory,
     * else the same-basename {@code .html} sibling. Null when neither
     * exists (inline-template components have no file to open).
     */
    public static File templateFor(File componentTs, String componentSource) {
        File dir = componentTs.getParentFile();
        if (componentSource != null) {
            Matcher m = TEMPLATE_URL.matcher(componentSource);
            if (m.find()) {
                // normalize: templateUrl conventionally starts with "./"
                File byUrl = new File(dir, m.group(1)).toPath().normalize().toFile();
                if (byUrl.isFile()) {
                    return byUrl;
                }
            }
        }
        File sibling = new File(dir, swapExtension(componentTs.getName(), ".ts", ".html"));
        return sibling.isFile() ? sibling : null;
    }

    /**
     * The component class a template belongs to: the same-basename
     * {@code .ts} sibling, else the first sibling {@code .ts} whose
     * source references this template by name in a {@code templateUrl}.
     * Null when no owner is found.
     */
    public static File componentFor(File templateHtml, SourceReader reader) {
        File dir = templateHtml.getParentFile();
        File sibling = new File(dir, swapExtension(templateHtml.getName(), ".html", ".ts"));
        if (sibling.isFile()) {
            return sibling;
        }
        File[] candidates = dir == null ? null : dir.listFiles(
                (d, name) -> name.endsWith(".ts") && !name.endsWith(".spec.ts"));
        if (candidates != null) {
            for (File candidate : candidates) {
                String source = reader.read(candidate);
                if (source != null && referencesTemplate(source, templateHtml.getName())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * The stylesheet a component file points at (v1.313.0): the
     * decorator's {@code styleUrl}/{@code styleUrls} first entry
     * resolved against the component's directory, else the first
     * same-basename sibling in the extensions an Angular workspace can
     * be configured for (css, scss, sass, less — the {@code ng new
     * --style} choices). Null when the component has none: inline
     * {@code styles: []} and style-less components are both ordinary.
     */
    public static File stylesFor(File componentTs, String componentSource) {
        File dir = componentTs.getParentFile();
        if (componentSource != null) {
            Matcher m = STYLE_URL.matcher(componentSource);
            if (m.find()) {
                File byUrl = new File(dir, m.group(1)).toPath().normalize().toFile();
                if (byUrl.isFile()) {
                    return byUrl;
                }
            }
        }
        for (String ext : STYLE_EXTENSIONS) {
            File sibling = new File(dir, swapExtension(componentTs.getName(), ".ts", ext));
            if (sibling.isFile()) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * The spec beside a component (v1.313.0): {@code foo.component.ts}
     * → {@code foo.component.spec.ts}. Null when the component has no
     * spec on disk — {@code ng generate --skip-tests} is a real choice,
     * so its absence is reported honestly rather than invented.
     *
     * <p>Called on a spec itself this returns null (a spec has no spec),
     * which keeps the action's refusal message truthful.
     */
    public static File specFor(File componentTs) {
        String name = componentTs.getName();
        if (!name.endsWith(".ts") || name.endsWith(".spec.ts")) {
            return null;
        }
        File spec = new File(componentTs.getParentFile(),
                swapExtension(name, ".ts", ".spec.ts"));
        return spec.isFile() ? spec : null;
    }

    /**
     * The component class a sibling file belongs to (v1.313.0), for the
     * files that carry no reference back: a stylesheet or a spec names
     * its component only by the basename convention, so
     * {@code foo.component.css} and {@code foo.component.spec.ts} both
     * resolve to {@code foo.component.ts}. Null when it isn't there.
     */
    public static File componentForSibling(File sibling) {
        String name = sibling.getName();
        String base = name.endsWith(".spec.ts")
                ? name.substring(0, name.length() - ".spec.ts".length())
                : stripLastExtension(name);
        if (base.isEmpty()) {
            return null;
        }
        File component = new File(sibling.getParentFile(), base + ".ts");
        return component.isFile() && !component.equals(sibling) ? component : null;
    }

    private static String stripLastExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(0, dot);
    }

    /** True when {@code source}'s templateUrl names {@code templateName}. */
    static boolean referencesTemplate(String source, String templateName) {
        Matcher m = TEMPLATE_URL.matcher(source);
        while (m.find()) {
            String url = m.group(1);
            int slash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
            if (url.substring(slash + 1).equals(templateName)) {
                return true;
            }
        }
        return false;
    }

    static String swapExtension(String name, String from, String to) {
        return name.endsWith(from)
                ? name.substring(0, name.length() - from.length()) + to
                : name + to;
    }

    /** Seam for tests: how a candidate component file's text is read. */
    @FunctionalInterface
    public interface SourceReader {
        String read(File file);
    }
}
