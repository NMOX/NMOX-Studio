package org.nmox.studio.rack.projectstudio;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

class RackPresetsTest {

    @TempDir
    Path projectDir;

    @Test
    @DisplayName("Should build a mountable patch from every preset")
    void shouldBuildEveryPreset() {
        for (RackPresets preset : RackPresets.values()) {
            Rack rack = new Rack();
            rack.setProjectDir(projectDir.toFile());
            try {
                RackIO.fromJson(rack, preset.buildPatch());
                assertThat(rack.getDevices()).as(preset + " devices").isNotEmpty();
                assertThat(rack.getCables()).as(preset + " cables").isNotEmpty();
            } finally {
                rack.shutdown();
            }
        }
    }
}
