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
 * A layout names its sides for the reader, not for the screen (3.5.3).
 *
 * <p>Swing has two vocabularies for one idea. {@code FlowLayout.LEFT},
 * {@code BorderLayout.WEST}, {@code BoxLayout.X_AXIS} and
 * {@code GridBagConstraints.WEST} name sides of the SCREEN and stay there
 * when a window mirrors. {@code LEADING}, {@code LINE_START},
 * {@code LINE_AXIS} name where the reader's line starts, and are the same
 * thing for a left-to-right reader. Orientation reaches only the second
 * kind: the Hebrew and Arabic builds mirrored their text and their menus
 * while 107 toolbars, sidebars and rows across 36 files stayed where
 * English had put them, the Workbench's rows among them, title on the left
 * and subtitle after it under a right-aligned heading.
 *
 * <p>The population is every main source file of every module, so a new
 * window fails here on the commit that adds a screen side. Painted surfaces
 * are not exempt: they keep their own direction through their orientation,
 * and a reader-relative constant resolves to the authored side there.
 * A margin written as four numbers with different left and right is the
 * same defect ({@code core.util.LeadingBorder} names them for the reader).
 * A line that must name a screen side says why, with
 * {@code // SCREEN-SIDE: reason} on the line.
 */
class ReaderSidesGateTest {

    private static final Pattern SCREEN_SIDE = Pattern.compile(
            "\\bBoxLayout\\.[XY]_AXIS\\b"
            + "|\\bFlowLayout\\.(LEFT|RIGHT)\\b"
            + "|\\bBorderLayout\\.(WEST|EAST)\\b"
            + "|\\bGridBagConstraints\\.(WEST|EAST|NORTHWEST|NORTHEAST|SOUTHWEST|SOUTHEAST)\\b"
            + "|\\bSwingConstants\\.(LEFT|RIGHT|WEST|EAST)\\b");

    private static final Pattern MARGIN = Pattern.compile(
            "(?:createEmptyBorder|new (?:javax\\.swing\\.border\\.)?EmptyBorder)"
            + "\\(\\s*\\d+\\s*,\\s*(\\d+)\\s*,\\s*\\d+\\s*,\\s*(\\d+)\\s*\\)");

    private static final String BLESSING = "// SCREEN-SIDE:";

    static List<String> offenders(Path repo) throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : List.of("core", "editor", "tools", "rack", "project",
                "ui", "infra", "apiclient", "dbstudio", "web3")) {
            Path src = repo.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String raw = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    String[] rawLines = raw.split("\n", -1);
                    String[] code = GateSources.stripComments(raw).split("\n", -1);
                    for (int i = 0; i < Math.min(code.length, rawLines.length); i++) {
                        if (rawLines[i].contains(BLESSING)) {
                            continue;
                        }
                        // a trailing comment is prose too
                        String line = code[i];
                        int slashes = line.indexOf("//");
                        if (slashes >= 0) {
                            line = line.substring(0, slashes);
                        }
                        Matcher side = SCREEN_SIDE.matcher(line);
                        if (side.find()) {
                            offenders.add(module + "/" + p.getFileName() + ":" + (i + 1) + " " + side.group());
                        }
                        Matcher margin = MARGIN.matcher(line);
                        while (margin.find()) {
                            if (!margin.group(1).equals(margin.group(2))) {
                                offenders.add(module + "/" + p.getFileName() + ":" + (i + 1) + " " + margin.group());
                            }
                        }
                    }
                }
            }
        }
        return offenders;
    }

    @Test
    @DisplayName("no layout in the product names a side of the screen")
    void sidesAreTheReaders() throws IOException {
        assertThat(offenders(Path.of("..")))
                .as("a screen side stays put when the window mirrors for Hebrew and Arabic: "
                        + "LEADING/TRAILING, LINE_START/LINE_END, LINE_AXIS/PAGE_AXIS, "
                        + "FIRST_LINE_START, core.util.LeadingBorder; or say why with " + BLESSING)
                .isEmpty();
    }

    @Test
    @DisplayName("the gate sees each kind it is here for, in code and not in prose")
    void theGateBites(@org.junit.jupiter.api.io.TempDir Path repo) throws IOException {
        Path dir = Files.createDirectories(repo.resolve("ui/src/main/java/x"));
        Files.writeString(dir.resolve("A.java"), String.join("\n",
                "class A {",
                "  // FlowLayout.LEFT in a comment is prose",
                "  /* BorderLayout.WEST in a block",
                "     BoxLayout.X_AXIS too */",
                "  Object a = new java.awt.FlowLayout(java.awt.FlowLayout.LEFT);",
                "  String b = java.awt.BorderLayout.EAST;",
                "  int c = javax.swing.BoxLayout.Y_AXIS;",
                "  int d = java.awt.GridBagConstraints.NORTHWEST;",
                "  Object e = javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 0);",
                "  Object f = javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8);",
                "  int g = javax.swing.SwingConstants.RIGHT; // SCREEN-SIDE: a ruler's zero is at the screen's left",
                "  Object h = new java.awt.FlowLayout(java.awt.FlowLayout.LEADING); // was FlowLayout.LEFT",
                "}"));

        assertThat(offenders(repo)).containsExactly(
                "ui/A.java:5 FlowLayout.LEFT",
                "ui/A.java:6 BorderLayout.EAST",
                "ui/A.java:7 BoxLayout.Y_AXIS",
                "ui/A.java:8 GridBagConstraints.NORTHWEST",
                "ui/A.java:9 createEmptyBorder(0, 8, 0, 0)");
    }
}
