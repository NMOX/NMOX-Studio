package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenProjects bridge (ledger 29, v1.45.0): a real aim publishes the
 * aimed directory to the platform's OpenProjects so both context systems
 * agree — with four guards, each pinned here. (a) WebProjectOpenedHook's
 * echo back into openProject must terminate on OUR re-entrancy flag, not
 * on the platform's idempotence; (b) passive aims (fresh boot, persisted
 * window state, the open-projects follower) never touch the platform —
 * the v1.38.0 boot law; (c) a directory the platform doesn't recognize
 * no-ops silently; (d) the bridge only ever opens, never closes.
 */
class OpenProjectsBridgeTest {

    @Test
    @DisplayName("(a) a hook that re-enters openProject publishes EXACTLY once — no echo loop")
    void reEnteringHookPublishesExactlyOnce(@TempDir Path a) {
        RackService service = new RackService();
        service.switchConfirmer = message -> true;
        AtomicInteger published = new AtomicInteger();
        service.bridgeHook = dir -> {
            published.incrementAndGet();
            // WebProjectOpenedHook's shape: OpenProjects.open fires the
            // ProjectOpenedHook, which aims the rack right back at the same
            // dir. Without the re-entrancy flag this proceed re-runs, which
            // re-publishes, which re-enters — forever (a fake hook has no
            // platform idempotence to hide behind).
            service.openProject(dir);
        };

        service.openProject(a.toFile());
        service.awaitBridgeIdle();
        // a second drain: an echo-queued publication would have landed by now
        service.awaitBridgeIdle();

        assertThat(published.get())
                .as("proceed side effects (and the publish they end in) run exactly once")
                .isEqualTo(1);
        assertThat(service.getRecentProjects())
                .as("addRecent not doubled by the echo")
                .containsOnlyOnce(a.toFile());
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("(b) passive aims never invoke the bridge; the next explicit aim still does")
    void passiveAimsNeverTouchThePlatform(@TempDir Path a, @TempDir Path b) {
        RackService service = new RackService();
        List<File> published = new CopyOnWriteArrayList<>();
        service.bridgeHook = published::add;

        // fresh-boot shape: getRack() runs the default-workspace aim and
        // followOpenProjects; then a passive source (persisted window state)
        service.getRack();
        service.openProjectPassively(a.toFile());
        service.awaitBridgeIdle();
        assertThat(published)
                .as("no passive path may resolve platform projects (v1.38.0 boot law)")
                .isEmpty();

        // the lane itself works — order on the single RP proves the passive
        // aims enqueued nothing ahead of this
        service.switchConfirmer = message -> true;
        service.openProject(b.toFile());
        service.awaitBridgeIdle();
        assertThat(published).containsExactly(b.toFile());
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("(b) source gate: no passive path calls publishToOpenProjects")
    void passivePathsCarryNoPublishCall() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/service/RackService.java"),
                StandardCharsets.UTF_8);
        for (String signature : new String[]{
            "public void openProjectPassively(File dir)",
            "private void aimAtDefaultWorkspace()",
            "private void followOpenProjects()",
            "private void aimAtOpenProject()",
            "public void openProjectQuietly(File dir)"}) {
            assertThat(method(source, signature))
                    .as(signature + " must not publish to OpenProjects")
                    .doesNotContain("publishToOpenProjects");
        }
        assertThat(method(source, "public void openProject(File dir)"))
                .as("the explicit aim is the one publisher")
                .contains("publishToOpenProjects");
    }

    @Test
    @DisplayName("(c) a directory with no manifest no-ops silently — the aim stands")
    void unrecognizedDirectoryNoOpsSilently(@TempDir Path plain) {
        // the REAL bridge hook: findProject on a manifest-less dir is null
        // (and in this headless JVM the project infrastructure may be absent
        // entirely — the bridge must shrug either way, never throw)
        RackService service = new RackService();
        service.switchConfirmer = message -> true;

        service.openProject(plain.toFile());
        service.awaitBridgeIdle();

        assertThat(service.getRack().getProjectDir())
                .as("the rack aims anywhere; the platform only at projects")
                .isEqualTo(plain.toFile());
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("(e) the real bridge never poisons ProjectManager for the rest of the JVM")
    void realBridgeLeavesProjectManagerUsable(@TempDir Path plain) {
        // the ubuntu-order failure of v1.45.0: ProjectManager.<clinit> ran
        // inside the real bridge hook, found no ProjectManagerImplementation
        // on the test classpath, and left the class poisoned — after which
        // openide-nodes' ProjectManagerDeadlockDetector threw from EVERY
        // DataObject.getNodeDelegate in the JVM (AimNodePublisherTest and
        // all three aim-selection window tests died 30 seconds later, but
        // only when surefire's filesystem order ran this class first).
        RackService service = new RackService();
        service.switchConfirmer = message -> true;
        service.openProject(plain.toFile());
        service.awaitBridgeIdle(); // the real hook has now touched ProjectManager

        assertThat(AimNodePublisher.resolveFolderNode(plain.toFile()))
                .as("getNodeDelegate must survive a real bridge publication — "
                        + "if this is null, ProjectManager failed to initialize "
                        + "(projectapi-nb missing from the test classpath?)")
                .isNotNull();
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("(d) source gate: the bridge (and all of rack) never calls OpenProjects.close")
    void bridgeNeverClosesProjects() throws Exception {
        // closing is the user's call: a rack aim is not a statement about
        // other projects, and nothing in this codebase closes any today —
        // keep it that way
        Pattern close = Pattern.compile("OpenProjects[\\s\\S]{0,120}?\\.close\\s*\\(");
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : (Iterable<Path>) sources
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains("OpenProjects")) {
                    assertThat(close.matcher(text).find())
                            .as(file + " must not close platform projects")
                            .isFalse();
                }
            }
        }
    }

    /** BootGateTest's idiom: the body of one method, located by signature. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }
}
