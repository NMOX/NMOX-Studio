package org.nmox.studio.tools.npm;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wizard panel honors the listener contract: Next/Finish buttons
 * only re-validate when the panel actually fires - a panel that never
 * fires leaves the wizard frozen on its first validity verdict.
 */
class WizardChangeSupportTest {

    @Test
    @DisplayName("fireChangeEvent reaches registered listeners; removal silences them")
    void listenersFireAndRemove() {
        WebProjectWizardPanel panel = new WebProjectWizardPanel();
        AtomicInteger fired = new AtomicInteger();
        javax.swing.event.ChangeListener listener = e -> fired.incrementAndGet();

        panel.addChangeListener(listener);
        panel.fireChangeEvent();
        assertThat(fired.get()).isEqualTo(1);

        panel.removeChangeListener(listener);
        panel.fireChangeEvent();
        assertThat(fired.get()).as("removed listener stays silent").isEqualTo(1);
    }
}
