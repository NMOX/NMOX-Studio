package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A library the product ships is compiled and tested at the version it ships.
 *
 * <p>3.4.0 added {@code com.formdev:flatlaf:3.2.5} to the ui module's test
 * classpath, so that {@code ToolbarKeyboardAccessTest} could prove a keyboard
 * repair under the look and feel the product runs. The assembled cluster
 * carries {@code flatlaf-3.7.2.jar}, brought by the platform's own
 * {@code libs-flatlaf} module — so the test proved the repair under a look and
 * feel no user runs, with a green build. It was found only because Dependabot
 * offered a bump from a number nobody recognised (3.4.1).
 *
 * <p>The version literal was the defect, a second home for a fact the platform
 * owns (v2.131.0's law, for a library rather than a plugin). The population
 * here is derived from the assembled cluster: every third-party jar under any
 * cluster's {@code modules/ext}. A module pom that names one of them at a
 * version the cluster does not carry fails, whichever library it is.
 *
 * <p>Runs with the packaged-app gates: the cluster exists only after
 * {@code package}.
 */
class ShippedLibraryVersionGateTest {

    private static final Path REPO = Path.of("..");
    private static final Path CLUSTER = Path.of("target", "nmoxstudio");

    /** {@code artifact-1.2.3.jar}: the version starts at the first hyphen followed by a digit. */
    private static final Pattern JAR = Pattern.compile("^(.+?)-(\\d[^/]*)\\.jar$");
    private static final Pattern DEPENDENCY = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);
    private static final Pattern ARTIFACT = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern VERSION = Pattern.compile("<version>([^<]+)</version>");
    private static final Pattern PROPERTY = Pattern.compile("<([A-Za-z0-9_.\\-]+)>([^<]+)</\\1>");
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

    /**
     * A pom that names a shipped library at another version ON PURPOSE, and
     * why. Each entry must still be a real difference, or it fails as stale.
     */
    static final Map<String, String> BLESSED = Map.of(
            "application/pom.xml:slf4j-api",
            "jgit imports org.slf4j [1.7.0,3.0.0) and is satisfied only by an slf4j-api the "
            + "assembly wraps as a BUNDLE under extra/modules; the platform's own 2.x jar sits in "
            + "an ext directory and exports nothing (v2.21.6). The 1.7 line is the pin Dependabot "
            + "is told to leave alone.");

    /** artifactId → the versions of it the assembled product carries. */
    static Map<String, Set<String>> shipped(Path cluster) throws IOException {
        Map<String, Set<String>> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(cluster)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                String path = p.toString().replace('\\', '/');
                if (!path.contains("/modules/ext/") || !path.endsWith(".jar")) {
                    continue;
                }
                Matcher m = JAR.matcher(p.getFileName().toString());
                if (m.matches()) {
                    out.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(m.group(2));
                }
            }
        }
        return out;
    }

    /** One problem per dependency a pom names at a version the product does not ship. */
    static List<String> problems(String pomName, String pom, Map<String, String> properties,
            Map<String, Set<String>> shipped) {
        List<String> problems = new ArrayList<>();
        Matcher d = DEPENDENCY.matcher(pom);
        while (d.find()) {
            Matcher a = ARTIFACT.matcher(d.group(1));
            Matcher v = VERSION.matcher(d.group(1));
            if (!a.find() || !v.find()) {
                continue;
            }
            String version = v.group(1).trim();
            if (version.startsWith("${") && version.endsWith("}")) {
                version = properties.getOrDefault(version.substring(2, version.length() - 1), version);
            }
            Set<String> carried = shipped.get(a.group(1).trim());
            if (carried != null && !carried.contains(version)) {
                problems.add(pomName + " names " + a.group(1).trim() + " " + version
                        + " but the product ships " + carried);
            }
        }
        return problems;
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Map<String, String> pomsByName() throws IOException {
        Map<String, String> poms = new LinkedHashMap<>();
        String root = read(REPO.resolve("pom.xml"));
        poms.put("pom.xml", root);
        Matcher m = MODULE.matcher(root);
        while (m.find()) {
            Path pom = REPO.resolve(m.group(1)).resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                poms.put(m.group(1) + "/pom.xml", read(pom));
            }
        }
        return poms;
    }

    private static Map<String, String> rootProperties() throws IOException {
        String root = read(REPO.resolve("pom.xml"));
        int from = root.indexOf("<properties>");
        int to = root.indexOf("</properties>");
        Map<String, String> properties = new LinkedHashMap<>();
        Matcher m = PROPERTY.matcher(root.substring(from, to));
        while (m.find()) {
            properties.put(m.group(1), m.group(2).trim());
        }
        return properties;
    }

    @Test
    @DisplayName("the cluster carries third-party jars, FlatLaf among them, so this gate has a population")
    void theClusterIsThePopulation() throws IOException {
        Map<String, Set<String>> shipped = shipped(CLUSTER);
        assertThat(shipped).as("third-party jars in the assembled product")
                .hasSizeGreaterThan(20)
                .containsKey("flatlaf");
    }

    @Test
    @DisplayName("no module pom names a shipped library at a version the product does not ship")
    void everyNamedVersionIsTheShippedOne() throws IOException {
        Map<String, Set<String>> shipped = shipped(CLUSTER);
        Map<String, String> properties = rootProperties();
        List<String> problems = new ArrayList<>();
        int poms = 0;
        for (Map.Entry<String, String> pom : pomsByName().entrySet()) {
            poms++;
            problems.addAll(problems(pom.getKey(), pom.getValue(), properties, shipped));
        }
        assertThat(poms).as("module poms read").isGreaterThan(8);
        List<String> unblessed = new ArrayList<>();
        Set<String> used = new TreeSet<>();
        for (String problem : problems) {
            // "<pom> names <artifact> <version> but ..."
            String[] words = problem.split(" ");
            String key = words[0] + ":" + words[2];
            if (BLESSED.containsKey(key)) {
                used.add(key);
            } else {
                unblessed.add(problem);
            }
        }
        assertThat(unblessed)
                .as("take the library from the platform module that ships it, or name the shipped version")
                .isEmpty();
        assertThat(used).as("a blessing nothing needs any more is stale: remove it")
                .containsExactlyInAnyOrderElementsOf(BLESSED.keySet());
        assertThat(BLESSED.values()).as("a blessing says why").allSatisfy(
                reason -> assertThat(reason.split(" ")).hasSizeGreaterThan(12));
    }

    @Test
    @DisplayName("the 3.4.0 pin is refused by name: FlatLaf 3.2.5 beside a cluster carrying 3.7.2")
    void theOldPinIsRefused() {
        String pom = "<dependency>\n<groupId>com.formdev</groupId>\n<artifactId>flatlaf</artifactId>\n"
                + "<version>3.2.5</version>\n<scope>test</scope>\n</dependency>";
        assertThat(problems("ui/pom.xml", pom, Map.of(), Map.of("flatlaf", Set.of("3.7.2"))))
                .containsExactly("ui/pom.xml names flatlaf 3.2.5 but the product ships [3.7.2]");
        assertThat(problems("ui/pom.xml", pom.replace("3.2.5", "${flatlaf.version}"),
                Map.of("flatlaf.version", "3.7.2"), Map.of("flatlaf", Set.of("3.7.2"))))
                .as("a property that resolves to the shipped version is fine").isEmpty();
        assertThat(problems("ui/pom.xml", pom, Map.of(), Map.of("jna", Set.of("5.14.0"))))
                .as("a library the product does not ship is not this gate's business").isEmpty();
    }
}
