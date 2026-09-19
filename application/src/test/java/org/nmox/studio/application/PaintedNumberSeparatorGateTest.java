package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A surface that asks for a logical font must not paint a grouped number
 * (ledger 107's standing hazard).
 *
 * <p><b>What was measured.</b> On macOS, {@code Dialog} and {@code SansSerif}
 * advance U+202F — French's group separator on this JDK — by <b>0.00px</b> and
 * paint no ink. Not a box: the character simply disappears, so
 * {@code 1 234 567} paints as {@code 1234567}, pixel-identical to the same
 * digits with no separator at all, and {@code canDisplay} says true the whole
 * time. The chrome font FlatLaf builds ({@code Helvetica Neue} there, Segoe UI
 * on Windows) is correct, which is why the status line, dialogs and every
 * platform label are safe and ledger 107 decided NOT to override the JDK's
 * separator.
 *
 * <p><b>What is left standing.</b> Twenty-two sites in eight files ask for
 * {@code new Font(Font.SANS_SERIF, …)} instead of the chrome font — the
 * Welcome, the rack's faceplates, the icon forge, the Projects explorer and
 * four infra panels. None of them formats a grouped number today. The hazard
 * ledger 107 named is that <i>the next counted sentence painted on one of
 * those surfaces loses its separator under French, silently, on the
 * maintainer's own platform</i> — a defect with no wrong logic to spot, on a
 * surface no bundle gate watches, visible only to someone running the product
 * in French.
 *
 * <p>So it is gated structurally rather than fixed: this passes today and
 * fails on the commit that introduces the hazard, naming the file and line.
 * The fix at that point is that surface asking for the chrome font, not a
 * rewrite of the separator.
 *
 * <p><b>The population is derived</b>, never hand-kept: every shipping source
 * file that constructs one of the measured zero-advance logical families. That
 * is deliberately wider than the self-painting census {@link
 * PaintedSurfaceLedgerTest} derives — a {@code JLabel} given a
 * {@code Font.SANS_SERIF} font paints in it just as surely as a
 * {@code paintComponent} override does, and two of the eight files
 * ({@code MainWindow}, {@code ProjectExplorerTopComponent}) reach it that way.
 * {@code Serif} and {@code Monospaced} are left out because they were measured
 * clean (12.81px and 38.53px, no ink).
 *
 * <p><b>{@code Numbers.display} is not banned outright</b>, and that is a
 * measurement rather than an oversight: it formats with {@code "%.Nf"}, which
 * takes a decimal separator and no grouping one, so infra's money strings on
 * {@code FlowCanvas} are safe. The gate READS that fact from
 * {@code Numbers.java} rather than restating it, so the day {@code display}
 * grows a grouping flag, its callers on these surfaces fail here by name —
 * one fact, one home (v2.131.0).
 */
class PaintedNumberSeparatorGateTest {

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /**
     * The logical families measured at 0.00px advance on macOS. {@code Dialog}
     * is spelled both ways a caller can reach it.
     */
    private static final Pattern ZERO_ADVANCE_FONT = Pattern.compile(
            "new\\s+Font\\s*\\(\\s*(?:Font\\.SANS_SERIF|Font\\.DIALOG"
            + "|\"SansSerif\"|\"Dialog\")");

    /**
     * A construct that can emit the reader's grouping separator.
     *
     * <p>{@code NumberFormat\.} rather than a bare name on purpose: a
     * {@code catch (NumberFormatException e)} carries no dot and is not a
     * number site — {@code IconForge} has one, and it was the gate's first
     * false positive.
     */
    private static final Pattern GROUPS = Pattern.compile(
            "\"[^\"]*%[\\d$\\-#+ 0(]*,"          // the ',' format flag: %,d  %,.2f  %1$,d
            + "|\\bNumberFormat\\s*\\."          // every getInstance family groups by default
            + "|new\\s+DecimalFormat\\s*\\("
            + "|setGroupingUsed\\s*\\(\\s*true");

    /** Where {@code Numbers.display} lives — read, not restated. */
    private static final Path NUMBERS = Path.of("..", "core", "src", "main", "java",
            "org", "nmox", "studio", "core", "util", "Numbers.java");

    @Test
    @DisplayName("no surface built on a zero-advance logical font paints a grouped number")
    void logicalFontSurfacesPaintNoGroupedNumber() throws IOException {
        Pattern grouping = groupingPattern();

        TreeSet<String> population = new TreeSet<>();
        List<String> hazards = new ArrayList<>();
        for (Path p : sources()) {
            String code = GateSources.stripComments(read(p));
            if (!ZERO_ADVANCE_FONT.matcher(code).find()) {
                continue;
            }
            population.add(p.getFileName().toString());
            String[] lines = code.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                Matcher m = grouping.matcher(lines[i]);
                if (m.find()) {
                    hazards.add(p.getFileName() + ":" + (i + 1) + " — '" + m.group().strip()
                            + "' on a surface that asks for a logical font. Ledger 107: "
                            + "macOS advances U+202F by 0.00px in Dialog/SansSerif, so under "
                            + "French this number loses its group separator on screen with "
                            + "no box and no warning. Paint it in the chrome font instead "
                            + "(the component's own getFont(), or the UIManager key), which "
                            + "was measured correct on every platform.");
                }
            }
        }

        assertThat(population)
                .as("the census should find the surfaces ledger 107 measured")
                .contains("RackStyle.java", "MainWindow.java", "FlowCanvas.java");
        assertThat(population)
                .as("ledger 107 counted eight files; a census that suddenly finds one or two "
                        + "is reading the wrong thing, and would pass forever")
                .hasSizeGreaterThanOrEqualTo(6);
        assertThat(hazards).as("grouped numbers on a logical-font surface").isEmpty();
    }

    @Test
    @DisplayName("the census can see — both patterns proven on planted lines")
    void theCensusIsNotBlind() {
        assertThat(ZERO_ADVANCE_FONT.matcher(
                "static final Font F = new Font(Font.SANS_SERIF, Font.BOLD, 10);").find())
                .as("the shape of all twenty-two sites").isTrue();
        assertThat(ZERO_ADVANCE_FONT.matcher(
                "g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));").find())
                .as("Monospaced advances 38.53px with no ink — measured clean, left out")
                .isFalse();

        assertThat(GROUPS.matcher("s = String.format(\"%,d files\", n);").find())
                .as("the grouping flag is the whole subject").isTrue();
        assertThat(GROUPS.matcher("s = String.format(\"%,.2f\", usd);").find())
                .as("the flag can precede a precision").isTrue();
        assertThat(GROUPS.matcher("s = NumberFormat.getIntegerInstance().format(n);").find())
                .as("every getInstance family groups by default").isTrue();
        assertThat(GROUPS.matcher("} catch (NumberFormatException ex) {").find())
                .as("a parse failure is not a number site — IconForge has one, and it was "
                        + "this gate's first false positive")
                .isFalse();
        assertThat(GROUPS.matcher("s = String.format(\"%.2f\", usd);").find())
                .as("a decimal separator is not a grouping separator — that is exactly why "
                        + "Numbers.display is safe on these surfaces today")
                .isFalse();
    }

    @Test
    @DisplayName("Numbers.display is read, not assumed — it carries no grouping flag today")
    void numbersDisplayStillDoesNotGroup() throws IOException {
        assertThat(displayGroups())
                .as("if display ever formats with the ',' flag, every Numbers.display call "
                        + "on a logical-font surface becomes the ledger-107 hazard — the "
                        + "gate above then names them, which is why this fact is read from "
                        + "Numbers.java rather than restated here")
                .isFalse();
    }

    /**
     * The hazard pattern for this build: the constructs that always group,
     * plus {@code Numbers.display} only while its own source says it groups.
     */
    private static Pattern groupingPattern() throws IOException {
        return displayGroups()
                ? Pattern.compile(GROUPS.pattern() + "|\\bNumbers\\s*\\.\\s*display\\s*\\(")
                : GROUPS;
    }

    /** True when {@code Numbers.display}'s own format string carries the {@code ,} flag. */
    private static boolean displayGroups() throws IOException {
        String src = GateSources.stripComments(read(NUMBERS));
        int from = src.indexOf("String display(");
        assertThat(from).as("Numbers.display must still exist for this gate to read it")
                .isPositive();
        int to = src.indexOf("String stable(", from);
        return src.substring(from, to > from ? to : src.length()).contains("\"%,");
    }

    /** CRLF-normalized: the Windows lane checks these files out with \r\n. */
    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                out.addAll(files.filter(f -> f.toString().endsWith(".java")).toList());
            }
        }
        return out;
    }
}
