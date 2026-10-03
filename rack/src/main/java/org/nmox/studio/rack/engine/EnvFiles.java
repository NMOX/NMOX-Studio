package org.nmox.studio.rack.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the project's dotenv files so every command the rack launches
 * sees the same environment the developer's own tooling does. The
 * precedence mirrors the ecosystem convention: {@code .env} is the
 * base, {@code .env.local} wins over it, and anything dialed on the
 * rack (ATMOS, per-launch extras) wins over both.
 *
 * A plain KEY=VALUE reader: comments and blanks skipped, an optional
 * leading {@code export } stripped, matched surrounding quotes removed.
 * No interpolation, no escapes - if a project needs those semantics its
 * own tooling applies them; the rack must not invent a dialect.
 */
public final class EnvFiles {

    /** Files bigger than this are config mistakes, not env files. */
    private static final long MAX_BYTES = 256 * 1024;

    private EnvFiles() {
    }

    /** {@code .env} overlaid with {@code .env.local}; empty if neither exists. */
    public static Map<String, String> load(File dir) {
        Map<String, String> env = new LinkedHashMap<>();
        if (dir == null) {
            return env;
        }
        env.putAll(parse(new File(dir, ".env")));
        env.putAll(parse(new File(dir, ".env.local")));
        return env;
    }

    static Map<String, String> parse(File file) {
        if (!file.isFile() || file.length() > MAX_BYTES) {
            return new LinkedHashMap<>();
        }
        try {
            return parseLines(Files.readAllLines(file.toPath()));
        } catch (IOException ex) {
            // unreadable env file: commands still run, just without it
            return new LinkedHashMap<>();
        }
    }

    /**
     * The dialect itself, over lines somebody else read: the one reader of
     * KEY=VALUE text in the product. A caller with its own rule about the
     * FILE - a {@code .vscode/launch.json} configuration's {@code envFile},
     * which is refused out loud when it is missing or too large rather than
     * skipped - reads the file its own way and hands the lines here, so what
     * a line means has one home.
     */
    public static Map<String, String> parseLines(Iterable<String> lines) {
        Map<String, String> env = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            if (key.isEmpty() || key.chars().anyMatch(Character::isWhitespace)) {
                continue;
            }
            env.put(key, unquote(line.substring(eq + 1).strip()));
        }
        return env;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
