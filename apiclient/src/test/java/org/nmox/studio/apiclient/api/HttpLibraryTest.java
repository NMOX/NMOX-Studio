package org.nmox.studio.apiclient.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request library (v1.297.0): .http/.rest files in
 * {@code ~/.nmox/api-library.d} join the Import… menu. The library
 * speaks .http DELIBERATELY — it is the product's sharing format, with
 * auth absent on export and keychain-lifted on import — so these tests
 * pin the listing discipline and the one-implementation wiring, not a
 * second parser.
 */
class HttpLibraryTest {

    @Test
    @DisplayName("listing: .http and .rest files, filename order, named sans extension")
    void listsHttpFiles(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("b-github.http"), "GET https://x/\n",
                StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("a-payments.rest"), "GET https://y/\n",
                StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("notes.txt"), "not a library entry",
                StandardCharsets.UTF_8);

        assertThat(HttpLibrary.listFrom(tmp.toFile()))
                .extracting(HttpLibrary.Entry::name)
                .containsExactly("a-payments", "b-github");
    }

    @Test
    @DisplayName("a missing library dir lists nothing and is not created")
    void missingDirIsEmpty(@TempDir Path tmp) {
        java.io.File absent = tmp.resolve("never").toFile();
        assertThat(HttpLibrary.listFrom(absent)).isEmpty();
        assertThat(absent).doesNotExist();
    }

    @Test
    @DisplayName("library items ride the ONE importHttpFrom implementation")
    void menuRidesTheSharedSeam() throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "apiclient", "ui", "ApiClientTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(src)
                .as("the library must join the menu, or the drop-in dir is dead")
                .contains("HttpLibrary.list()");
        assertThat(src)
                .as("a library import must ride the shared seam — the off-EDT"
                        + " read, the refusals, and the secrets-law lift live"
                        + " there and must reach the library by construction")
                .contains("importHttpFrom(entry.file())");
        assertThat(src)
                .as("the scan is file IO off the EDT, and the button disables"
                        + " until the menu shows (the v1.296.0 double-open"
                        + " class, prevented on day one)")
                .contains("importBtn.setEnabled(false);");
    }
}
