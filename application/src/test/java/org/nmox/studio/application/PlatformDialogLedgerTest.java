package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform's own dialogs, and what we have done about each one.
 *
 * <p>The NetBeans Platform ships no UI localization (measured, v2.101.1), so
 * every dialog IT owns reads English in a translated build until we overlay
 * its bundle in the branding+locale slot. v2.127.0 overlaid the shared
 * message-dialog buttons; v2.141.0 found that a dialog building its OWN button
 * row never sees them (the Plugin Manager), and v2.142.0 found four more of the
 * same shape — the Options dialog, Go to File, Find in Projects and the
 * Templates manager.
 *
 * <p><b>This population cannot be derived.</b> There is no list of "dialogs" in
 * the cluster: a dialog is whatever some action decides to show, and only a
 * WALK can tell you what it paints. So the ledger is hand-kept — and every
 * claim it makes is checked here, which is the honest half:
 *
 * <ul>
 *   <li>every action id in the ledger still resolves in the assembled
 *       cluster's layers, so a platform rename fails this build rather than
 *       silently orphaning a row;
 *   <li>every OVERLAID row names a package that really carries twelve locale
 *       files under branding;
 *   <li>every RECORDED row gives a reason a person can disagree with.
 * </ul>
 *
 * <p>The instrument that extends it is the forge's walk-only dialog shot
 * (v2.141.0): {@code -J-Dnmox.shots.dialogs=Category/id=file.png}.
 */
class PlatformDialogLedgerTest {

    /** Where the overlays live. */
    private static final Path BRANDING =
            Paths.get("..", "branding", "src", "main", "nbm-branding", "modules");

