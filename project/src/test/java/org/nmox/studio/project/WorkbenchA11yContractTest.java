package org.nmox.studio.project;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack's name law (v1.41.0: every control on every faceplate has an
 * accessible name, DeviceContractTest) applied to the Workbench (v2.73.0):
 * every button the home base paints — with and without something running —
 * carries a non-blank accessible name, so the page reads the same to a
 * screen reader as to the eye. A JButton's name falls back to its text,
 * so this contract is the FLOOR (nothing nameless); the per-run names
 * ("Stop Run — shop", not a bare "Stop") are pinned by
 * WorkbenchRunningRowsTest, which finds the buttons BY those names — the
 * mutant that drops the explicit name dies there, not here.
 */
class WorkbenchA11yContractTest {

    @AfterEach
    void drain() {
        LiveRuns.stopAll(); // a fixture killer has no process whose exit would remove it
        LiveRuns.clearForTest();
    }

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    @Test
    @DisplayName("every button on the Workbench is named, idle and with runs on the page")
    void everyButtonIsNamed() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        LiveRuns.add(new LiveRuns.Run("ide-run:/tmp/a11y#1", "Run — a11y", () -> { }));
        // the run's row arrives through a coalesced refresh the EDT may be
        // mid-way through (the v2.82.0 convergence law): poll for the row's
        // own Stop button, so the contract really covers it, never count drains
        List<Component> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            all.clear();
            collect(tc[0], all);
            if (all.stream().anyMatch(c -> c instanceof AbstractButton b
                    && "Stop Run — a11y".equals(b.getAccessibleContext().getAccessibleName()))) {
                break;
            }
            Thread.sleep(20);
        }
        assertThat(all.stream().anyMatch(c -> c instanceof AbstractButton b
                && "Stop Run — a11y".equals(b.getAccessibleContext().getAccessibleName())))
                .as("the run's row (and its Stop) is on the page, so the contract covers it").isTrue();
        List<String> unnamed = new ArrayList<>();
        int buttons = 0;
        for (Component c : all) {
            // the look-and-feel's own chrome (a scroll pane's arrow buttons,
            // javax.swing.plaf.*) is not ours to name — the platform names it
            if (c instanceof AbstractButton b && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                buttons++;
                String name = b.getAccessibleContext().getAccessibleName();
                if (name == null || name.isBlank()) {
                    unnamed.add(b.getClass().getSimpleName() + " '" + b.getText() + "'");
                }
            }
        }
        assertThat(buttons).as("the page has buttons to name (the RUNNING row's Stop at least)").isPositive();
        assertThat(unnamed).as("a button without an accessible name is invisible to assistive technology").isEmpty();
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }

    @Test
    @DisplayName("text the Workbench had to cut is still readable in full somewhere")
    void cutTextIsNeverLost() throws Exception {
        // v2.119.0, the fresh-install walk: the TOOLING descriptions were
        // being middle-elided into gibberish, and the only full copy lived
        // in the title button's accessible name — where a sighted user
        // cannot get at it. The v1.282.0 law one surface over: a widget
        // that has to cut its text carries the whole of it as a tooltip.
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        List<Component> all = new ArrayList<>();
        // a dock narrower than its longest subtitle: what is cut depends on
        // the width since 3.5.2, so the page is given one and laid out
        SwingUtilities.invokeAndWait(() -> {
            layOut(tc[0], 230, 900);
            collect(tc[0], all);
        });
        List<String> silent = new ArrayList<>();
        int cut = 0;
        for (Component c : all) {
            // the POPULATION is what the label says it shortened, not what
            // happens to end in an ellipsis: "detecting…" is a progress
            // label that was never cut, and the first cut of this gate
            // failed the Windows lane for exactly that reason
            if (c instanceof org.nmox.studio.core.util.FitLabel l && l.isCut()) {
                cut++;
                String full = l.getToolTipText();
                if (full == null || !full.strip().equals(l.getFull())) {
                    silent.add("'" + l.getText() + "'");
                }
                assertThat(l.getText()).as("what is shown is shorter and says so").contains("…");
            }
        }
        assertThat(silent)
                .as("a row that shows an ellipsis and offers no way to read the rest "
                        + "has simply lost the text")
                .isEmpty();
        assertThat(cut).as("a narrow Workbench paints at least one shortened subtitle "
                + "(English's own longest is 51 characters), "
                + "or this gate is measuring nothing").isPositive();
    }

    @Test
    @DisplayName("a subtitle is whole when the row has room for it, however long it is")
    void roomIsUsed() throws Exception {
        // the 3.5 walks, all three systems: "devices, cables, pipelines - Tab
        // flip…" and "DigitalOcean · Hetzner · Cloudflare f…" beside empty
        // space, because the cut was a count of characters
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            layOut(tc[0], 900, 900);
            collect(tc[0], all);
        });
        List<org.nmox.studio.core.util.FitLabel> subtitles = new ArrayList<>();
        for (Component c : all) {
            if (isSubtitle(c) && c instanceof org.nmox.studio.core.util.FitLabel l) {
                subtitles.add(l);
            }
        }
        assertThat(subtitles).as("the tooling rows have subtitles").hasSizeGreaterThanOrEqualTo(4);
        assertThat(subtitles).as("at least one is longer than the old 38-character cut")
                .anyMatch(l -> l.getFull().length() > 38);
        assertThat(subtitles).as("a tooling row's subtitle is given the row's spare width, not only what it asked for")
                .anyMatch(l -> l.getWidth() > l.getPreferredSize().width * 2);
        assertThat(subtitles).allSatisfy(l -> {
            assertThat(l.isCut()).as(l.getFull()).isFalse();
            assertThat(l.getText().strip()).isEqualTo(l.getFull());
            assertThat(l.getToolTipText()).as("nothing cut, nothing to say: the row's own tooltip shows").isNull();
        });
    }

    @Test
    @DisplayName("a wide subtitle never widens the dock: it asks for no more than the old budget")
    void theDockIsNoWider() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> collect(tc[0], all));
        for (Component c : all) {
            if (isSubtitle(c) && c instanceof org.nmox.studio.core.util.FitLabel l) {
                int budget = l.getFontMetrics(l.getFont())
                        .stringWidth("n".repeat(ProjectExplorerTopComponent.SUBTITLE_ASKS_FOR));
                assertThat(l.getPreferredSize().width).as(l.getFull()).isLessThanOrEqualTo(budget);
                assertThat(l.getMinimumSize().width).isZero();
            }
        }
    }

    @Test
    @DisplayName("a row is one target: its subtitle opens it too, cut or whole")
    void theSubtitleOpensTheRow() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            layOut(tc[0], 230, 900);
            collect(tc[0], all);
        });
        int subtitles = 0;
        for (Component c : all) {
            if (isSubtitle(c) && c instanceof org.nmox.studio.core.util.FitLabel l) {
                subtitles++;
                java.awt.Container row = l.getParent();
                // a label with a tooltip is the pointer's target, and events
                // do not travel on to its parent: whatever the row hears, the
                // subtitle must hear (the tooltip manager aside, which listens
                // wherever there is a tooltip and is not the row's)
                List<java.awt.event.MouseListener> rows = new ArrayList<>(java.util.Arrays.asList(row.getMouseListeners()));
                rows.removeIf(m -> m instanceof javax.swing.ToolTipManager);
                assertThat(rows).as("the row listens for the pointer and for a click").hasSizeGreaterThanOrEqualTo(2);
                assertThat(l.getMouseListeners()).as(l.getFull()).containsAll(rows);
                // and the row lights up under the pointer wherever on it the pointer is
                java.awt.Color resting = row.getBackground();
                l.dispatchEvent(new java.awt.event.MouseEvent(l, java.awt.event.MouseEvent.MOUSE_ENTERED,
                        System.currentTimeMillis(), 0, 2, 2, 0, false));
                assertThat(row.getBackground()).as("lit under the subtitle").isNotEqualTo(resting);
                l.dispatchEvent(new java.awt.event.MouseEvent(l, java.awt.event.MouseEvent.MOUSE_EXITED,
                        System.currentTimeMillis(), 0, 2, 2, 0, false));
                assertThat(row.getBackground()).isEqualTo(resting);
            }
        }
        assertThat(subtitles).isPositive();
    }

    @Test
    @DisplayName("the page follows the dock's width down to what cannot shrink, and scrolls only below that")
    void thePageFollowsTheDock() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(() -> {
            List<Component> all = new ArrayList<>();
            layOut(tc[0], 230, 900);
            collect(tc[0], all);
            ProjectExplorerTopComponent.Page page = (ProjectExplorerTopComponent.Page) all.stream()
                    .filter(c -> c instanceof ProjectExplorerTopComponent.Page).findFirst().orElseThrow();
            javax.swing.JViewport viewport = (javax.swing.JViewport) page.getParent();
            assertThat(page.getPreferredSize().width).as("the rows ask for more than this dock has")
                    .isGreaterThan(viewport.getWidth());
            assertThat(page.getScrollableTracksViewportWidth()).isTrue();
            assertThat(page.getWidth()).as("and are given the dock's width, not their own")
                    .isEqualTo(viewport.getWidth());

            layOut(tc[0], 40, 900);
            assertThat(page.getScrollableTracksViewportWidth())
                    .as("narrower than the titles: nothing left to shorten, so it scrolls").isFalse();
        });
    }

    @Test
    @DisplayName("a project row's subtitle becomes what the folder holds, cut as a list and not as the path it replaces")
    void kindsAreAList() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            org.nmox.studio.core.util.FitLabel sub = new org.nmox.studio.core.util.FitLabel(
                    org.nmox.studio.core.util.FitLabel.Cut.MIDDLE, 200, true);
            javax.swing.JPanel row = new javax.swing.JPanel();
            row.add(sub);
            sub.setFull("/Users/someone/code");
            sub.setBounds(0, 0, 90, 16);
            sub.dispatchEvent(new java.awt.event.ComponentEvent(sub, java.awt.event.ComponentEvent.COMPONENT_RESIZED));

            ProjectExplorerTopComponent.showKinds(sub, List.of("node", "typescript", "docker"));

            assertThat(sub.getFull()).isEqualTo("node · typescript · docker");
            assertThat(sub.getText()).as("too narrow for all three: the beginning stays, whole names only")
                    .startsWith("node").endsWith("…").doesNotContain("docker");

            ProjectExplorerTopComponent.showKinds(sub, List.of());
            assertThat(sub.getFull()).as("nothing detected leaves what was there").isEqualTo("node · typescript · docker");
        });
    }

    @Test
    @DisplayName("a subtitle longer than its budget asks for the budget, takes more when there is more, and is cut as its kind")
    void aLongSubtitle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String sentence = "Container, Images, Speicherplatz zurückgewinnen, als Docker-Image verpacken und starten";
            org.nmox.studio.core.util.FitLabel prose = ProjectExplorerTopComponent.subtitleLabel(
                    sentence, ProjectExplorerTopComponent.Sub.PROSE);
            int budget = prose.getFontMetrics(prose.getFont())
                    .stringWidth("n".repeat(ProjectExplorerTopComponent.SUBTITLE_ASKS_FOR));
            int whole = prose.getFontMetrics(prose.getFont()).stringWidth(sentence);
            assertThat(whole).as("the fixture is wider than the budget").isGreaterThan(budget);
            assertThat(prose.getPreferredSize().width).as("asks the dock for the old budget and no more")
                    .isLessThanOrEqualTo(budget).isGreaterThan(budget / 2);
            assertThat(prose.getMaximumSize().width).as("takes the row's spare width").isGreaterThan(whole);

            prose.setBounds(0, 0, budget, 16);
            prose.dispatchEvent(new java.awt.event.ComponentEvent(prose, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            assertThat(prose.getText()).as("a sentence keeps its beginning").startsWith("Container, Images").endsWith("…");

            org.nmox.studio.core.util.FitLabel path = ProjectExplorerTopComponent.subtitleLabel(
                    "/Users/someone/a/very/deep/folder/of/many/projects/and/their/checkouts/shop",
                    ProjectExplorerTopComponent.Sub.PATH);
            path.setBounds(0, 0, 150, 16);
            path.dispatchEvent(new java.awt.event.ComponentEvent(path, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            assertThat(path.getText()).as("a path keeps its ends").startsWith("/Users").endsWith("shop").contains("…");
        });
    }

    /** A row's subtitle: a fitted label inside a row, which the header's path and an empty section's hint are not. */
    private static boolean isSubtitle(Component c) {
        return c instanceof org.nmox.studio.core.util.FitLabel
                && !(c instanceof org.nmox.studio.core.util.PathLabel)
                && !(c.getParent() instanceof ProjectExplorerTopComponent.Page);
    }

    /** Gives the window a size and lays out everything in it; a window never shown lays out nothing by itself. */
    private static void layOut(Container root, int width, int height) {
        root.setSize(width, height);
        // twice: a label re-cuts its text when it learns its width, which changes what its row asks for
        for (int pass = 0; pass < 2; pass++) {
            layOutTree(root);
        }
    }

    private static void layOutTree(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof org.nmox.studio.core.util.FitLabel l) {
                l.dispatchEvent(new java.awt.event.ComponentEvent(l, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            }
            if (child instanceof Container cc) {
                layOutTree(cc);
            }
        }
    }
}
