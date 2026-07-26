package org.nmox.studio.dbstudio.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.OracleAsk;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB Studio's Explain button follows the same soft-dependency law as
 * API Studio's (ledger 30): the studio compiles against core's
 * {@link OracleAsk} facade and branches on a null lookup, so a platform
 * without the rack simply has no button — an honest lookup miss, never
 * a caught {@code LinkageError}.
 */
class OracleSoftDependencyTest {

    @Test
    @DisplayName("without the rack module, the ORACLE lookup is null (the feature-off branch)")
    void lookupIsNullWithoutRack() {
        assertThat(OracleAsk.find()).isNull();
    }

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
