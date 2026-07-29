package org.nmox.studio.project;

import java.beans.PropertyChangeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The startup hook that feeds the recent-files trail: it must be
 * inert on everything that is not an editor-tab activation, and its
 * registration must survive a headless (dummy window manager)
 * platform — the same environment CI boots it in. The full
 * record-on-activation path needs a real opened editor tab and is
 * exercised by the app-level gauntlets, not here.
 */
class RecentFilesTrackerTest {

    @Test
    @DisplayName("a non-activation registry event is ignored without touching the trail")
    void ignoresOtherEvents() {
        RecentFilesTracker tracker = new RecentFilesTracker();

        assertThatCode(() -> tracker.propertyChange(new PropertyChangeEvent(
                this, TopComponent.Registry.PROP_OPENED, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an activation with no activated editor component is a quiet no-op")
    void activationWithoutEditorIsQuiet() {
        RecentFilesTracker tracker = new RecentFilesTracker();

        // headless registry: nothing is activated, so the tracker must
        // stop at its guard rather than NPE its way into RecentFiles
        assertThatCode(() -> tracker.propertyChange(new PropertyChangeEvent(
                this, TopComponent.Registry.PROP_ACTIVATED, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the @OnStart registration hooks the window registry without a real window system")
    void onStartRegistersHeadless() throws Exception {
        RecentFilesTracker tracker = new RecentFilesTracker();
        try {
            assertThatCode(tracker::run).doesNotThrowAnyException();
            // the dummy window manager runs UI-ready hooks via the EDT queue;
            // drain it so the listener add executes before we detach again
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
        } finally {
            TopComponent.getRegistry().removePropertyChangeListener(tracker);
        }
    }
}
