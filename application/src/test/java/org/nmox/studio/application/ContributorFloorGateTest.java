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
 * The build JDK is stated once and agreed on everywhere a contributor or a
 * CI runner meets it (v3.1.0).
 *
 * <p>Before this, nothing enforced JDK 25. A clone built on JDK 21 failed in
 * the ui module as hundreds of {@code cannot find symbol: variable Bundle}
 * lines, because OpenJFX 26's jars are class-file 68 and javac abandons
 * annotation processing after reporting that once. {@code build.sh} checked
 * for Java 17, {@code setup-and-run.sh} looked for 23 or 17, and the CI step
 * that installs JDK 25 was named "Set up JDK 21". Four places, four answers.
 *
 * <p>The root pom's {@code maven-enforcer-plugin} rule is the one home. This
 * gate holds the rest to it: every CI {@code setup-java} installs at least that
 * JDK, no step's name claims a different one, and {@code build.sh} refuses
 * below the same floor. Measured when the rule landed: on Temurin 21.0.4,
 * {@code mvn -o validate} fails at the root project naming the JVM and where
 * to get JDK 25.
 */
class ContributorFloorGateTest {

    private static final Path REPO = Path.of("..");

    private static final Pattern RANGE = Pattern.compile(
            "<requireJavaVersion>\\s*<version>\\[(\\d+),\\)</version>");

    @Test
    @DisplayName("the root pom enforces the build JDK at validate, and says why in its message")
    void thePomEnforcesTheFloor() throws IOException {
        String pom = read(REPO.resolve("pom.xml"));
        int floor = floor(pom);
        assertThat(floor).as("the build JDK floor (OpenJFX 26 jars are class-file 68 = JDK 24+)")
                .isGreaterThanOrEqualTo(24);
        int build = pom.indexOf("<id>enforce-build-jdk</id>");
        int pm = pom.indexOf("</pluginManagement>");
        assertThat(build).as("the enforce execution is bound in <build><plugins>, not only managed")
                .isGreaterThan(pm);
        String execution = pom.substring(build, pom.indexOf("</execution>", build));
        assertThat(execution).contains("<phase>validate</phase>").contains("<goal>enforce</goal>")
                .contains("cannot find symbol").contains("JAVA_HOME")
                .contains("<requireMavenVersion>");
    }

    @Test
    @DisplayName("every CI setup-java installs at least the enforced JDK, and no step name says otherwise")
    void ciAgrees() throws IOException {
        int floor = floor(read(REPO.resolve("pom.xml")));
        List<String> problems = new ArrayList<>();
        int installs = 0;
        try (Stream<Path> flows = Files.list(REPO.resolve(".github").resolve("workflows"))) {
            for (Path flow : flows.filter(p -> p.toString().endsWith(".yml")).sorted().toList()) {
                String name = flow.getFileName().toString();
                List<String> lines = List.of(read(flow).split("\n"));
                for (int i = 0; i < lines.size(); i++) {
                    Matcher v = Pattern.compile("java-version:\\s*['\"]?(\\d+)").matcher(lines.get(i));
                    if (v.find()) {
                        installs++;
                        if (Integer.parseInt(v.group(1)) < floor) {
                            problems.add(name + ":" + (i + 1) + " installs JDK " + v.group(1));
                        }
                    }
                    Matcher step = Pattern.compile("name:.*\\bJDK (\\d+)").matcher(lines.get(i));
                    if (step.find()) {
                        String says = step.group(1);
                        // the version the step's own with: block installs, a few lines on
                        for (int j = i + 1; j < Math.min(lines.size(), i + 8); j++) {
                            Matcher installed = Pattern.compile("java-version:\\s*['\"]?(\\d+)").matcher(lines.get(j));
                            if (installed.find()) {
                                if (!installed.group(1).equals(says)) {
                                    problems.add(name + ":" + (i + 1) + " is named JDK " + says
                                            + " and installs JDK " + installed.group(1));
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        assertThat(installs).as("setup-java steps found (a scan that finds none proves nothing)")
                .isGreaterThan(3);
        assertThat(problems).as("CI must build on the JDK the pom enforces").isEmpty();
    }

    @Test
    @DisplayName("build.sh refuses below the same floor, asking Maven which JVM it will use")
    void buildScriptAgrees() throws IOException {
        int floor = floor(read(REPO.resolve("pom.xml")));
        String script = read(REPO.resolve("build.sh"));
        assertThat(script).contains("mvn -v")
                .contains("-lt " + floor + " ]")
                .contains("mvn clean install -DskipTests");
    }

    private static int floor(String pom) {
        Matcher m = RANGE.matcher(pom);
        assertThat(m.find()).as("the root pom carries a requireJavaVersion [N,) range").isTrue();
        return Integer.parseInt(m.group(1));
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
