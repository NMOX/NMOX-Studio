package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A language server that is a package inside an interpreter is started only
 * when the package is there (3.5.5).
 *
 * <p>Found by opening one file of every kind the learning catalogue ships:
 * with Racket and Perl installed and neither server package, each "server"
 * started, printed that it could not find its package, and exited, five
 * times for one file, with the failed handshakes in the log and no
 * notification saying what to install.
 */
class HostedServerProbeTest {

    private static final String JAVA = new File(new File(System.getProperty("java.home"), "bin"), "java")
            .getAbsolutePath();
    private static final Duration LIMIT = Duration.ofSeconds(20);

    @BeforeEach
    void forget() {
        LanguageServers.Hosted.forgetForTest();
    }

    @Test
    @DisplayName("a probe that ends non-zero is a no; one that ends zero is not")
    void aDefiniteNo() {
        assertThat(LanguageServers.Hosted.saysNo(List.of(JAVA, "--definitely-not-a-flag"), LIMIT)).isTrue();
        assertThat(LanguageServers.Hosted.saysNo(List.of(JAVA, "-version"), LIMIT)).isFalse();
    }

    @Test
    @DisplayName("no interpreter, or a probe still running at the limit, is not a no: the launch goes ahead as before")
    void notKnownIsNotNo(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        assertThat(LanguageServers.Hosted.saysNo(List.of("definitely-not-a-tool-9x7", "-e", "1"), LIMIT)).isFalse();

        // a program that outlives the limit and would exit non-zero if it were waited for
        Path slow = dir.resolve("Slow.java");
        Files.writeString(slow, "public class Slow { public static void main(String[] a) throws Exception {"
                + " Thread.sleep(60_000); System.exit(1); } }", StandardCharsets.UTF_8);
        assertThat(LanguageServers.Hosted.saysNo(List.of(JAVA, slow.toString()), Duration.ofMillis(1500)))
                .as("a slow machine must not lose a server that works").isFalse();
    }

    @Test
    @DisplayName("a package seen once is not asked for again; an absent one is asked for every time")
    void presentIsRemembered() {
        List<String> yes = List.of(JAVA, "-version");
        List<String> no = List.of(JAVA, "--definitely-not-a-flag");

        assertThat(LanguageServers.Hosted.absent("absent-package", no)).isTrue();
        assertThat(LanguageServers.Hosted.absent("absent-package", yes))
                .as("installed since: found without a restart").isFalse();
        assertThat(LanguageServers.Hosted.absent("absent-package", no))
                .as("and not asked again once seen").isFalse();
    }

    @Test
    @DisplayName("wiring: the four interpreter-hosted servers ask before they launch, and report what is missing")
    void theFourAsk() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        for (String[] server : new String[][] {
            {"class RacketServer", "reported(null, \"racket\")", "\"racket\", \"-l\", \"racket-langserver\""},
            {"class JuliaServer", "reported(null, \"julia\")", "using LanguageServer; runserver()"},
            {"class RServer", "reported(null, \"R\")", "languageserver::run()"},
            {"class PerlServer", "launchFirst(lookup, List.of(\"pls\"))", "Perl::LanguageServer::run"}}) {
            int start = src.indexOf(server[0]);
            assertThat(start).as(server[0]).isPositive();
            String body = src.substring(start, src.indexOf("\n    }\n", start));
            int asks = body.indexOf("if (Hosted.absent(");
            assertThat(asks).as(server[0] + " asks whether its package is there").isPositive();
            assertThat(body.indexOf(server[1])).as(server[0] + " reports instead of launching")
                    .isGreaterThan(asks);
            assertThat(body.indexOf(server[2], body.indexOf(server[1])))
                    .as(server[0] + " launches only after asking").isPositive();
        }
    }
}
