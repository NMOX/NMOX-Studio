package org.nmox.studio.ui.browser.devtools;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.nmox.studio.core.spi.LiveServings;

/**
 * Maps the Browser's current page URL back to the source file on disk
 * — the first half of inspect-to-source (v1.357.0). Two honest cases:
 * a {@code file://} page IS its source, and a page served by one of
 * the rack's own serve devices (the {@link LiveServings} registry)
 * maps through the serving's project directory. Anything else — a
 * remote site, a dev server the registry doesn't know — resolves to
 * null and the caller says so instead of guessing.
 *
 * <p>The decoded URL path is resolved with a canonical containment
 * check (the learning-space path law, v1.310.0): a hostile or mangled
 * path like {@code /../../etc/passwd} must never escape the project.
 */
public final class PageSourceResolver {

    private PageSourceResolver() {
    }

    /** The served-file result: where it lives and which root matched. */
    public record Resolved(File file) {
    }

    /**
     * Resolves a page URL to its source file, or null when the page
     * is not backed by a local file this IDE can name.
     */
    public static Resolved resolve(String pageUrl, List<LiveServings.Serving> servings) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(pageUrl.trim());
        } catch (IllegalArgumentException bad) {
            return null;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            File f = new File(uri.getPath() == null ? "" : uri.getPath());
            return f.isFile() ? new Resolved(f) : null;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        for (LiveServings.Serving s : servings) {
            if (s.projectDir() == null || s.url() == null) {
                continue;
            }
            URI served;
            try {
                served = URI.create(s.url().trim());
            } catch (IllegalArgumentException bad) {
                continue;
            }
            if (!sameOrigin(uri, served)) {
                continue;
            }
            File hit = fileForPath(s.projectDir(), uri.getRawPath());
            if (hit != null) {
                return new Resolved(hit);
            }
        }
        return null;
    }

    /** Origin match: scheme + host (loopback spellings unified) + port. */
    static boolean sameOrigin(URI page, URI served) {
        if (page.getScheme() == null || served.getScheme() == null
                || !page.getScheme().equalsIgnoreCase(served.getScheme())) {
            return false;
        }
        if (effectivePort(page) != effectivePort(served)) {
            return false;
        }
        String a = loopbackName(page.getHost());
        String b = loopbackName(served.getHost());
        return a != null && a.equalsIgnoreCase(b);
    }

    /** localhost / 127.0.0.1 / [::1] are one origin for this purpose. */
    private static String loopbackName(String host) {
        if (host == null) {
            return null;
        }
        String h = host.toLowerCase();
        return switch (h) {
            case "127.0.0.1", "::1", "[::1]", "0.0.0.0" -> "localhost";
            default -> h;
        };
    }

    private static int effectivePort(URI u) {
        if (u.getPort() != -1) {
            return u.getPort();
        }
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    /**
     * The URL path inside the serving's project: the file itself, its
     * {@code index.html} for directory paths, and the conventional
     * {@code public/} docroot as a fallback. Canonical containment is
     * checked on every candidate so no decoded path escapes the root.
     */
    static File fileForPath(File projectDir, String rawPath) {
        String path = rawPath == null || rawPath.isEmpty() ? "/"
                : URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        String rel = path.startsWith("/") ? path.substring(1) : path;
        String[] candidates = rel.isEmpty() || path.endsWith("/")
                ? new String[]{rel + "index.html", "public/" + rel + "index.html"}
                : new String[]{rel, "public/" + rel, rel + "/index.html", "public/" + rel + "/index.html"};
        for (String candidate : candidates) {
            File f = insideOnly(projectDir, candidate);
            if (f != null && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** Canonical containment guard; null when the path escapes the root. */
    private static File insideOnly(File root, String relative) {
        try {
            File f = new File(root, relative).getCanonicalFile();
            String rootPath = root.getCanonicalPath() + File.separator;
            return f.getPath().startsWith(rootPath) ? f : null;
        } catch (IOException e) {
            return null;
        }
    }
}
