package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloneRepositoryTest {

    @Test
    @DisplayName("without the git module the link refuses instead of throwing (the status line then speaks)")
    void refusesWithoutGit() {
        assertThat(CloneRepository.tryOpen(new ActionEvent(this, 0, "clone"))).isFalse();
    }
}
