package org.nmox.studio.rack.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.GateSources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test that swaps a production seam puts it back through the seam's own
 * reset, never through a value of its own.
 *
 * <p>The incident: {@code AsyncExecTest} restored {@code RackDevice.execLane}
 * by writing a lane itself — {@code RequestProcessor.getDefault()::post} —
 * under a comment reading "back to a real async lane". It <i>is</i> a real
 * async lane. It is not the production one, which is the dedicated eight-wide
 * {@code nmox-device-exec}. Surefire reuses one fork per module, so every rack
 * test that ran after it spawned devices on the platform's shared processor,
 * and nothing anywhere said so.
 *
 * <p>The same shape is a security problem one seam over: six classes each kept
 * their own capture-and-restore of {@code CommandDevice.trustCheck}, which is
 * the gate between a cloned repository and a spawned process. Six correct
 * copies are six chances to forget, and a forgotten one leaks
 * {@code f -> true} into every later test while the build stays green.
 *
 * <p>So the population is <b>derived</b>: every {@code reset<Name>()} in the
 * module's own sources declares a seam, and every test that writes that seam
 * must call it. A seam that gains a reset tomorrow is covered tomorrow, with
 * no list to keep.
 */
class SeamRestoreGateTest {

    private static final String SELF = "SeamRestoreGateTest.java";

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TEST = Path.of("src/test/java");

    /** {@code static void resetFooBar()} whose body assigns {@code fooBar}. */
    private static final Pattern RESET = Pattern.compile(
            "static\\s+void\\s+reset([A-Z]\\w*)\\s*\\(\\s*\\)\\s*\\{([^}]*)}", Pattern.DOTALL);

    private static List<Path> javaUnder(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path p) throws IOException {
        return GateSources.stripComments(
                Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    /** seam field name -> the class that declares its reset. */
    private static Map<String, String> seams() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        for (Path p : javaUnder(MAIN)) {
            String src = read(p);
            Matcher m = RESET.matcher(src);
            while (m.find()) {
                String field = Character.toLowerCase(m.group(1).charAt(0)) + m.group(1).substring(1);
                if (m.group(2).contains(field + " =")) { // the reset really assigns its seam
                    found.put(field, p.getFileName().toString().replace(".java", ""));
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("the seams this module declares a reset for are found, so the gate below is not vacuously green")
    void thePopulationIsNotEmpty() throws IOException {
        assertThat(seams())
                .as("a reset method names a seam; none found means the pattern stopped matching, "
                        + "and a gate over an empty population proves nothing")
                .containsKeys("execLane", "trustCheck");
    }

    @Test
    @DisplayName("every test that writes a seam restores it through that seam's own reset")
    void everyTestThatWritesASeamRestoresThroughItsReset() throws IOException {
        Map<String, String> seams = seams();
        List<String> offenders = new ArrayList<>();
        for (Path p : javaUnder(TEST)) {
            String src = read(p);
            String name = p.getFileName().toString();
            for (var seam : seams.entrySet()) {
                String owner = seam.getValue();
                String field = seam.getKey();
                boolean writes = src.contains(owner + "." + field + " =");
                if (!writes) {
                    continue;
                }
                String reset = owner + ".reset"
                        + Character.toUpperCase(field.charAt(0)) + field.substring(1) + "()";
                if (!src.contains(reset)) {
                    offenders.add(name + " writes " + owner + "." + field
                            + " and never calls " + reset);
                }
            }
        }
        assertThat(offenders)
                .as("restore through the seam's reset — a hand-written restore is a second home "
                        + "for the production default, and it has already drifted once")
                .isEmpty();
    }

    @Test
    @DisplayName("no test spells a production default itself — that is how the exec lane drifted, with a comment claiming it had not")
    void noTestInventsAProductionDefault() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : javaUnder(TEST)) {
            if (p.getFileName().toString().equals(SELF)) {
                // this gate names the forbidden spellings in order to look for
                // them, so it matches itself. Stripping comments is not enough
                // — they are string literals in code. The same class has now
                // been paid for three times: a gate a comment could satisfy
                // (v2.178.0), a gate a javadoc could trip (v2.182.0), and a
                // gate that is its own subject (here).
                continue;
            }
            String src = read(p);
            // the two shapes that mean "I am putting this back myself"
            if (src.contains("execLane = ") && src.contains("RequestProcessor.getDefault()")) {
                offenders.add(p.getFileName() + ": restores execLane with a lane of its own");
            }
            if (src.contains("trustCheck = ") && src.contains("WorkspaceTrust::requestTrust")) {
                offenders.add(p.getFileName() + ": restores trustCheck with the production prompt spelled here");
            }
        }
        assertThat(offenders).as("the production default has one home: the seam's reset").isEmpty();
    }
}
