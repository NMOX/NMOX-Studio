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
 * The JaCoCo floor stands down when the tests do (3.2.0).
 *
 * <p>CONTRIBUTING tells a contributor to type {@code mvn -o install
 * -DskipTests} "every time after" the first build, and after any focused
 * {@code -Dtest=X} run that command failed: the tests were skipped, so the
 * coverage-floor check read the {@code jacoco.exec} the focused run had left
 * behind, measured one class's worth of coverage against a module floor, and
 * failed the build. The root pom now derives {@code jacoco.skip} from
 * {@code skipTests}; this holds the derivation, its default, and that nothing
 * downstream re-decides it in a way that would quietly win.
 */
class CoverageFloorGateTest {

    private static final Path REPO = Path.of("..");

    private static String rootProperties() throws IOException {
        String pom = Files.readString(REPO.resolve("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<properties>(.*?)</properties>", Pattern.DOTALL).matcher(pom);
        assertThat(m.find()).as("the root pom has a properties block").isTrue();
        return m.group(1).replaceAll("(?s)<!--.*?-->", "");
    }

    @Test
    @DisplayName("jacoco.skip follows skipTests in the root pom, and skipTests defaults to false")
    void theRootDerivesTheSkip() throws IOException {
        String props = rootProperties();
        assertThat(props).as("tests run unless asked not to, so the floor runs with them")
                .contains("<skipTests>false</skipTests>");
        assertThat(props).as("the floor stands down with the tests")
                .contains("<jacoco.skip>${skipTests}</jacoco.skip>");
    }

    @Test
    @DisplayName("no module pom decides jacoco.skip or a JaCoCo <skip> of its own")
    void noModuleOverridesTheDerivation() throws IOException {
        List<String> found = new ArrayList<>();
        Pattern prop = Pattern.compile("<jacoco\\.skip>");
        Pattern plugin = Pattern.compile("<artifactId>jacoco-maven-plugin</artifactId>(.*?)</plugin>", Pattern.DOTALL);
        try (Stream<Path> dirs = Files.list(REPO)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                Path pom = dir.resolve("pom.xml");
                if (!Files.isRegularFile(pom)) {
                    continue;
                }
                String body = Files.readString(pom, StandardCharsets.UTF_8).replaceAll("(?s)<!--.*?-->", "");
                if (prop.matcher(body).find()) {
                    found.add(REPO.relativize(pom) + " sets jacoco.skip");
                }
                Matcher m = plugin.matcher(body);
                while (m.find()) {
                    if (m.group(1).contains("<skip>")) {
                        found.add(REPO.relativize(pom) + " configures a JaCoCo <skip>");
                    }
                }
            }
        }
        assertThat(found).as("a second home for the decision would override the root's").isEmpty();
    }
}
