package org.nmox.studio.infra;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.ProjectAim;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tech-debt ledger 72, closed v2.186.0: the Infra Designer publishes
 * the aimed project's node as its ambient selection, so the platform's
 * project-sensitive actions (Test Project ^F6, the Team menu, the git
 * verbs) work while this tab is focused. Before this it published
 * nothing at all — those actions read the global selection and greyed
 * for every project kind.
 *
 * <p>Two laws, both behavioural on the REAL window: showing publishes
 * the aim, and a hidden window follows nothing (the v1.38.0 boot law —
 * a hidden default-open tab must resolve no filesystem node at boot).
 * The follower itself is core's and proven there; what this pins is
 * that THIS window is wired to it, with its own hooks and its own sink.
 */
class InfraDesignerAimSelectionTest {

    /** A stand-in provider: this module keeps no rack on its classpath. */
    private static final class FakeAim implements ProjectAim {

        private final List<Listener> listeners = new CopyOnWriteArrayList<>();
        private volatile File dir;

        FakeAim(File dir) {
            this.dir = dir;
        }

        void aimAt(File next) {
            dir = next;
            listeners.forEach(Listener::projectChanged);
        }

        @Override
        public File projectDir() {
            return dir;
        }

        @Override
        public void aim(File d) {
            aimAt(d);
        }

        @Override
        public List<File> recentProjects() {
            return List.of();
        }

        @Override
        public void addListener(Listener listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public void addManifestListener(Consumer<List<Path>> listener) {
        }

        @Override
        public void removeManifestListener(Consumer<List<Path>> listener) {
        }
    }

    private static void await(java.util.function.BooleanSupplier cond) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 10s");
            }
            Thread.sleep(20);
        }
    }

    /** The directory the window's current selection names, or null. */
    private static File selectedDir(InfraDesignerTopComponent window) throws Exception {
        File[] out = {null};
        SwingUtilities.invokeAndWait(() -> {
            Node[] nodes = window.getActivatedNodes();
            if (nodes == null || nodes.length == 0) {
                return;
            }
            DataObject dob = nodes[0].getLookup().lookup(DataObject.class);
            out[0] = dob == null ? null : FileUtil.toFile(dob.getPrimaryFile());
        });
        return out[0];
    }

    @Test
    @DisplayName("showing publishes the aimed project's node; hidden follows nothing")
    void publishesWhileShowingOnly(@TempDir File aimA, @TempDir File aimB) throws Exception {
        InfraDesignerTopComponent[] holder = new InfraDesignerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new InfraDesignerTopComponent());
        InfraDesignerTopComponent tc = holder[0];
        FakeAim aim = new FakeAim(aimA);
        tc.followAimForTest(() -> aim);

        assertThat(selectedDir(tc))
                .as("a window that has never been shown offers no selection")
                .isNull();

        SwingUtilities.invokeAndWait(tc::componentShowing);
        await(() -> {
            try {
                return FileUtil.normalizeFile(aimA).equals(selectedDir(tc));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        // the aim moves while the tab is SHOWING: the selection follows
        aim.aimAt(aimB);
        await(() -> {
            try {
                return FileUtil.normalizeFile(aimB).equals(selectedDir(tc));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        // hidden: the aim moves back and the window must NOT follow it —
        // the v1.38.0 law, and the reason a hidden default-open tab costs
        // nothing at boot
        SwingUtilities.invokeAndWait(tc::componentHidden);
        aim.aimAt(aimA);
        Thread.sleep(300);
        assertThat(selectedDir(tc))
                .as("a hidden window resolves nothing — its selection is frozen "
                        + "at whatever it last published while showing")
                .isEqualTo(FileUtil.normalizeFile(aimB));
    }
}
