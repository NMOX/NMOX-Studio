package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seed data is chrome until the user edits it.
 *
 * <p>A German walk found a fully translated Task Board whose three column
 * headers read To Do / Doing / Done, and a fully translated API Studio whose
 * first-run collection, request and environment read My API / Health check /
 * Local. Every l10n gate was green, because the English never passed through
 * a bundle at all: it was string literals inside two model classes.
 *
 * <p>That is the ledger-88 shape one layer over — English reaching a
 * translated build through a path BELOW the gates — and the answer is the
 * same one: the core returns data, the consumer renders it (v2.101.0). Each
 * seed factory now takes its names as parameters, and this gate keeps them
 * parameters: a literal at a seed call site in main sources is the defect
 * itself, spelled out.
 *
 * <p>Tests may pass literals, and should — a test pins behaviour, not
 * chrome, and a German assertion about column ORDER would be unreadable.
 */
class SeedNamesAreNotLiteralsTest {

    private static final Path REPO = Path.of("..");

    private static final List<String> SOURCE_MODULES = List.of(
            "core", "editor", "tools", "rack", "project", "ui", "apiclient", "dbstudio", "web3", "infra");

    /** The seed factories whose names are the product speaking. */
    private static final List<String> SEEDS = List.of("TaskBoard.starter(", "Workspace.starter(");

    /** A double-quoted string anywhere in the call's arguments. */
    private static final Pattern LITERAL = Pattern.compile("\"[^\"]*\"");

    @Test
    @DisplayName("no main source names a seed with a literal — the names come from a bundle")
    void seedCallsCarryNoProse() throws IOException {
        List<String> literals = new ArrayList<>();
        int calls = 0;
        for (String module : SOURCE_MODULES) {
            Path src = REPO.resolve(module).resolve("src").resolve("main");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path java : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(java, StandardCharsets.UTF_8);
                    for (String seed : SEEDS) {
                        int at = -1;
                        while ((at = body.indexOf(seed, at + 1)) >= 0) {
                            // the QUALIFIED form is always a call: a
                            // declaration reads "static TaskBoard starter(",
                            // never "TaskBoard.starter(". An earlier cut
                            // skipped any hit whose line also held the word
                            // "static", which would have silently excused
                            // a static field initialized from a seed
                            calls++;
                            String args = argumentOf(body, at + seed.length());
                            Matcher m = LITERAL.matcher(args);
                            if (m.find()) {
                                literals.add(REPO.relativize(java) + ": " + seed + m.group()
                                        + "…) — a seeded name is chrome; read it from the bundle");
                            }
                        }
                    }
                }
            }
        }
        // exactly two, one per studio that seeds a first-run file: the Task
        // Board's and API Studio's. The floor is a tripwire for a rename —
        // if a seed factory moves and this drops to one, the gate is half
        // blind and says so rather than passing quietly
        assertThat(calls).as("the gate should find both seed call sites, "
                + "or it is watching a method nobody calls").isGreaterThanOrEqualTo(2);
        assertThat(literals).as("seed names spelled as literals in shipping code").isEmpty();
    }

    /** The argument text of a call whose open paren has just been consumed, to its matching close. */
    private static String argumentOf(String body, int from) {
        int depth = 1;
        int i = from;
        while (i < body.length() && depth > 0) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            i++;
        }
        return body.substring(from, Math.max(from, i - 1));
    }
}
