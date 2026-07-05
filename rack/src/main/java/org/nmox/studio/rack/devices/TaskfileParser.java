package org.nmox.studio.rack.devices;

import java.util.ArrayList;
import java.util.List;

/**
 * Static task enumeration for the classic task runners: reads a
 * Gruntfile or gulpfile as TEXT and lists exactly the tasks the file
 * declares — instant, no node required, and no process spawned per
 * project change (grunt --help forks the world; this does not).
 *
 * <p>Accepted forms, all hand-scanned (no regular expressions, so no
 * backtracking risk):</p>
 * <ul>
 *   <li>Grunt: {@code grunt.registerTask('name', ...)} with either quote
 *       style, any argument count, and the paren-less CoffeeScript call
 *       ({@code grunt.registerTask 'name', [...]});
 *       {@code grunt.loadNpmTasks('grunt-contrib-uglify')} contributes
 *       the task the plugin registers ({@code uglify}).</li>
 *   <li>Gulp v3: {@code gulp.task('name', ...)}.</li>
 *   <li>Gulp v4 exports: {@code exports.name = ...} (which also covers
 *       {@code module.exports.name}) and {@code export const name}.</li>
 * </ul>
 * Garbage in produces an empty or partial list, never an exception.
 */
public final class TaskfileParser {

    private TaskfileParser() {
    }

    /**
     * Tasks a Gruntfile declares — registerTask names and loadNpmTasks
     * plugins — in file-declaration order.
     */
    public static List<String> gruntTasks(String source) {
        List<Hit> hits = new ArrayList<>();
        for (Hit hit : callArguments(source, "registerTask")) {
            hits.add(hit);
        }
        for (Hit hit : callArguments(source, "loadNpmTasks")) {
            hits.add(new Hit(hit.at(), pluginTaskName(hit.name())));
        }
        hits.sort(java.util.Comparator.comparingInt(Hit::at));
        List<String> tasks = new ArrayList<>();
        for (Hit hit : hits) {
            addOnce(tasks, hit.name());
        }
        return tasks;
    }

    /** Tasks a gulpfile declares: gulp.task names + v4 export names, in file order. */
    public static List<String> gulpTasks(String source) {
        List<Hit> hits = new ArrayList<>(callArguments(source, "gulp.task"));
        hits.addAll(exportedNames(source));
        hits.sort(java.util.Comparator.comparingInt(Hit::at));
        List<String> tasks = new ArrayList<>();
        for (Hit hit : hits) {
            addOnce(tasks, hit.name());
        }
        return tasks;
    }

    /** One declaration found in the source: where, and what it names. */
    private record Hit(int at, String name) {
    }

    /** grunt-contrib-uglify → uglify; grunt-shell → shell; other names pass through. */
    private static String pluginTaskName(String module) {
        if (module.startsWith("grunt-contrib-")) {
            return module.substring("grunt-contrib-".length());
        }
        if (module.startsWith("grunt-")) {
            return module.substring("grunt-".length());
        }
        return module;
    }

    /**
     * Every first string argument of calls to the named function, with
     * its position: {@code fn('x', ...)}, {@code fn("x")}, and the
     * paren-less CoffeeScript {@code fn 'x', ...}.
     */
    private static List<Hit> callArguments(String source, String function) {
        List<Hit> hits = new ArrayList<>();
        int i = 0;
        while ((i = source.indexOf(function, i)) >= 0) {
            // require a call site, not an identifier that merely ends with it
            if (i > 0 && isIdentifierChar(source.charAt(i - 1))
                    && source.charAt(i - 1) != '.') {
                i += function.length();
                continue;
            }
            int j = i + function.length();
            while (j < source.length() && isInlineSpace(source.charAt(j))) {
                j++;
            }
            if (j < source.length() && source.charAt(j) == '(') {
                j++;
                while (j < source.length() && isInlineSpace(source.charAt(j))) {
                    j++;
                }
            }
            String name = quotedStringAt(source, j);
            if (name != null && !name.isEmpty()) {
                hits.add(new Hit(i, name));
            }
            i = i + function.length();
        }
        return hits;
    }

    /** The quoted string starting exactly at index i, or null. */
    private static String quotedStringAt(String source, int i) {
        if (i >= source.length()) {
            return null;
        }
        char quote = source.charAt(i);
        if (quote != '\'' && quote != '"') {
            return null;
        }
        int close = source.indexOf(quote, i + 1);
        return close < 0 ? null : source.substring(i + 1, close);
    }

    /** Gulp v4: {@code exports.name =} and {@code export const name =}. */
    private static List<Hit> exportedNames(String source) {
        List<Hit> hits = new ArrayList<>();
        int i = 0;
        while ((i = source.indexOf("exports.", i)) >= 0) {
            int j = i + "exports.".length();
            int start = j;
            while (j < source.length() && isIdentifierChar(source.charAt(j))) {
                j++;
            }
            String name = source.substring(start, j);
            while (j < source.length() && isInlineSpace(source.charAt(j))) {
                j++;
            }
            if (!name.isEmpty() && j < source.length() && source.charAt(j) == '='
                    && (j + 1 >= source.length() || source.charAt(j + 1) != '=')) {
                hits.add(new Hit(i, name));
            }
            i = start;
        }
        i = 0;
        while ((i = source.indexOf("export const ", i)) >= 0) {
            int j = i + "export const ".length();
            int start = j;
            while (j < source.length() && isIdentifierChar(source.charAt(j))) {
                j++;
            }
            if (j > start) {
                hits.add(new Hit(i, source.substring(start, j)));
            }
            i = j;
        }
        return hits;
    }

    private static void addOnce(List<String> tasks, String name) {
        if (!name.isEmpty() && !tasks.contains(name)) {
            tasks.add(name);
        }
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    private static boolean isInlineSpace(char c) {
        return c == ' ' || c == '\t';
    }
}
