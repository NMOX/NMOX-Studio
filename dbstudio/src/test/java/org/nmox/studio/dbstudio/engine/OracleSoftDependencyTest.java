package org.nmox.studio.dbstudio.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Explain button's standing properties.
 *
 * <p><b>NOT a soft-dependency boundary</b> — the correction is worth
 * recording. Unlike API Studio (which dropped its rack dependency in
 * v1.46.0), DB Studio still DECLARES one: {@code FileWatcher} and
 * {@code DockerClient} are rack surfaces with no core equivalent, and
 * their KEPT catches are documented against ledger 30. So the ORACLE
 * provider IS on this module's classpath and the lookup finds it. An
 * earlier version of this test asserted the lookup was null; it passed
 * under {@code -pl dbstudio} and failed the full reactor, because the
 * PREMISE was wrong, not the assertion.
 *
 * <p>The null branch is therefore DEFENSIVE — it covers a platform
 * where the provider was never registered — not a module boundary. It
 * still has to exist, which is what the source gate below pins.
 */
class OracleSoftDependencyTest {

    @Test
    @DisplayName("the error strip is built only when the lookup succeeds")
    void stripIsGuardedByTheLookup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"));

        int guard = source.indexOf("OracleAsk.find() != null");
        int strip = source.indexOf("explainStrip(spec, result)");
        assertThat(guard).as("the null-lookup guard must exist").isGreaterThan(-1);
        assertThat(strip).as("the strip is added under it").isGreaterThan(guard);
    }

    @Test
    @DisplayName("the strip is offered only for a FAILED statement")
    void stripOnlyOnError() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"));
        // a successful statement has rows on screen; Explain exists for
        // the error message, and the disclosure assumes there are none
        assertThat(source).contains("result.isError() && org.nmox.studio.core.spi.OracleAsk.find()");
    }

    /**
     * v1.175.0 — the same law the API Studio review established in
     * v1.172.0, checked on THIS consumer: a result belongs to the
     * workspace that produced it, so a re-aim must drop it before the
     * Explain button can disclose a previous project's SQL.
     */
    @Test
    @DisplayName("a workspace re-aim drops the result tabs Explain could disclose")
    void reAimClearsResultTabs() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"));

        String apply = source.substring(source.indexOf("private void applyReloadedWorkspace"));
        apply = apply.substring(0, apply.indexOf("\n    }"));
        assertThat(apply)
                .as("every re-aim must forget the previous workspace's results")
                .contains("clearResultTabs()");

        // and the run path shares the one implementation, so the two can
        // never drift apart
        assertThat(source).contains("private void clearResultTabs()");
        assertThat(source.split("clearResultTabs\\(\\)", -1).length - 1)
                .as("declaration plus BOTH call sites")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("the disclosure cannot carry connection details — structurally")
    void disclosureCarriesNoConnection() throws Exception {
        // Asserted on the SIGNATURE, not by grepping the file for
        // "password": the javadoc names what is never sent, which is
        // good documentation that a word-absence gate would punish
        // (it did, on 2026-07-26). What matters is that no type
        // carrying a host, user or password can reach this core at all.
        // synthetic members are skipped: under `mvn verify` JaCoCo
        // instruments the class and injects $jacocoInit/$jacocoData,
        // whose types are nothing to do with our API (this test passed
        // under `mvn test` and failed under `mvn verify` until filtered)
        for (var m : SqlErrorDisclosure.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            for (var p : m.getParameterTypes()) {
                assertThat(p)
                        .as("%s takes only strings/ints — never a connection type", m.getName())
                        .matches(t -> t == String.class || t.isPrimitive(),
                                "String or primitive");
            }
        }
        // and the class knows nothing of the connection model
        for (var f : SqlErrorDisclosure.class.getDeclaredFields()) {
            if (f.isSynthetic()) {
                continue;
            }
            assertThat(f.getType())
                    .as("field %s", f.getName())
                    .matches(t -> t.isPrimitive() || t == String.class, "no model types");
        }
    }
}
