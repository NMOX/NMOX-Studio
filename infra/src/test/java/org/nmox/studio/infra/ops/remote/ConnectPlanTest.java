package org.nmox.studio.infra.ops.remote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectPlanTest {

    @Test
    @DisplayName("mosh only when both ends have it; the reason names the missing end")
    void chooses() {
        assertThat(ConnectPlan.choose(true, true)).isEqualTo(ConnectPlan.MOSH);
        assertThat(ConnectPlan.choose(false, true)).isEqualTo(ConnectPlan.SSH_NO_LOCAL_MOSH);
        assertThat(ConnectPlan.choose(true, false)).isEqualTo(ConnectPlan.SSH_NO_REMOTE_MOSH);
        assertThat(ConnectPlan.choose(true, null)).isEqualTo(ConnectPlan.SSH_NO_REMOTE_MOSH);
        assertThat(ConnectPlan.choose(false, null).mosh()).isFalse();
    }
}
