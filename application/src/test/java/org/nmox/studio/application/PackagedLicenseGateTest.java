package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The relicense follow-up gate (v1.208.0): every installer carries the
 * license terms alongside the app.
 *
 * <p>All five distributables (DMG, tar.gz, deb, Windows installer,
 * portable zip) are built from — or, for the zip, aligned with — the
 * assembled cluster at {@code application/target/nmoxstudio}, so the one
 * place LICENSE and NOTICE must exist is that directory. A
 * maven-resources execution ({@code bundle-license-notice} in this
 * module's pom) copies them there during {@code package}; this test runs
 * in the {@code packaged-app-gates} surefire execution at
 * {@code integration-test} — after packaging — so it checks the REAL
 * assembled output, not the sources.
 *
 * <p>The portable zip is the one artifact the nbm plugin zips before the
 * copy runs, so release.yml appends the two files into it separately;
 * the workflow half of that promise is pinned here as a source gate.
 */
class PackagedLicenseGateTest {

    private static final Path APP_DIR = Path.of("target", "nmoxstudio");

    @Test
    @DisplayName("assembled app carries the canonical Apache-2.0 LICENSE")
    void assembledAppCarriesLicense() throws IOException {
        Path license = APP_DIR.resolve("LICENSE");
        assertThat(license).as("LICENSE beside the assembled app").exists();
        String text = Files.readString(license);
        assertThat(text).contains("Apache License");
        assertThat(text).contains("Version 2.0, January 2004");
    }

    @Test
    @DisplayName("assembled app carries the NOTICE with NetBeans attribution")
    void assembledAppCarriesNotice() throws IOException {
        Path notice = APP_DIR.resolve("NOTICE");
        assertThat(notice).as("NOTICE beside the assembled app").exists();
        String text = Files.readString(notice);
        assertThat(text).contains("NMOX Studio");
        assertThat(text).contains("NetBeans");
    }

    @Test
    @DisplayName("release workflow appends LICENSE and NOTICE into the portable zip")
    void portableZipStepAppendsLicenseAndNotice() throws IOException {
        String workflow = Files.readString(
                Path.of("..", ".github", "workflows", "release.yml"));
        // The zip is created by the nbm plugin before the package-phase
        // copy lands the files in the cluster dir, so the workflow must
        // add them itself. Pin the mechanism, not just intent: a staging
        // dir shaped like the zip's internal root, then zip -ur.
        assertThat(workflow)
                .as("portable-zip step stages LICENSE and NOTICE under the zip root")
                .contains("cp LICENSE NOTICE zipstage/nmoxstudio/")
                .contains("zip -ur");
    }
}
