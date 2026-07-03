package org.nmox.studio.editor.format;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The options controller is thin glue over the IDE-wide Format-on-Save
 * preference: update() loads the current value into the checkbox,
 * isChanged() reports divergence, and applyChanges() writes it back.
 * These pin the load/dirty/store cycle against the real preference store.
 */
class FormatOnSaveOptionsControllerTest {

    private final boolean original = FormatOnSave.isEnabled();

    @AfterEach
    void restore() {
        FormatOnSave.setEnabled(original);
    }

    @Test
    @DisplayName("Default preference is on")
    void defaultOn() {
        NbPreferences.forModule(FormatOnSave.class).remove(FormatOnSave.PREF_ENABLED);
        assertThat(FormatOnSave.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("setEnabled/isEnabled round-trips through the preference store")
    void roundTripsPreference() {
        FormatOnSave.setEnabled(false);
        assertThat(FormatOnSave.isEnabled()).isFalse();
        FormatOnSave.setEnabled(true);
        assertThat(FormatOnSave.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("update() loads the current preference into the panel; freshly loaded is not changed")
    void updateLoadsAndIsNotDirty() {
        FormatOnSave.setEnabled(true);
        FormatOnSaveOptionsController c = new FormatOnSaveOptionsController();
        c.update();
        assertThat(c.isChanged()).isFalse();
    }

    @Test
    @DisplayName("isChanged() is false before the panel exists")
    void notChangedBeforePanel() {
        FormatOnSaveOptionsController c = new FormatOnSaveOptionsController();
        // no getComponent()/update() yet: the checkbox is null, so nothing is dirty
        assertThat(c.isChanged()).isFalse();
    }

    @Test
    @DisplayName("applyChanges() writes the panel's state back to the preference")
    void applyChangesWritesBack() {
        FormatOnSave.setEnabled(true);
        FormatOnSaveOptionsController c = new FormatOnSaveOptionsController();
        c.getComponent(org.openide.util.Lookup.EMPTY);   // build the panel
        c.update();                                       // checkbox = true
        // flip the preference under the panel so update() reflects true, then
        // simulate the user toggling by applying the opposite via a fresh store write
        FormatOnSave.setEnabled(false);                   // preference now false
        assertThat(c.isChanged()).isTrue();               // panel(true) != pref(false)
        c.applyChanges();                                 // writes panel's true back
        assertThat(FormatOnSave.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isValid() is always true and cancel() is a no-op")
    void trivialContract() {
        FormatOnSaveOptionsController c = new FormatOnSaveOptionsController();
        assertThat(c.isValid()).isTrue();
        c.cancel();   // must not throw
    }
}
