package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A hint that lives in a narrow pane has a budget, in every language
 * (v2.123.0).
 *
 * <p>Three walks in a row found the same defect wearing different clothes:
 * a message longer than the thing drawing it. The Workbench middle-elided
 * its tooling descriptions into gibberish (v2.119.0), the learning-space
 * picker clipped its blurbs mid-word and grew a sideways scrollbar
 * (v2.120.0), and Contract Studio"s empty-state hint — a row in a ~320px
 * tree — read "No artifacts found — Compile (forge b".
 *
 * <p>Where the text can wrap, wrapping wins and nothing is cut. Where it
 * CANNOT — a tree row is a row — the only honest answer is to write a
 * shorter sentence, and the only way that survives twelve translations is
 * to fail the build when one does not fit. The budget is a character count
 * rather than a measured width because the gate has no toolkit: it is
 * deliberately generous, and it is a ceiling on prose length, not a
 * promise about pixels.
 *
 * <p>THE POPULATION COMES FROM THE ASSEMBLED CLUSTER (v2.129.0), not from a
 * list of source directories, and that fixed two blind spots a German walk
 * found at once. DB Studio's connection tree read "Noch keine Verbindungen —
 * klicken Sie unte" while this gate was green, because dbstudio was simply
 * not one of the two directories it read. And ENGLISH was never measured for
 * any of them: these modules keep their English in {@code @Messages}, which
 * the annotation processor merges into {@code Bundle.properties} at COMPILE
 * time, so the source tree holds only translations and the gate's own
 * "en" branch was dead code under a javadoc claiming thirteen languages.
 * The shipped jar holds every language in one place, English included, so
 * adding a key to the budget map is now enough — no module can be missed by
 * being unlisted, and no value can be missed by being the original.
 *
 * <p>Bound to the {@code packaged-app-gates} execution for that reason: a
 * gate reading {@code target/} in the test phase passes on a stale cluster.
 */
class NarrowPaneHintBudgetTest {

    /** Key → the budget its pane can show, with why that pane is narrow. */
    private static final Map<String, Integer> BUDGETS = Map.of(
            // a node in the contract tree, which shares the studio with the
            // Interact pane and is the narrower half
            "Web3StudioTopComponent_noArtifacts", 68,
            "Web3StudioTopComponent_noDeployments", 68,
            // a full-width row in the Workbench's left pane (v2.125.0): the
            // walk in GERMAN — the first in a language longer than English —
            // photographed this one cut at "wo Sie aufgehört hab", with no
            // ellipsis and no tooltip, so the sentence simply ended. English
            // is 47 characters and German was 70
            "ProjectExplorerTopComponent_nothingOpen", 62,
            "ProjectExplorerTopComponent_filesGather", 62,
            // rows in DB Studio's connection tree, the narrow left third of
            // a studio whose console takes the rest (v2.129.0): the German
            // walk photographed the first cut at "klicken Sie unte", no
            // ellipsis, no tooltip — the sentence simply stopped. French was
            // 82 characters and English 49, in a pane that showed 43
            "DbStudioTopComponent_noConnectionsYet", 44,
            "DbStudioTopComponent_noServicesConnections", 44);

    /** The shipped modules: every bundle the product actually loads, in one place. */
    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");

    @Test
    @DisplayName("every narrow-pane hint fits its budget in every language the product speaks")
    void hintsFitTheirPane() throws IOException {
        List<String> over = new ArrayList<>();
        int measured = 0;
        assertThat(MODULES).as("the assembled cluster's own modules").isDirectory();
        try (var jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        String name = e.getName();
                        int slash = name.lastIndexOf('/');
                        String file = slash < 0 ? name : name.substring(slash + 1);
                        if (!file.startsWith("Bundle") || !file.endsWith(".properties")) {
                            continue;
                        }
                        String lang = file.replace("Bundle", "").replace(".properties", "")
                                .replace("_", "");
                        Properties props = new Properties();
                        try (InputStream in = jar.getInputStream(e)) {
                            // UTF-8, NOT Properties.load(InputStream): that
                            // overload is ISO-8859-1 by contract, and these
                            // bundles ship as raw UTF-8 (the em dash is e2 80
                            // 94 in the jar). Reading them byte-wise turned
                            // every Cyrillic and Devanagari value into two or
                            // three times its length and reported the whole
                            // set over budget — a gate measuring its own
                            // decoding rather than the product's prose
                            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                        }
                        for (Map.Entry<String, Integer> budgeted : BUDGETS.entrySet()) {
                            String value = props.getProperty(budgeted.getKey());
                            if (value == null) {
                                continue;
                            }
                            measured++;
                            if (value.length() > budgeted.getValue()) {
                                over.add((lang.isEmpty() ? "en" : lang) + " " + budgeted.getKey()
                                        + ": " + value.length() + " > " + budgeted.getValue()
                                        + " — " + value);
                            }
                        }
                    }
                }
            }
        }
        assertThat(measured)
                .as("the gate should find every budgeted hint in the SHIPPED bundles — "
                        + "English and all twelve translations — or it is guarding keys "
                        + "nobody ships")
                .isGreaterThan(70);
        assertThat(over)
                .as("a hint wider than its pane is cut where the reader cannot get it back; "
                        + "write a shorter sentence, and put any command on the button that runs it")
                .isEmpty();
    }
}
