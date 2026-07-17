package org.nmox.studio.editor.outline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drift gate the v1.76.0 review demanded: {@link OutlineModel}
 * gained per-language extractors across ten releases while
 * {@link StructureNavigatorPanel}'s registration block stayed frozen at
 * its v1.34.0 shape — so the Fortran/V/Scheme/Odin (and six older)
 * outlines were fully built, unit-tested, and UNREACHABLE in the
 * product. The panel is OutlineModel's only consumer; a mime missing
 * from its registrations means no Structure view, silently.
 *
 * This test reads both SOURCE files (the annotations are
 * source-retention, processed into the layer — reflection can't see
 * them) and pins: every mime the family() switch maps must be
 * registered on the panel, except the documented aliases below.
 */
class OutlineNavigatorGateTest {

    /**
     * family() aliases that are not real editor mimes: .jsx resolves to
     * text/javascript and .tsx to text/typescript (see
     * JavaScriptDataObject/TypeScriptDataObject) — the alias entries
     * exist so callers passing the specific mime still get the js
     * family, but no file ever opens under them.
     */
    private static final Set<String> ALIAS_MIMES = Set.of("text/jsx", "text/tsx");

    @Test
    @DisplayName("Every family()-mapped mime is registered on the Navigator panel")
    void everyOutlineFamilyMimeIsRegistered() throws IOException {
        Path src = Path.of("src/main/java/org/nmox/studio/editor/outline");
        String model = Files.readString(src.resolve("OutlineModel.java"));
        String panel = Files.readString(src.resolve("StructureNavigatorPanel.java"));

        // the family() switch: everything between the method head and its default
        int start = model.indexOf("static String family(String mime)");
        int end = model.indexOf("default -> \"generic\"", start);
        assertThat(start).as("family() found").isPositive();
        assertThat(end).as("family() default found").isGreaterThan(start);
        String familySwitch = model.substring(start, end);

        Set<String> familyMimes = new TreeSet<>();
        Matcher m = Pattern.compile("\"(text/[a-z0-9/._-]+)\"").matcher(familySwitch);
        while (m.find()) {
            familyMimes.add(m.group(1));
        }
        assertThat(familyMimes).as("family() parses").hasSizeGreaterThan(40);

        Set<String> registered = new TreeSet<>();
        Matcher r = Pattern.compile("mimeType = \"(text/[a-z0-9/._-]+)\"").matcher(panel);
        while (r.find()) {
            registered.add(r.group(1));
        }

        Set<String> missing = new TreeSet<>(familyMimes);
        missing.removeAll(registered);
        missing.removeAll(ALIAS_MIMES);
        assertThat(missing)
                .as("mimes with a real outline family but no Navigator registration"
                        + " — the outline is dead code for these")
                .isEmpty();
    }
}
