package org.nmox.studio.apiclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The loading-guard law (v1.265.0, the v1.259–v1.264 arc review's find):
 * the v1.263.0 rename bug's worst symptom was not the exception — it was
 * the aborted bindRequest leaving {@code loading} stuck true, which
 * silently killed every edit-recording listener from then on: a UI bug
 * promoted to DATA LOSS with no visible symptom. Removing that trigger
 * fixed the instance; six raise sites still had the un-finally'd shape,
 * so ANY future throw inside a guarded body would re-create the class.
 *
 * <p>Now every raise of the flag is structurally paired with a
 * guaranteed drop, and this gate makes the shape a build law:
 * {@code loading = true} may only appear immediately above a
 * {@code try} block, and {@code loading = false} may only appear inside
 * a {@code finally}. A new bare raise anywhere in the studio fails the
 * build by name.
 */
class LoadingGuardShapeTest {

    private static List<String> studioLines() throws Exception {
        Path src = Path.of("src", "main", "java", "org", "nmox", "studio",
                "apiclient", "ui", "ApiClientTopComponent.java");
        assertThat(src).exists();
        return Files.readAllLines(src);
    }

    @Test
    @DisplayName("every raise of the loading guard is followed by a try block")
    void everyRaiseEntersATry() throws Exception {
        List<String> lines = studioLines();
        int raises = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().equals("loading = true;")) {
                continue;
            }
            raises++;
            String next = i + 1 < lines.size() ? lines.get(i + 1).trim() : "";
            assertThat(next)
                    .as("line %d raises the guard, so the NEXT line must open "
                            + "the try that guarantees its fall — a throw "
                            + "between a bare raise and its drop wedges the "
                            + "guard and silently stops edit recording "
                            + "(the v1.263.0 data-loss class)", i + 1)
                    .isEqualTo("try {");
        }
        assertThat(raises)
                .as("the guard is raised somewhere (the law has a subject)")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("every drop of the loading guard lives in a finally block")
    void everyDropIsInAFinally() throws Exception {
        List<String> lines = studioLines();
        int drops = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().equals("loading = false;")) {
                continue;
            }
            drops++;
            String prev = i > 0 ? lines.get(i - 1).trim() : "";
            assertThat(prev)
                    .as("line %d drops the guard, so it must sit directly "
                            + "inside a finally — anywhere else and an "
                            + "exception path can skip it", i + 1)
                    .isEqualTo("} finally {");
        }
        assertThat(drops).isGreaterThanOrEqualTo(2);
    }
}
