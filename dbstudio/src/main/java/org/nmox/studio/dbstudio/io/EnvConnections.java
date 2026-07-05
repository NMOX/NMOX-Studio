package org.nmox.studio.dbstudio.io;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * Reads a project's {@code .env} and turns the Laravel/LEMP-template
 * {@code DB_*} family (or a {@code DATABASE_URL}) into a connection
 * <em>suggestion</em> — something the UI offers to prefill the New
 * Connection dialog with, never a connection it creates behind the
 * user's back.
 *
 * <p><b>Secrecy expectation</b>: the parsed values include the
 * database password. Nothing here logs, stores or serializes them —
 * the {@link Suggestion} travels straight to the dialog prefill, and
 * from there the password's only home is the OS keychain
 * ({@code Passwords}); it never touches {@code .nmoxdb.json}. Callers
 * must uphold the same rule: never log a {@code Suggestion}.
 *
 * <p>{@code .env} parsing rules: {@code KEY=VALUE} lines split on the
 * first {@code =}; keys and values whitespace-trimmed; surrounding
 * matched single or double quotes stripped; {@code #} comment lines
 * and blank lines ignored; an optional {@code export } prefix
 * tolerated; later duplicate keys win.
 *
 * <p>Recognized signals, in precedence order:
 * <ol>
 *   <li>{@code DB_CONNECTION} (mysql, mariadb, pgsql/postgres/
 *       postgresql, sqlite), {@code DB_HOST}, {@code DB_PORT},
 *       {@code DB_DATABASE}, {@code DB_USERNAME}, {@code DB_PASSWORD}.
 *       When {@code DB_CONNECTION} is absent the engine falls back to
 *       the port (3306 → MySQL, 5432 → PostgreSQL). A suggestion needs
 *       at least CONNECTION+DATABASE or HOST+DATABASE (with a
 *       derivable engine); anything thinner is noise, not signal.</li>
 *   <li>{@code DATABASE_URL} with the
 *       {@code mysql://user:pass@host:port/db} /
 *       {@code postgres(ql)://…} / {@code mariadb://…} shapes —
 *       user and password URL-decoded, a missing port meaning the
 *       engine's default. Consulted only when the {@code DB_*} family
 *       didn't produce a suggestion.</li>
 * </ol>
 */
public final class EnvConnections {

    /**
     * A prefill for the New Connection dialog.
     *
     * @param engine         the engine the .env points at
     * @param host           server host ({@code "localhost"} when the
     *                       file names a database but no host); empty
     *                       for SQLite
     * @param port           server port, the engine default when the
     *                       file names none ({@code -1} for SQLite)
     * @param database       database name — for SQLite this is
     *                       {@code DB_DATABASE} verbatim, typically the
     *                       file path
     * @param user           login user, possibly empty
     * @param passwordOrNull the password when the file carries one,
     *                       else {@code null}; NEVER log or persist it
     */
    public record Suggestion(
            DbEngine engine,
            String host,
            int port,
            String database,
            String user,
            String passwordOrNull) {

        /** Redacts the password — a logged Suggestion must not leak it. */
        @Override
        public String toString() {
            return "Suggestion[engine=" + engine + ", host=" + host + ", port=" + port
                    + ", database=" + database + ", user=" + user
                    + ", passwordOrNull=" + (passwordOrNull == null ? "null" : "•••")
                    + "]";
        }
    }

    private EnvConnections() {
    }

    /**
     * Whether a manifest-change batch (the rack's coalesced
     * {@code manifestChanged} payload) touches a {@code .env} file —
     * exact filename match, so {@code .env.example} stays quiet. One
     * boolean per batch by construction: however many .env files a kit
     * writes, the caller reacts once.
     */
    public static boolean touchesEnv(java.util.List<java.nio.file.Path> batch) {
        if (batch == null) {
            return false;
        }
        for (java.nio.file.Path path : batch) {
            java.nio.file.Path name = path == null ? null : path.getFileName();
            if (name != null && ".env".equals(name.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The connection the given {@code .env} content suggests, or empty
     * when the file carries too little signal. Null-safe, never throws.
     */
    public static Optional<Suggestion> fromEnv(String envFileContent) {
        Map<String, String> env = parse(envFileContent);
        Optional<Suggestion> fromDbFamily = fromDbKeys(env);
        if (fromDbFamily.isPresent()) {
            return fromDbFamily;
        }
        return fromDatabaseUrl(env.get("DATABASE_URL"));
    }

    // ---- the DB_* family -------------------------------------------

    private static Optional<Suggestion> fromDbKeys(Map<String, String> env) {
        String connection = env.get("DB_CONNECTION");
        String host = env.get("DB_HOST");
        String database = env.get("DB_DATABASE");
        int port = parsePort(env.get("DB_PORT"));

        DbEngine engine = engineForConnection(connection);
        if (engine == null && connection == null) {
            engine = engineForPort(port);
        }
        if (engine == null || database == null || database.isEmpty()) {
            return Optional.empty();
        }
        // enough signal: CONNECTION+DATABASE, or HOST+DATABASE
        if (connection == null && (host == null || host.isEmpty())) {
            return Optional.empty();
        }
        if (engine == DbEngine.SQLITE) {
            return Optional.of(new Suggestion(engine, "", -1, database,
                    nz(env.get("DB_USERNAME")), env.get("DB_PASSWORD")));
        }
        return Optional.of(new Suggestion(
                engine,
                (host == null || host.isEmpty()) ? "localhost" : host,
                port > 0 ? port : engine.defaultPort(),
                database,
                nz(env.get("DB_USERNAME")),
                env.get("DB_PASSWORD")));
    }

    private static DbEngine engineForConnection(String connection) {
        if (connection == null) {
            return null;
        }
        return switch (connection.toLowerCase(Locale.ROOT)) {
            case "mysql" -> DbEngine.MYSQL;
            case "mariadb" -> DbEngine.MARIADB;
            case "pgsql", "postgres", "postgresql" -> DbEngine.POSTGRES;
            case "sqlite" -> DbEngine.SQLITE;
            default -> null;
        };
    }

    private static DbEngine engineForPort(int port) {
        return switch (port) {
            case 3306 -> DbEngine.MYSQL;
            case 5432 -> DbEngine.POSTGRES;
            default -> null;
        };
    }

    // ---- DATABASE_URL ----------------------------------------------

    private static Optional<Suggestion> fromDatabaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (java.net.URISyntaxException malformed) {
            return Optional.empty();
        }
        DbEngine engine = engineForScheme(uri.getScheme());
        if (engine == null) {
            return Optional.empty();
        }
        String host = uri.getHost();
        String database = stripLeadingSlash(uri.getPath());
        if (host == null || host.isEmpty() || database.isEmpty()) {
            return Optional.empty();
        }
        String user = "";
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            user = urlDecode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            if (colon >= 0) {
                password = urlDecode(userInfo.substring(colon + 1));
            }
        }
        int port = uri.getPort() > 0 ? uri.getPort() : engine.defaultPort();
        return Optional.of(new Suggestion(engine, host, port, database, user, password));
    }

    private static DbEngine engineForScheme(String scheme) {
        if (scheme == null) {
            return null;
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "mysql" -> DbEngine.MYSQL;
            case "mariadb" -> DbEngine.MARIADB;
            case "postgres", "postgresql" -> DbEngine.POSTGRES;
            default -> null;
        };
    }

    // ---- .env line parsing -----------------------------------------

    /** KEY=VALUE map per the class-javadoc rules; later duplicates win. */
    static Map<String, String> parse(String content) {
        Map<String, String> env = new LinkedHashMap<>();
        if (content == null) {
            return env;
        }
        for (String rawLine : content.split("\r?\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = unquote(line.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                env.put(key, value);
            }
        }
        return env;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return s; // a stray % — take the text as written
        }
    }

    private static String stripLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static int parsePort(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
