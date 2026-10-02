package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product carries one PostgreSQL driver, and it is the one DB Studio keeps
 * current.
 *
 * <p>DB Studio bundles pgJDBC and has moved it past every advisory since
 * v1.35.1. The platform's {@code db.drivers} module carries a pgJDBC of its
 * own — 42.5.4 in RELEASE310, named by CVE-2024-1597 and CVE-2026-42198 — and
 * registers it as the PostgreSQL driver of Window ▸ Services ▸ Databases, so a
 * connection made there, and every one DB Studio's Services bridge then ran,
 * loaded the old jar. Nothing in this repository names that jar, which is why
 * no bump and no advisory alert ever reached it; it was found by
 * {@link ShippedLibraryVersionGateTest}'s first run, which reported the
 * product shipping two versions of one library (3.4.1).
 *
 * <p>Held here on the assembled product: no platform cluster carries a pgJDBC
 * jar, DB Studio's layer hides the platform's entry and depends on the module
 * that owns it (a hide is only ordered above what it hides through a
 * dependency), the entry it registers names a jar that exists, and the
 * portable zip — which the nbm plugin makes before the assembled directory is
 * trimmed — loses the same file in the release workflow.
 *
 * <p>Runs with the packaged-app gates: the cluster exists only after
 * {@code package}.
 */
class OnePostgresDriverGateTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Path DBSTUDIO = CLUSTER.resolve(
            Path.of("nmoxstudio", "modules", "org-nmox-NMOX-Studio-dbstudio.jar"));
    private static final Pattern NBINST = Pattern.compile("nbinst://([^/\"]+)/([^\"]+)\"");

    private static String entry(Path jar, String name) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry e = jf.getEntry(name);
            assertThat(e).as("%s in %s", name, jar.getFileName()).isNotNull();
            try (InputStream in = jf.getInputStream(e)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            }
        }
    }

    @Test
    @DisplayName("no platform cluster carries a pgJDBC jar: the bundled driver is the only one")
    void thePlatformsDriverJarIsGone() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(CLUSTER)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                String name = p.getFileName().toString();
                if (name.startsWith("postgresql") && name.endsWith(".jar")) {
                    found.add(CLUSTER.relativize(p).toString().replace('\\', '/'));
                }
            }
        }
        assertThat(found).as("pgJDBC jars in the assembled product").containsExactly(
                "nmoxstudio/modules/ext/org.nmox.NMOX-Studio-dbstudio/org-postgresql/postgresql.jar");
    }

    @Test
    @DisplayName("DB Studio's layer hides the platform's PostgreSQL entries and registers its own")
    void theLayerReplacesThePlatformsEntry() throws IOException {
        String layer = entry(DBSTUDIO, "org/nmox/studio/dbstudio/layer.xml");
        assertThat(layer).contains("<file name=\"postgresql.xml_hidden\"/>")
                .contains("<file name=\"PostgreSQLDriver.xml_hidden\"/>")
                .contains("<file name=\"nmox-postgresql.xml\" url=\"postgresql-driver.xml\"/>");
    }

    @Test
    @DisplayName("the hide is ordered: DB Studio depends on the module whose entry it hides")
    void theHideHasItsDependency() throws IOException {
        try (JarFile jf = new JarFile(DBSTUDIO.toFile())) {
            Manifest mf = jf.getManifest();
            String deps = mf.getMainAttributes().getValue("OpenIDE-Module-Module-Dependencies");
            assertThat(deps).as("DB Studio's module dependencies").isNotNull();
            assertThat(List.of(deps.split("\\s*,\\s*")))
                    .anySatisfy(d -> assertThat(d.trim()).startsWith("org.netbeans.modules.db.drivers"));
        }
        assertThat(CLUSTER.resolve(Path.of("ide", "modules", "org-netbeans-modules-db-drivers.jar")))
                .as("the module depended on is in the product").isRegularFile();
    }

    @Test
    @DisplayName("the entry DB Studio registers keeps the platform's name and class, and names a jar that exists")
    void theRegisteredDriverIsTheBundledJar() throws IOException {
        String driver = entry(DBSTUDIO, "org/nmox/studio/dbstudio/postgresql-driver.xml");
        assertThat(driver).as("saved connections find their driver by this name and class")
                .contains("<name value='Postgres'/>")
                .contains("<class value='org.postgresql.Driver'/>");
        Matcher m = NBINST.matcher(driver);
        assertThat(m.find()).as("one nbinst url").isTrue();
        assertThat(m.group(1)).isEqualTo("org.nmox.NMOX.Studio.dbstudio");
        assertThat(CLUSTER.resolve("nmoxstudio").resolve(m.group(2)))
                .as("the jar the driver entry names").isRegularFile();
        assertThat(m.find()).as("exactly one url").isFalse();
    }

    @Test
    @DisplayName("the portable zip is made before the trim, so the release workflow drops the jar from it")
    void thePortableZipDropsItToo() throws IOException {
        String workflow = Files.readString(Path.of("..", ".github", "workflows", "release.yml"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(workflow).contains("zip -d \"NMOX-Studio-${{ needs.version.outputs.version }}-portable.zip\" "
                + "'nmoxstudio/ide/modules/ext/postgresql-*.jar'");
    }
}
