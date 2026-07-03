package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Standards Kit: generates the web's well-known standard files,
 * each correct to its spec - robots.txt (REP, RFC 9309), sitemap.xml
 * (sitemaps.org protocol), site.webmanifest (W3C Web App Manifest),
 * .well-known/security.txt (RFC 9116, with the required Expires), and
 * humans.txt. Pure builders so every claim is testable; the wizard in
 * the File menu drives {@link #write}.
 */
public final class StandardsKit {

    private StandardsKit() {
    }

    /** What the wizard collected. */
    public record Options(String siteUrl, String siteName, String contactEmail,
            boolean robots, boolean sitemap, boolean manifest,
            boolean securityTxt, boolean humans) {
    }

    /** One generated (or skipped) file, for the wizard's report. */
    public record Outcome(String path, boolean written) {
    }

    public static String robots(String siteUrl) {
        return """
                # robots.txt — Robots Exclusion Protocol (RFC 9309)
                User-agent: *
                Allow: /

                Sitemap: %s/sitemap.xml
                """.formatted(trimSlash(siteUrl));
    }

    public static String sitemap(String siteUrl, LocalDate lastmod) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url>
                    <loc>%s/</loc>
                    <lastmod>%s</lastmod>
                  </url>
                </urlset>
                """.formatted(trimSlash(siteUrl), lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    /** W3C Web App Manifest with the members installability requires. */
    public static String manifest(String siteName) {
        JSONObject m = new JSONObject();
        m.put("name", siteName);
        m.put("short_name", siteName.length() > 12 ? siteName.substring(0, 12) : siteName);
        m.put("start_url", "/");
        m.put("display", "standalone");
        m.put("background_color", "#1a1a1e");
        m.put("theme_color", "#1a1a1e");
        JSONArray icons = new JSONArray();
        icons.put(new JSONObject().put("src", "/icon-192.png").put("sizes", "192x192")
                .put("type", "image/png"));
        icons.put(new JSONObject().put("src", "/icon-512.png").put("sizes", "512x512")
                .put("type", "image/png"));
        m.put("icons", icons);
        return m.toString(2) + "\n";
    }

    /**
     * RFC 9116: Contact and Expires are REQUIRED; Expires must be
     * RFC 3339 and SHOULD be less than a year away - we set exactly
     * the recommended horizon so tooling nags before it lapses.
     */
    public static String securityTxt(String contactEmail, String siteUrl, LocalDate today) {
        // RFC 3339 requires seconds; Instant.toString always carries them
        String expires = today.plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        return """
                # security.txt — RFC 9116
                Contact: mailto:%s
                Expires: %s
                Canonical: %s/.well-known/security.txt
                Preferred-Languages: en
                """.formatted(contactEmail, expires, trimSlash(siteUrl));
    }

    public static String humans(String siteName, String contactEmail) {
        return """
                /* TEAM */
                Site: %s
                Contact: %s

                /* SITE */
                Standards: HTML5, CSS3, ES2024
                Built with: NMOX Studio
                """.formatted(siteName, contactEmail);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Writes the selected files into the project's public root
     * (existing files are never overwritten - the kit scaffolds, it
     * does not clobber). Returns what happened, file by file.
     */
    public static List<Outcome> write(File projectDir, Options opts) throws IOException {
        List<Outcome> outcomes = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (opts.robots()) {
            outcomes.add(writeOne(projectDir, "robots.txt", robots(opts.siteUrl())));
        }
        if (opts.sitemap()) {
            outcomes.add(writeOne(projectDir, "sitemap.xml", sitemap(opts.siteUrl(), today)));
        }
        if (opts.manifest()) {
            outcomes.add(writeOne(projectDir, "site.webmanifest", manifest(opts.siteName())));
        }
        if (opts.securityTxt()) {
            outcomes.add(writeOne(projectDir, ".well-known/security.txt",
                    securityTxt(opts.contactEmail(), opts.siteUrl(), today)));
        }
        if (opts.humans()) {
            outcomes.add(writeOne(projectDir, "humans.txt",
                    humans(opts.siteName(), opts.contactEmail())));
        }
        return outcomes;
    }

    private static Outcome writeOne(File dir, String relative, String content) throws IOException {
        File target = new File(dir, relative);
        if (target.exists()) {
            return new Outcome(relative, false);
        }
        Files.createDirectories(target.getParentFile().toPath());
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        return new Outcome(relative, true);
    }
}
