package org.nmox.studio.core.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The pure half of Copy as Markdown with Link (v2.87.0, the developer
 * evangelist's issue-and-post gesture): a git remote URL becomes a GitHub
 * {@code blob} link for a file and a line range. Everything here is a
 * string function — the file reads live in {@link GitFacts}. GitHub only,
 * by design: a link the product cannot vouch for is a refusal, not a
 * guess (GitLab's {@code -/blob} shape and self-hosted forges are not
 * GitHub's).
 */
public final class GitLink {

    /** A parsed GitHub remote: {@code owner/repo}. */
    public record Remote(String owner, String repo) {
        public String slug() {
            return owner + "/" + repo;
        }
    }

    private GitLink() {
    }

    /**
     * The {@code url} of {@code [remote "origin"]} in a git config's text,
     * or null. A plain INI scan: sections in brackets, keys before {@code =},
     * quotes and whitespace tolerated the way git writes them. Only the
     * origin section is consulted — a repo with {@code upstream} first still
     * links where the user pushes.
     */
    public static String originUrl(String configText) {
        if (configText == null) {
            return null;
        }
        boolean inOrigin = false;
        for (String raw : configText.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("[")) {
                inOrigin = line.replace("\t", " ").replaceAll("\\s+", " ")
                        .equalsIgnoreCase("[remote \"origin\"]");
                continue;
            }
            if (!inOrigin || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase("url")) {
                String url = line.substring(eq + 1).trim();
                return url.isEmpty() ? null : url;
            }
        }
        return null;
    }

    /**
     * The GitHub owner/repo behind a remote URL in any form git accepts —
     * {@code git@github.com:o/r.git}, {@code https://github.com/o/r},
     * {@code ssh://git@github.com/o/r.git}, {@code git://github.com/o/r} —
     * or null for anything that is not github.com or has no owner/repo.
     */
    public static Remote parseRemote(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String host;
        String path;
        String u = url.trim();
        if (u.contains("://")) {
            try {
                URI uri = new URI(u);
                host = uri.getHost();
                path = uri.getPath();
            } catch (URISyntaxException ex) {
                return null;
            }
        } else {
            // scp-like: [user@]host:path — an index walk, not a regex (a
            // backtracking pattern over remote text is a ReDoS surface;
            // SpotBugs named it on the first verify)
            int colon = u.indexOf(':');
            if (colon <= 0 || colon == u.length() - 1) {
                return null;
            }
            String authority = u.substring(0, colon);
            int at = authority.lastIndexOf('@');
            host = at >= 0 ? authority.substring(at + 1) : authority;
            if (host.isEmpty() || host.indexOf('/') >= 0 || authority.indexOf('/') >= 0) {
                return null;
            }
            path = "/" + u.substring(colon + 1);
        }
        if (host == null || !host.equalsIgnoreCase("github.com") || path == null) {
            return null;
        }
        String p = path;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.endsWith(".git")) {
            p = p.substring(0, p.length() - 4);
        }
        String[] parts = p.split("/");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return new Remote(parts[0], parts[1]);
    }

    /**
     * {@code https://github.com/o/r/blob/<ref>/<path>#L3-L14}. The path is
     * repo-relative with forward slashes, each segment percent-encoded
     * (a space in a file name is {@code %20}, a slash stays a separator);
     * the ref is a branch name or sha, encoded the same way. Lines are
     * 1-based inclusive: {@code startLine <= 0} means no fragment (the
     * whole file), a single line is {@code #L7}.
     */
    public static String blobUrl(Remote remote, String ref, String relPath, int startLine, int endLine) {
        StringBuilder sb = new StringBuilder("https://github.com/")
                .append(remote.owner()).append('/').append(remote.repo())
                .append("/blob/").append(encodePath(ref)).append('/').append(encodePath(relPath));
        if (startLine > 0) {
            sb.append("#L").append(startLine);
            if (endLine > startLine) {
                sb.append("-L").append(endLine);
            }
        }
        return sb.toString();
    }

    /**
     * {@code https://github.com/o/r/tree/<ref>/<path>} — a FOLDER's page
     * (3.2.0, the file tree's Open on GitHub). The path is repo-relative
     * with forward slashes and encoded exactly as {@link #blobUrl} encodes
     * it; an empty path is the repository's root at that ref
     * ({@code …/tree/<ref>}), never a trailing slash.
     */
    public static String treeUrl(Remote remote, String ref, String relPath) {
        StringBuilder sb = new StringBuilder("https://github.com/")
                .append(remote.owner()).append('/').append(remote.repo())
                .append("/tree/").append(encodePath(ref));
        if (relPath != null && !relPath.isEmpty()) {
            sb.append('/').append(encodePath(relPath));
        }
        return sb.toString();
    }

    /**
     * {@code https://github.com/o/r/compare/<branch>?expand=1} — GitHub's
     * own New Pull Request page for {@code branch} against the repository's
     * default branch (3.2.0). The branch is encoded as {@link #blobUrl}
     * encodes a ref, so {@code feature/x} keeps its slash.
     */
    public static String compareUrl(Remote remote, String branch) {
        return "https://github.com/" + remote.owner() + '/' + remote.repo()
                + "/compare/" + encodePath(branch) + "?expand=1";
    }

    /**
     * {@code https://github.com/o/r/commit/<sha>} — a commit's own page
     * (3.2.0, the line blame note's Open Commit on GitHub), or null when
     * {@code sha} is not a commit id: 7 to 64 hexadecimal digits, so a
     * repository's text can never steer the link anywhere else.
     */
    public static String commitUrl(Remote remote, String sha) {
        if (sha == null || sha.length() < 7 || sha.length() > 64) {
            return null;
        }
        for (int i = 0; i < sha.length(); i++) {
            if (Character.digit(sha.charAt(i), 16) < 0) {
                return null;
            }
        }
        return "https://github.com/" + remote.owner() + '/' + remote.repo()
                + "/commit/" + sha.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * A remote URL fit to show: the user-info part of a {@code scheme://}
     * URL is dropped, because a remote like
     * {@code https://oauth2:glpat-…@gitlab.com/g/p.git} carries a token in
     * it and a refusal must not print it on the status line (3.2.0 review).
     * An scp-style {@code git@host:path} has no secret in it and is kept.
     */
    public static String withoutCredentials(String url) {
        if (url == null) {
            return null;
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int hostStart = scheme + 3;
        int pathStart = url.indexOf('/', hostStart);
        int at = url.lastIndexOf('@', pathStart < 0 ? url.length() : pathStart);
        return at < hostStart ? url : url.substring(0, hostStart) + url.substring(at + 1);
    }

    /** The Markdown link line under the block: {@code [src/App.jsx#L3-L14](url)}. */
    public static String linkLine(String relPath, int startLine, int endLine, String url) {
        String label = relPath;
        if (startLine > 0) {
            label += "#L" + startLine + (endLine > startLine ? "-L" + endLine : "");
        }
        return "[" + label.replace("]", "\\]") + "](" + url + ")";
    }

    static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/", -1)) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            for (byte b : seg.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                int c = b & 0xff;
                boolean keep = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '-' || c == '_' || c == '.' || c == '~';
                if (keep) {
                    sb.append((char) c);
                } else {
                    sb.append('%').append(String.format("%02X", c));
                }
            }
        }
        return sb.toString();
    }
}
