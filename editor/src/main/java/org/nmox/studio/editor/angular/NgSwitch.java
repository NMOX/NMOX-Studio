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
