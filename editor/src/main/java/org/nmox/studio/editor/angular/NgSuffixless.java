package org.nmox.studio.editor.angular;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The two-signal test for a SUFFIXLESS Angular template (ledger 82,
 * the v2.37.8 probe): Angular 21's CLI generates {@code widget.html}
 * beside {@code widget.ts} with no {@code .component} infix, so the
 * declarative resolver (which cannot see siblings) never claims it
 * and the template opens as plain HTML — no Angular grammar, no
 * template chords, no language service.
 *
 * <p>An .html file is a suffixless template exactly when BOTH
 * structural signals hold (the v1.314.0 rule — two signals that
 * co-occur by construction beat one signal made stricter, and neither
 * sniffs the template's own content, so the wrong-guess-mutates law
 * is satisfied):
 *
 * <ol>
 * <li>a same-basename {@code .ts} sibling carries {@code @Component}
 *     (read bounded — a decorator lives near the top);
 * <li>an ancestor within the walk bound carries {@code angular.json}
 *     (the v1.354.0 bounded walk: stop at {@code .git}, eight levels).
 * </ol>
 *
 * <p>Pure {@code java.io.File} so every rule is a plain unit test.
 */
public final class NgSuffixless {

    /** The decorator must appear within this prefix of the sibling. */
    static final int SIBLING_READ_CAP = 64 * 1024;

    private static final int WALK_CAP = 8;

    private NgSuffixless() {
    }

    /** True when {@code html} is a suffixless Angular template. */
    public static boolean isSuffixlessTemplate(File html) {
        if (html == null || !html.isFile()) {
            return false;
        }
        String name = html.getName();
        if (!name.endsWith(".html")) {
            return false;
        }
        String base = name.substring(0, name.length() - ".html".length());
        if (base.isEmpty() || base.endsWith(".component")) {
            // .component.html is the declarative resolver's territory —
            // this predicate exists ONLY for the suffixless shape
            return false;
        }
        File sibling = new File(html.getParentFile(), base + ".ts");
        return siblingDeclaresComponent(sibling) && hasAngularAncestry(html.getParentFile());
    }

    /** Signal 1: the same-basename .ts sibling carries @Component. */
    static boolean siblingDeclaresComponent(File ts) {
        if (ts == null || !ts.isFile()) {
            return false;
        }
        try (var in = Files.newInputStream(ts.toPath())) {
            byte[] head = in.readNBytes(SIBLING_READ_CAP);
            return new String(head, StandardCharsets.UTF_8).contains("@Component");
        } catch (IOException unreadable) {
            return false;
        }
    }

    /** Signal 2: angular.json within the bounded ancestor walk. */
    static boolean hasAngularAncestry(File dir) {
        File at = dir;
        for (int i = 0; i < WALK_CAP && at != null; i++) {
            if (new File(at, "angular.json").isFile()) {
                return true;
            }
            if (new File(at, ".git").exists()) {
                // the repo boundary: an angular.json ABOVE the repo is
                // someone else's project (the v1.354.0 hijack lesson)
                return false;
            }
            at = at.getParentFile();
        }
        return false;
    }
}
