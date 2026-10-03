package org.nmox.studio.tools.vscode;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.rack.engine.EnvFiles;

/**
 * A launch configuration's {@code envFile}, read: the variables a dotenv
 * file adds to the program's environment. Pure but for the one bounded
 * read; nothing here spawns, and nothing here ever prints a VALUE — a
 * refusal names the file and a line number, because the values are what an
 * env file exists to keep out of sight.
 *
 * <p><b>One parser, and a guard in front of it.</b> What a line means is
 * {@link EnvFiles#parseLines}'s to say — the product's one dotenv reader,
 * the same one that feeds every command the rack runs: {@code NAME=value},
 * {@code #} comment lines, matched quotes removed, no escapes and no
 * interpolation. VS Code does not have one dialect either: its Node
 * debugger reads an env file with the {@code dotenv} package (16.4.1 in the
 * adapter this IDE ships — an unquoted value ends at the first {@code #},
 * {@code \n} becomes a newline inside double quotes, back-ticks quote) and
 * its Python debugger with a reader of its own (no {@code export}, {@code
 * ${NAME}} replaced by an earlier variable). On the plain lines every
 * {@code .env} is mostly made of, all three agree. On the others they do
 * not, and a program started with a value VS Code would have read
 * differently is a different program from the one the file describes — so
 * {@link #firstUnplainLine} finds the first such line and the
 * configuration is refused naming it, rather than debugged with an
 * environment that is nearly right.
 *
 * <p><b>The plain line.</b> {@code NAME=value}, optionally after {@code
 * export} (Node only), where NAME is letters, digits and underscores and
 * does not start with a digit, and the value is one of: nothing; unquoted
 * text that does not start with a quote or a back-tick and, for Node, holds
 * no {@code #}; or text inside one pair of matching single or double quotes
 * with no backslash and no further quote of that kind inside. For Python,
 * no value may hold a dollar sign followed by an opening brace. Blank
 * lines and lines starting {@code #} are skipped by everyone.
 *
 * <p><b>The file.</b> Read through {@link BoundedReads} under {@link
 * #MAX_BYTES} (the rack's own ceiling for an env file). A file too large or
 * unreadable is a refusal, not an empty environment: VS Code ignores an
 * {@code envFile} that is not there, and this IDE says so instead, because
 * the configuration asked for variables and a program run without them is
 * not the one it describes.
 */
final class VsCodeEnvFile {

    /** An honest env file is a few kilobytes; past this it is not an env file. */
    static final long MAX_BYTES = 256L * 1024;

    /** A variable name every reader accepts: VS Code's Python reader is the strictest. */
    private static final Pattern NAME = Pattern.compile("_*[A-Za-z][A-Za-z0-9_]*");

    private VsCodeEnvFile() {
    }

    /** What reading an env file came to. */
    sealed interface Result permits Loaded, Unreadable, Unplain {
    }

    /** The variables, by name. */
    record Loaded(Map<String, String> vars) implements Result {

        /** Names only: a value never reaches a log or a failure message through this record. */
        @Override
        public String toString() {
            return "Loaded" + vars.keySet();
        }
    }

    /** The file could not be read, or is over {@link #MAX_BYTES}. */
    record Unreadable() implements Result {
    }

    /** Line {@code line} (1-based) is one VS Code would read differently. */
    record Unplain(int line) implements Result {
    }

    /**
     * The variables of {@code file}, or why not.
     *
     * @param python true for a Python configuration, whose reader in VS Code
     *               differs from the Node one
     */
    static Result read(File file, boolean python) {
        String text;
        try {
            text = BoundedReads.read(file, MAX_BYTES);
        } catch (IOException tooLargeOrUnreadable) {
            return new Unreadable();
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<String> lines = text.lines().toList();
        int unplain = firstUnplainLine(lines, python);
        if (unplain > 0) {
            return new Unplain(unplain);
        }
        return new Loaded(Collections.unmodifiableMap(new TreeMap<>(EnvFiles.parseLines(lines))));
    }

    /** The 1-based number of the first line that is not a plain one, or 0. */
    static int firstUnplainLine(List<String> lines, boolean python) {
        for (int i = 0; i < lines.size(); i++) {
            if (!plain(lines.get(i), python)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Whether every reader — ours, VS Code's Node one, VS Code's Python one — reads {@code raw} alike. */
    static boolean plain(String raw, boolean python) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return true;
        }
        if (line.startsWith("export ")) {
            if (python) {
                return false;
            }
            line = line.substring("export ".length()).strip();
        }
        int eq = line.indexOf('=');
        if (eq <= 0 || !NAME.matcher(line.substring(0, eq).strip()).matches()) {
            return false;
        }
        String value = line.substring(eq + 1).strip();
        if (python && value.contains("${")) {
            return false;
        }
        if (value.isEmpty()) {
            return true;
        }
        char first = value.charAt(0);
        if (first == '`') {
            return false;
        }
        if (first == '"' || first == '\'') {
            if (value.length() < 2 || value.charAt(value.length() - 1) != first) {
                return false;
            }
            String inner = value.substring(1, value.length() - 1);
            return inner.indexOf(first) < 0 && inner.indexOf('\\') < 0;
        }
        return python || value.indexOf('#') < 0;
    }
}
