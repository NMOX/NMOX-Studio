package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clicked-item-wins gate (v1.270.0 arc review). A context menu
 * installed with {@code setComponentPopupMenu} opens on the platform's
 * popup trigger but never moves the selection — while its verbs read
 * {@code getSelectedRow()}/{@code getLastSelectedPathComponent()}/
 * {@code getSelectedValue()}. The review found the mismatch at every
 * popup site in the product: right-click row 3 with row 1 selected and
 * Delete/Forget/Run Script acted on row 1 — sharpest in API Studio,
 * where a request delete has no confirm and wipes the request's
 * keychain token with it.
 *
 * <p>The fix is {@code core.util.Popups.selectOnTrigger}, which
 * selects the item under the trigger before the menu opens (and clears
 * on empty space). This gate makes the pairing a build law: every main
 * source that installs a component popup menu must also install the
 * targeting listener, so a NEW popup site cannot ship the stale-
 * selection bug back in. The scan runs from the application module —
 * the last reactor member — so it sees every sibling module's sources.
 *
 * <p>One shape is exempt, in writing: a PER-ROW menu — one JPopupMenu
 * per row component, its verbs capturing that row's own data in the
 * closure (the Workbench's Forget rows, v1.288.0). There is no
 * selection model a stale click could read, so the property the gate
 * enforces holds by construction and selectOnTrigger has nothing to
 * select. Such a site carries a {@code POPUP-PER-ROW:} comment stating
 * the claim beside the install; the marker is the blessing.
 */
class PopupTargetGateTest {

    @Test
    @DisplayName("every setComponentPopupMenu site targets the clicked item")
    void everyPopupSiteTargetsTheClickedItem() throws IOException {
        Path root = Path.of("..").toRealPath();
        List<String> offenders = new ArrayList<>();
        int sites = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                // Windows walks yield backslash paths — normalize before
                // matching or the filter passes NOTHING and the subject
                // floor fails on exactly one OS (the v1.63.2 class).
                // Filter RELATIVE to the repo root: a git worktree's
                // absolute path carries "/.claude/", which made the dot
                // filter exclude every file and trip the subject floor
                // (v2.15.0). The leading "/" keeps top-level dot-dirs out.
                String s = "/" + root.relativize(p).toString().replace('\\', '/');
                // dot-dirs hold non-build copies (.claude worktrees, .git);
                // only reactor members' main sources are the gate's subjects
                if (!s.endsWith(".java") || !s.contains("src/main/java")
                        || s.contains("/target/") || s.contains("/.")) {
                    continue;
                }
                String src = Files.readString(p);
                if (!src.contains("setComponentPopupMenu(")) {
                    continue;
                }
                sites += src.split("setComponentPopupMenu\\(", -1).length - 1;
                // TWO lawful forms since v1.326.0: the trigger listener,
                // and popupTargetList for DRAG-ENABLED lists where that
                // listener is inert (measured in the shipped 1.325.0 Task
                // Board — an empty-space right-click failed to clear the
                // selection, so Edit…/Delete… were dead on any card the
                // user had not already left-clicked). The law is the same:
                // the menu acts on the item under the pointer.
                if (!src.contains("Popups.selectOnTrigger(")
                        && !src.contains("Popups.popupTargetList(")
                        && !src.contains("POPUP-PER-ROW:")) {
                    offenders.add(root.relativize(p).toString());
                }
            }
        }
        assertThat(offenders)
                .as("a component popup menu that neither installs "
                        + "Popups.selectOnTrigger nor builds its list with "
                        + "Popups.popupTargetList acts on the STALE selection, "
                        + "not the clicked item — use whichever form fits "
                        + "(popupTargetList when the list is drag-enabled)")
                .isEmpty();
        assertThat(sites)
                .as("the gate has subjects (the four v1.270.0 sites exist)")
                .isGreaterThanOrEqualTo(4);
    }
}
