package org.nmox.studio.ui.a11y;

import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toolbar buttons in NMOX's windows are Tab stops (3.4, question 3), under
 * the look and feel that takes them away: FlatLaf ships
 * {@code ToolBar.focusableButtons = false}. The census found 58 such
 * buttons in nine windows; these tests run the real FlatLaf toolbar UI.
 */
class ToolbarKeyboardAccessTest {

    /** Stands in for a studio: an NMOX window by its package. */
    static final class StudioLike extends TopComponent {
    }

    private LookAndFeel saved;

    @BeforeEach
    void flatLaf() throws Exception {
        saved = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new FlatDarkLaf());
    }

    @AfterEach
    void restore() throws Exception {
        UIManager.setLookAndFeel(saved);
    }

    private static JToolBar toolbar(String... names) {
        JToolBar bar = new JToolBar();
        for (String n : names) {
            bar.add(new JButton(n));
        }
        return bar;
    }

    @Test
    @DisplayName("the premise: under FlatLaf a toolbar's buttons cannot take focus")
    void flatLafTakesFocusAway() {
        JToolBar bar = toolbar("RUN", "EXPLAIN");
        assertThat(bar.getComponent(0).isFocusable()).isFalse();
    }

    @Test
    @DisplayName("a studio's toolbar buttons become Tab stops, and ones added later too, without stealing a click's focus")
    void studioToolbarBecomesFocusable() {
        StudioLike studio = new StudioLike();
        studio.setLayout(new BorderLayout());
        JPanel north = new JPanel(new BorderLayout());
        JToolBar bar = toolbar("RUN", "EXPLAIN", "Cancel");
        JToggleButton toggle = new JToggleButton("Auto");
        bar.add(toggle);
        north.add(bar);
        studio.add(north, BorderLayout.NORTH);

        ToolbarKeyboardAccess.installUnder(studio);

        for (java.awt.Component c : bar.getComponents()) {
            assertThat(c.isFocusable()).as(((javax.swing.AbstractButton) c).getText()).isTrue();
            assertThat(((javax.swing.JComponent) c).isRequestFocusEnabled())
                    .as("a mouse press keeps focus where it was").isFalse();
        }
        JButton later = new JButton("Save…");
        bar.add(later);
        assertThat(later.isFocusable()).as("a button added after the repair").isTrue();
        assertThat(later.isRequestFocusEnabled()).isFalse();

        // the look and feel re-installs its UI (a theme change) and keeps them
        bar.updateUI();
        assertThat(bar.getComponent(0).isFocusable()).isTrue();
    }

    @Test
    @DisplayName("a button that asks to stay out of the Tab order keeps its choice (the rack's rear flip, which Tab itself does)")
    void skipIsKept() {
        StudioLike studio = new StudioLike();
        JToolBar bar = toolbar("RUN");
        JToggleButton flip = new JToggleButton("REAR");
        flip.setFocusable(false);
        flip.putClientProperty(ToolbarKeyboardAccess.SKIP, Boolean.TRUE);
        bar.add(flip);
        studio.add(bar);
        ToolbarKeyboardAccess.installUnder(studio);
        assertThat(bar.getComponent(0).isFocusable()).isTrue();
        assertThat(flip.isFocusable()).isFalse();
    }

    @Test
    @DisplayName("a toolbar that joins an NMOX window after it opened is found by the add")
    void lateToolbarIsFound() {
        StudioLike studio = new StudioLike();
        JToolBar bar = toolbar("Compile");
        JPanel holder = new JPanel();
        holder.add(bar);
        studio.add(holder);
        ToolbarKeyboardAccess.added(studio, holder);
        assertThat(bar.getComponent(0).isFocusable()).isTrue();
    }

    @Test
    @DisplayName("a window that is not NMOX's keeps its toolbar as the platform built it")
    void foreignWindowsAreLeftAlone() {
        TopComponent foreign = new TopComponent();
        JToolBar bar = toolbar("Refresh");
        foreign.add(bar);
        ToolbarKeyboardAccess.added(foreign, bar);
        assertThat(bar.getComponent(0).isFocusable()).isFalse();
        assertThat(bar.getClientProperty(ToolbarKeyboardAccess.INSTALLED)).isNull();
    }

    @Test
    @DisplayName("an existing FlatLaf style is kept, the switch appended")
    void styleMerges() {
        assertThat(ToolbarKeyboardAccess.withStyle(null)).isEqualTo(ToolbarKeyboardAccess.STYLE);
        assertThat(ToolbarKeyboardAccess.withStyle("margin: 2,2,2,2"))
                .isEqualTo("margin: 2,2,2,2; " + ToolbarKeyboardAccess.STYLE);
        Object merged = ToolbarKeyboardAccess.withStyle(Map.of("margin", "2"));
        assertThat(merged).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) merged).get("margin")).isEqualTo("2");
        assertThat(((Map<?, ?>) merged).get("focusableButtons")).isEqualTo(Boolean.TRUE);
    }

    private static final String[] MODULES = {"core", "editor", "tools", "rack", "project",
        "ui", "apiclient", "dbstudio", "web3", "infra"};

    /**
     * The population the seam must reach, derived: every production file
     * that builds a toolbar. The seam reaches toolbars inside NMOX
     * TopComponents; a toolbar built anywhere else (a dialog, a floating
     * window) would stay unreachable by Tab under FlatLaf, so it fails
     * here until it is covered or written down.
     */
    @Test
    @DisplayName("census: every toolbar the product builds lives in an NMOX window the seam reaches")
    void everyToolbarIsInAWindowTheSeamReaches() throws IOException {
        List<String> builders = new ArrayList<>();
        List<String> outside = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    if (!text.contains("new JToolBar(") && !text.contains("new javax.swing.JToolBar(")) {
                        continue;
                    }
                    String name = module + "/" + p.getFileName();
                    builders.add(name);
                    if (!text.contains("extends TopComponent")
                            || !text.contains("package org.nmox.")) {
                        outside.add(name);
                    }
                }
            }
        }
        // the census found eight windows in 3.4; a smaller number means the
        // scan stopped reading, not that toolbars vanished
        assertThat(builders).as("toolbar-building files").hasSizeGreaterThanOrEqualTo(8);
        assertThat(outside).as("toolbars outside an NMOX TopComponent").isEmpty();
    }
}
