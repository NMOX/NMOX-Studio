package org.nmox.studio.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "one vocabulary, two surfaces" claim, made structural (the
 * v1.189.0 lesson: a comment claiming a parity property is a test not
 * yet written). {@link NgSchematic#SCHEMATICS} and HALO's private
 * SCHEMATICS in AngularDevice are two literal lists that must stay in
 * lockstep — HALO's field is private in another module, so this gate
 * reads the rack SOURCE, the CiExportPinCurrencyTest idiom.
 */
class NgSchematicParityGateTest {

    @Test
    @DisplayName("File-menu schematics equal HALO's knob vocabulary, in order")
    void schematicsMatchHalo() throws Exception {
        Path src = Path.of("..", "rack", "src", "main", "java",
                "org", "nmox", "studio", "rack", "devices", "AngularDevice.java");
        assertThat(src).as("rack source visible from the ui module").exists();
        String java = Files.readString(src);

        Matcher decl = Pattern.compile(
                "SCHEMATICS\\s*=\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(java);
        assertThat(decl.find())
                .as("AngularDevice still declares a SCHEMATICS array").isTrue();

        List<String> halo = new ArrayList<>();
        Matcher item = Pattern.compile("\"([^\"]+)\"").matcher(decl.group(1));
        while (item.find()) {
            halo.add(item.group(1));
        }
        assertThat(halo).as("regex actually extracted the vocabulary").isNotEmpty();
        assertThat(List.of(NgSchematic.SCHEMATICS))
                .as("File ▸ New Angular Schematic… offers exactly HALO's list")
                .containsExactlyElementsOf(halo);
    }
}
