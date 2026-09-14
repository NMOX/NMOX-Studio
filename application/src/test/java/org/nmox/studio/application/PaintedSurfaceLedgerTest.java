package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every surface that paints itself has a decision about which way it runs.
 *
 * <p>Applying a component orientation sweeps an ordinary Swing tree and misses
 * every component that computes its own x coordinates — it simply never reads
 * the flag. So a right-to-left build mirrors the forms, trees, toolbars and
 * dialogs, and leaves eighteen painted surfaces exactly as authored. That is
 * correct for most of them and a debt for three, and the difference has to be
 * written down or a reader meets it as a bug.
 *
 * <p>The population is DERIVED from the shipping source — every class that
 * overrides {@code paintComponent} — so a painted surface added tomorrow fails
 * this gate until somebody decides about it. That is the v2.147.0 lesson: a
 * ledger whose population is hand-kept protects exactly the members whoever
 * wrote it happened to know.
 */
class PaintedSurfaceLedgerTest {

    private static final Path REPO = Path.of("..");

    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "project", "ui",
            "infra", "apiclient", "dbstudio", "web3");

    @Test
    @DisplayName("every self-painting component is classified geometry or owed")
    void everyPaintedSurfaceIsDecided() throws IOException {
        TreeSet<String> painted = census();
        assertThat(painted)
                .as("the census should find the painted surfaces we know about")
                .contains("RackDevice", "FlowCanvas", "MainWindow", "MinimapSideBar");

        String ledger = Files.readString(REPO.resolve(
                "ui/src/main/java/org/nmox/studio/ui/rtl/PaintedSurfaces.java"),
                StandardCharsets.UTF_8);

        List<String> undecided = new ArrayList<>();
        for (String name : painted) {
            if (!ledger.contains("\"" + name + "\"")) {
                undecided.add(name + ": paints itself, so an orientation sweep cannot "
                        + "reach it — classify it GEOMETRY (mirroring would break it), "
                        + "MIRRORS (it reads the orientation itself) "
                        + "or OWED (it should mirror and does not yet)");
            }
        }
        assertThat(undecided).as("painted surfaces nobody has decided about").isEmpty();

        // and the other direction: a ledger nobody prunes stops being read
        List<String> stale = new ArrayList<>();
        for (String quoted : quotedNames(ledger)) {
            if (!painted.contains(quoted)) {
                stale.add(quoted + ": classified here, but nothing by that name paints "
                        + "itself any more — drop the entry");
            }
        }
        assertThat(stale).as("ledger entries for surfaces that no longer paint").isEmpty();
    }

    @Test
    @DisplayName("a surface classified MIRRORS names no absolute side")
    void mirroredSurfacesUseLogicalSides() throws IOException {
        // A MIRRORS entry takes the surface out of the reset, so the sweep
        // reaches it — which is only right if the surface reads the
        // orientation. One BorderLayout.WEST left behind puts a Hebrew row's
        // title on the wrong side while the rest of the row mirrors: exactly
        // the half-mirror the reset existed to prevent.
        String ledger = Files.readString(REPO.resolve(
                "ui/src/main/java/org/nmox/studio/ui/rtl/PaintedSurfaces.java"),
                StandardCharsets.UTF_8);
        int from = ledger.indexOf("MIRRORS = Set.of(");
        assertThat(from).as("the ledger's MIRRORS set").isPositive();
        java.util.regex.Matcher names = java.util.regex.Pattern.compile("\"([A-Z]\\w+)\"")
                .matcher(ledger.substring(from, ledger.indexOf(';', from)));
        java.util.regex.Pattern absolute = java.util.regex.Pattern.compile(
                "BorderLayout\\.(WEST|EAST)\\b|FlowLayout\\.(LEFT|RIGHT)\\b"
                + "|SwingConstants\\.(LEFT|RIGHT)\\b|GridBagConstraints\\.(WEST|EAST|NORTHWEST|NORTHEAST|SOUTHWEST|SOUTHEAST)\\b");

        List<String> checked = new ArrayList<>();
        List<String> absoluteSides = new ArrayList<>();
        while (names.find()) {
            Path file = sourceOf(names.group(1));
            assertThat(file).as("source for %s", names.group(1)).isNotNull();
            checked.add(names.group(1));
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                java.util.regex.Matcher m = absolute.matcher(lines.get(i));
                if (m.find()) {
                    absoluteSides.add(file.getFileName() + ":" + (i + 1) + " — " + m.group());
                }
            }
        }
        assertThat(checked).as("the surfaces classified MIRRORS").isNotEmpty();
        assertThat(absoluteSides)
                .as("an absolute side in a surface the sweep now reaches — use LINE_START/LINE_END/LEADING/TRAILING")
                .isEmpty();
    }

    private static Path sourceOf(String simpleName) throws IOException {
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                java.util.Optional<Path> hit = files
                        .filter(f -> f.getFileName().toString().equals(simpleName + ".java"))
                        .findFirst();
                if (hit.isPresent()) {
                    return hit.get();
                }
            }
        }
        return null;
    }

    /** Every class in the shipping source that overrides {@code paintComponent}. */
    private static TreeSet<String> census() throws IOException {
        TreeSet<String> out = new TreeSet<>();
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    if (text.contains("protected void paintComponent")) {
                        String n = p.getFileName().toString();
                        out.add(n.substring(0, n.length() - ".java".length()));
                    }
                }
            }
        }
        return out;
    }

    /** The names the ledger classifies, read out of its own two sets. */
    private static List<String> quotedNames(String ledger) {
        List<String> out = new ArrayList<>();
        int from = ledger.indexOf("GEOMETRY = Set.of(");
        int to = ledger.indexOf("private PaintedSurfaces()");
        if (from < 0 || to < 0) {
            return out;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([A-Z]\\w+)\"")
                .matcher(ledger.substring(from, to));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
