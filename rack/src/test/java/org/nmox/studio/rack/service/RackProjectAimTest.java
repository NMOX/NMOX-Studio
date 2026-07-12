package org.nmox.studio.rack.service;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.ProjectAim;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link ProjectAim} facade contract (ledger 30): the adapter is a
 * pure delegate over {@link RackService} — reads read the rack, aims
 * aim it, and the narrowed listener shape stays symmetric (add/remove
 * operate on the same wrapper; a double-add never double-delivers).
 */
class RackProjectAimTest {

    @Test
    @DisplayName("the rack module publishes the provider — studios find it by lookup")
    void lookupFindsProvider() {
        ProjectAim aim = Lookup.getDefault().lookup(ProjectAim.class);
        assertThat(aim).isInstanceOf(RackProjectAim.class);
    }

    @Test
    @DisplayName("projectDir/aim delegate to the service's rack")
    void projectDirAndAimDelegate(@TempDir Path tmp) {
        RackService service = new RackService();
        service.switchConfirmer = message -> true;
        service.bridgeHook = dir -> { }; // no platform in a unit test
        ProjectAim aim = new RackProjectAim(service);
        try {
            File first = tmp.resolve("first").toFile();
            assertThat(first.mkdir()).isTrue();
            service.getRack().setProjectDir(first);
            assertThat(aim.projectDir()).isEqualTo(first);

            File second = tmp.resolve("second").toFile();
            assertThat(second.mkdir()).isTrue();
            aim.aim(second);
            assertThat(service.getRack().getProjectDir()).isEqualTo(second);
            // aim() is openProject: the recent list follows, same as before
            assertThat(aim.recentProjects()).contains(second);
        } finally {
            service.awaitBridgeIdle();
            service.getRack().shutdown();
        }
    }

    @Test
    @DisplayName("aiming at a non-directory is the no-op it always was")
    void aimIgnoresNonDirectories(@TempDir Path tmp) {
        RackService service = new RackService();
        service.bridgeHook = dir -> { };
        ProjectAim aim = new RackProjectAim(service);
        try {
            File dir = tmp.toFile();
            service.getRack().setProjectDir(dir);

            aim.aim(new File(dir, "no-such-dir"));
            assertThat(service.getRack().getProjectDir()).isEqualTo(dir);
        } finally {
            service.getRack().shutdown();
        }
    }

    @Test
    @DisplayName("listener lifecycle: subscribed hears each aim once; removed hears nothing; double-add never double-delivers")
    void listenerLifecycle(@TempDir Path tmp) {
        RackService service = new RackService();
        ProjectAim aim = new RackProjectAim(service);
        AtomicInteger heard = new AtomicInteger();
        ProjectAim.Listener listener = heard::incrementAndGet;

        try {
            aim.addListener(listener);
            aim.addListener(listener); // must not double-deliver
            File a = tmp.resolve("a").toFile();
            assertThat(a.mkdir()).isTrue();
            service.getRack().setProjectDir(a);
            assertThat(heard.get()).isEqualTo(1);

            // re-aiming the SAME dir fires nothing (Rack's equality guard rides through)
            service.getRack().setProjectDir(a);
            assertThat(heard.get()).isEqualTo(1);

            aim.removeListener(listener);
            File b = tmp.resolve("b").toFile();
            assertThat(b.mkdir()).isTrue();
            service.getRack().setProjectDir(b);
            assertThat(heard.get()).isEqualTo(1); // detached: silence

            // removing again (or a stranger) is a quiet no-op
            aim.removeListener(listener);
            aim.removeListener(() -> { });
        } finally {
            service.getRack().shutdown();
        }
    }

    @Test
    @DisplayName("manifest listeners ride the service's own list — add hears the batch, remove silences it")
    void manifestListenersDelegate() throws Exception {
        RackService service = new RackService();
        ProjectAim aim = new RackProjectAim(service);
        AtomicInteger heard = new AtomicInteger();
        java.util.function.Consumer<List<Path>> listener = batch -> heard.incrementAndGet();

        aim.addManifestListener(listener);
        dispatch(service, List.of(Path.of("package.json")));
        assertThat(heard.get()).isEqualTo(1);

        aim.removeManifestListener(listener);
        dispatch(service, List.of(Path.of("package.json")));
        assertThat(heard.get()).isEqualTo(1);
    }

    /** Fires the service's private manifest dispatch, as ManifestPulse would. */
    private static void dispatch(RackService service, List<Path> batch) throws Exception {
        Method m = RackService.class.getDeclaredMethod("dispatchManifestBatch", List.class);
        m.setAccessible(true);
        m.invoke(service, batch);
    }
}
