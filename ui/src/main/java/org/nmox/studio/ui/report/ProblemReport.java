package org.nmox.studio.ui.report;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Report a Problem, the pure half: what a useful bug report carries
 * (version, OS, Java, the log's tail) and what it must never carry (the
 * user's home path and name, anything shaped like a credential). The
 * product composes the body, SHOWS it in full, and hands it to the user
 * — it never sends anything: the GitHub new-issue page opens with the
 * body pre-filled, and the user presses Submit there, or copies it.
 *
 * <p>Bounded twice: the log tail is the LAST {@code maxLines} lines,
 * and a body that would push the new-issue URL past GitHub's limit is
 * clipped with a marker while the full text stays on the clipboard path.
 */
public final class ProblemReport {

    /** The repository's new-issue page. */
    public static final String NEW_ISSUE = "https://github.com/NMOX/NMOX-Studio/issues/new";
    /** Log lines kept from the end of messages.log. */
    public static final int MAX_LOG_LINES = 40;
    /** Longest URL we hand to a browser (GitHub refuses around 8k). */
    public static final int MAX_URL_CHARS = 7_500;
    private static final String CLIP_MARKER = "\n\n[log tail clipped to fit the issue URL — paste the copied report for the rest]";

    /** Credential shapes that must never leave the machine in a bug report. */
    private static final Pattern SECRET = Pattern.compile(
            "(sk-ant-[A-Za-z0-9_-]{8,}|ghp_[A-Za-z0-9]{8,}|github_pat_[A-Za-z0-9_]{8,}"
            + "|(?i:bearer)\\s+[A-Za-z0-9._-]{8,}|(?i:api[_-]?key|token|secret|password)\\s*[=:]\\s*\\S{4,})");

    private ProblemReport() {
    }

    /** The last {@code maxLines} NON-BLANK lines of a log, in order — blank lines never eat the budget. */
    public static String tail(String log, int maxLines) {
        if (log == null || log.isBlank() || maxLines <= 0) {
            return "";
        }
        String[] lines = log.split("\n");
        List<String> kept = new ArrayList<>();
        for (int i = lines.length - 1; i >= 0 && kept.size() < maxLines; i--) {
            if (!lines[i].isBlank()) {
                kept.add(0, lines[i]);
            }
        }
        return String.join("\n", kept);
    }

    /**
     * Strips the user's identity and anything credential-shaped: the home
     * directory becomes {@code ~}, the login becomes {@code <user>}, and a
     * secret-shaped token becomes {@code [redacted]}.
     */
    public static String redact(String text, String home, String user) {
        if (text == null) {
            return "";
        }
        String out = text;
        if (home != null && !home.isBlank()) {
            out = out.replace(home, "~");
        }
        if (user != null && user.length() >= 3) {
            out = out.replace(user, "<user>");
        }
        return SECRET.matcher(out).replaceAll("[redacted]");
    }

    /** The markdown body: environment table + fenced log tail. */
    public static String compose(String version, String os, String java, String logTail) {
        StringBuilder sb = new StringBuilder();
        sb.append("**What happened**\n\n(describe what you did and what you expected)\n\n");
        sb.append("**Environment**\n\n");
        sb.append("- NMOX Studio: ").append(blankTo(version, "dev build")).append('\n');
        sb.append("- OS: ").append(blankTo(os, "unknown")).append('\n');
        sb.append("- Java: ").append(blankTo(java, "unknown")).append('\n');
        if (logTail != null && !logTail.isBlank()) {
            sb.append("\n**Log tail** (redacted — paths under your home show as ~)\n\n```\n")
                    .append(logTail).append("\n```\n");
        }
        return sb.toString();
    }

    /**
     * The new-issue URL with title and body pre-filled; a body that would
     * exceed {@link #MAX_URL_CHARS} is clipped from the END with a marker,
     * so the environment block always survives.
     */
    public static String issueUrl(String title, String body) {
        String base = NEW_ISSUE + "?title=" + encode(title) + "&body=";
        String candidate = base + encode(body);
        if (candidate.length() <= MAX_URL_CHARS) {
            return candidate;
        }
        // shrink the body until it fits; encoding inflates, so iterate on the encoded length
        int keep = body.length();
        while (keep > 0) {
            keep = keep * 3 / 4;
            String clipped = body.substring(0, keep) + CLIP_MARKER;
            String url = base + encode(clipped);
            if (url.length() <= MAX_URL_CHARS) {
                return url;
            }
        }
        return base + encode(CLIP_MARKER.strip());
    }

    /** Whether {@link #issueUrl} had to clip this body. */
    public static boolean clipped(String url) {
        return url.contains(encode("[log tail clipped"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