    private static final List<String> LOCALES = List.of(
            "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /**
     * One platform surface. {@code actionId} is null for a surface no action
     * opens (a sub-dialog behind a button, or a panel inside another dialog).
     */
    private record Row(String category, String actionId, String what,
                       String overlayPackage, String reason) {

        static Row overlaid(String category, String actionId, String what, String overlayPackage) {
            return new Row(category, actionId, what, overlayPackage, null);
        }

        static Row recorded(String category, String actionId, String what, String reason) {
            return new Row(category, actionId, what, null, reason);
        }
    }

    private static final List<Row> LEDGER = List.of(
            Row.overlaid("Window", "org.netbeans.modules.options.OptionsWindowAction",
                    "Options — chrome, the category strip and the General panel",
                    "org-netbeans-modules-options-api.jar/org/netbeans/modules/options"),
            Row.overlaid("Tools", "org.netbeans.modules.jumpto.file.FileSearchAction",
                    "Go to File",
                    "org-netbeans-modules-jumpto.jar/org/netbeans/modules/jumpto/file"),
            Row.overlaid("Edit", "org.netbeans.modules.search.FindInFilesAction",
                    "Find in Projects",
                    "org-netbeans-api-search.jar/org/netbeans/modules/search"),
            Row.overlaid("Edit", "org.netbeans.modules.search.ReplaceInFilesAction",
                    "Replace in Projects — the same form, so the same overlay",
                    "org-netbeans-api-search.jar/org/netbeans/modules/search"),
            Row.overlaid("System", "org.netbeans.modules.templates.actions.TemplatesAction",
                    "the Templates manager",
                    "org-netbeans-modules-templates.jar/org/netbeans/modules/templates/ui"),
            Row.overlaid("System", "org.netbeans.modules.autoupdate.ui.actions.PluginManagerAction",
                    "the Plugin Manager (v2.141.0)",
                    "org-netbeans-modules-autoupdate-ui.jar/org/netbeans/modules/autoupdate/ui"),
            Row.overlaid("Help", "org.netbeans.core.actions.AboutAction",
                    "About (v2.141.0)",
                    "org-netbeans-core.jar/org/netbeans/core/ui"),
            Row.recorded("Window", null, "the Advanced Proxy dialog behind Options ▸ General ▸ More…",
                    "opened by a BUTTON, not by an action, and the forge's walk-only dialog shot "
                    + "drives actions — photographing it needs an instrument that can press a named "
                    + "button first, so the panel is left English rather than translated unseen."),
            Row.recorded("Window", null, "the interiors of the Editor, Fonts and Colors, and Keymap panels",
                    "each is a deep configuration surface of several hundred keys whose audience is a "
                    + "user changing platform defaults; the category NAMES are overlaid so the strip "
                    + "reads in the reader's language, and the panels are the next walk, not this one."),
            Row.recorded("Edit", "org.netbeans.modules.jumpto.type.GoToType",
                    "Go to Type",
                    "shares the jumpto module with Go to File but has its own bundle package and its "
                    + "own index; not photographed this shift, so nothing is claimed about it."));

    @Test
    @DisplayName("every action the ledger names still resolves in the assembled cluster")
    void theActionsAreRealActions() throws IOException {
        Set<String> registered = registeredActions();
        assertThat(registered).as("actions found in the assembled cluster's layers").isNotEmpty();
        List<String> ghosts = new ArrayList<>();
        for (Row row : LEDGER) {
            if (row.actionId() == null) {
                continue;
            }
            String path = row.category() + "/" + row.actionId().replace('.', '-') + ".instance";
            if (!registered.contains(path)) {
                ghosts.add(path + " (" + row.what() + ")");
            }
        }
        assertThat(ghosts).as("ledger rows naming an action the platform no longer registers").isEmpty();
    }

    @Test
    @DisplayName("every OVERLAID row really carries twelve locale files")
    void theOverlaysExist() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Row row : LEDGER) {
            if (row.overlayPackage() == null) {
                continue;
            }
            Path dir = BRANDING.resolve(row.overlayPackage());
            for (String locale : LOCALES) {
                Path p = dir.resolve("Bundle_" + locale + ".properties");
                if (!Files.isRegularFile(p)) {
                    wrong.add(row.what() + " [" + locale + "]: no overlay at " + p);
                } else if (Files.readString(p, StandardCharsets.ISO_8859_1).isBlank()) {
                    wrong.add(row.what() + " [" + locale + "]: overlay is empty");
                }
            }
        }
        assertThat(wrong).as("ledger rows claiming an overlay that is not there").isEmpty();
    }

    @Test
    @DisplayName("every RECORDED row gives a reason a person can disagree with")
    void theRefusalsSpeak() {
        List<String> thin = new ArrayList<>();
        for (Row row : LEDGER) {
            if (row.reason() == null) {
                continue;
            }
            // a one-clause reason decides nothing (the v2.134.0 lesson, where
            // the ledger gate rejected its own author's "machine tokens")
            if (row.reason().length() < 80 || !row.reason().contains(" ")) {
                thin.add(row.what() + ": " + row.reason());
            }
        }
        assertThat(thin).as("recorded surfaces whose reason does not decide anything").isEmpty();
        assertThat(LEDGER).as("the ledger should hold both verdicts, or it is not a ledger")
                .anyMatch(r -> r.reason() != null)
                .anyMatch(r -> r.overlayPackage() != null);
    }

    /**
     * Every {@code Actions/<category>/<id>.instance} the assembled cluster
     * registers, reconstructed from the layer XML by tracking folder nesting —
     * the population comes from the shipped artifact, never from a list.
     */
    private static Set<String> registeredActions() throws IOException {
        Pattern node = Pattern.compile("<folder\\s+name=\"([^\"]+)\"|</folder>|<file\\s+name=\"([^\"]+)\"");
        Set<String> found = new HashSet<>();
        Path cluster = Paths.get("target", "nmoxstudio");
        assertThat(cluster).as("the assembled cluster").exists();
        try (Stream<Path> jars = Files.walk(cluster)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (JarEntry e : jf.stream().toList()) {
                        String n = e.getName();
                        if (!n.endsWith("layer.xml")) {
                            continue;
                        }
                        String xml;
                        try (InputStream in = jf.getInputStream(e)) {
                            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        Deque<String> stack = new ArrayDeque<>();
                        Matcher m = node.matcher(xml);
                        while (m.find()) {
                            if (m.group(1) != null) {
                                stack.addLast(m.group(1));
                            } else if (m.group(2) != null) {
                                if (!stack.isEmpty() && "Actions".equals(stack.peekFirst())) {
                                    List<String> parts = new ArrayList<>(stack);
                                    parts.remove(0);
                                    parts.add(m.group(2));
                                    found.add(String.join("/", parts));
                                }
                            } else if (!stack.isEmpty()) {
                                stack.removeLast();
                            }
                        }
                    }
                } catch (IOException unreadable) {
                    // a jar we cannot open contributes no actions; the
                    // isNotEmpty assertion above catches a total failure
                }
            }
        }
        return found;
    }
}
