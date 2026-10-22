package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one script a folder with no project file would obviously run (3.8.0).
 *
 * <p>A newcomer's first folder is often not a project at all: a {@code hello.py},
 * an {@code app.js}, a {@code main.go} beside a README. Detection says NONE for
 * it — no manifest, no toolchain — and until 3.8.0 the IDE stopped there: the
 * toolbar ▶ stayed grey and silent, the Workbench read "no toolchain yet", and
 * the rack mounted the bare MONITOR. The shelf held the device that could run
 * the file (SOLDER, "run anything"), and nothing pointed at it. Walked on
 * 2026-10-22 in a throwaway home: a VS Code switcher with a script folder had
 * no door, because "run this file" is a gesture VS Code has and this product
 * only had for projects.
 *
 * <p>This is the sniff behind the {@code script} starter rack: read the
 * folder's ROOT entries by name (one {@code File.list()}, no walk, no stat
 * beyond {@code isFile} on the candidates — the v1.234.0 TCC rule, since
 * {@code ~/NMOX} and a home directory both reach here), keep the files whose
 * extension names an interpreter this product already speaks, and answer only
 * when the choice is not a guess: exactly one such file, or exactly one whose
 * stem is a conventional entry name ({@code main}, {@code app}, {@code index},
 * {@code server}, {@code run}, {@code start}). Two {@code main.*} files, or
 * three scripts with no conventional name, answer nothing and the rack stays
 * bare — guessing {@code node index.js} for a folder that also holds
 * {@code main.py} is exactly the lie the kind vocabulary exists to stop
 * telling.
 *
 * <p>The interpreters are the spellings IGNITION's lanes already use
 * ({@code python3}, {@code node}, {@code go run}, {@code ruby}, {@code php}),
 * one vocabulary, plus {@code sh} for a shell script and Node's own
 * {@code --experimental-strip-types} for a lone {@code .ts} (v2.69.0: one
 * argv for every Node since 22.6). A file name holding whitespace is quoted
 * the way SOLDER's own tokenizer reads it; one holding a quote character is
 * refused, since no quoting makes it inert there.
 */
public final class ScriptEntry {

    /** extension → the argv that runs a file of that kind, the file appended last. */
    static final Map<String, List<String>> INTERPRETERS = Map.ofEntries(
            Map.entry("py", List.of("python3")),
            Map.entry("js", List.of("node")),
            Map.entry("mjs", List.of("node")),
            Map.entry("cjs", List.of("node")),
            Map.entry("ts", List.of("node", "--experimental-strip-types")),
            Map.entry("mts", List.of("node", "--experimental-strip-types")),
            Map.entry("go", List.of("go", "run")),
            Map.entry("rb", List.of("ruby")),
            Map.entry("php", List.of("php")),
            Map.entry("sh", List.of("sh")));

    /** Stems that name an entry point by convention; one of these beats any number of others. */
    static final Set<String> CONVENTIONAL = Set.of("main", "app", "index", "server", "run", "start");

    /**
     * A root listing past this many entries is not a script folder — a
     * download dump, a photo library — and is not read further (bounded, the
     * v2.180.0 law: nobody asked for this read, the aim did).
     */
    static final int MAX_ENTRIES = 500;

    /** The script and the command line SOLDER runs it with. */
    public record Entry(String file, String command) {
    }

    private ScriptEntry() {
    }

    /** The one obvious script at {@code dir}'s root, or empty when there is none or the choice would be a guess. */
    public static Optional<Entry> find(File dir) {
        String[] names = dir == null ? null : dir.list();
        if (names == null || names.length > MAX_ENTRIES) {
            return Optional.empty();
        }
        return choose(Arrays.asList(names), name -> new File(dir, name).isFile());
    }

    /**
     * The pure half: {@code names} are a root listing, {@code isFile} says which
     * of them are files (a {@code main.py} directory is not a script).
     */
    static Optional<Entry> choose(List<String> names, java.util.function.Predicate<String> isFile) {
        List<String> candidates = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(".") || interpreterFor(name) == null || !isFile.test(name)) {
                continue;
            }
            candidates.add(name);
        }
        candidates.sort(null);
        String chosen = null;
        if (candidates.size() == 1) {
            chosen = candidates.get(0);
        } else if (candidates.size() > 1) {
            List<String> conventional = candidates.stream().filter(n -> CONVENTIONAL.contains(stem(n))).toList();
            if (conventional.size() == 1) {
                chosen = conventional.get(0);
            }
        }
        if (chosen == null || chosen.indexOf('"') >= 0 || chosen.indexOf('\'') >= 0) {
            return Optional.empty();
        }
        List<String> argv = new ArrayList<>(interpreterFor(chosen));
        argv.add(quoteIfNeeded(chosen));
        return Optional.of(new Entry(chosen, String.join(" ", argv)));
    }

    /** The interpreter argv for {@code name}'s extension, or null when this product does not speak it. */
    static List<String> interpreterFor(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        return INTERPRETERS.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return (dot <= 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
    }

    /** SOLDER tokenizes on whitespace with quotes grouping, so a name holding whitespace rides inside double quotes. */
    private static String quoteIfNeeded(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                return "\"" + name + "\"";
            }
        }
        return name;
    }
}
