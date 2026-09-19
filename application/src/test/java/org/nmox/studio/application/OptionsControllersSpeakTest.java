package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.spi.options.OptionsPanelController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Options panel tells the dialog when it changes, or Apply never lights up.
 *
 * <p>Half of the platform's contract was implemented in all three of this
 * product's panels and the other half was missing. Each one has a correct
 * {@code isChanged()}; not one of them ever fired {@code PROP_CHANGED}, and a
 * grep for {@code firePropertyChange} across all 910 product files returned
 * only accessibility notifications on rack widgets. Read from the shipped
 * bytecode, {@code OptionsDisplayerImpl} listens for that property on the
 * controller and calls back to decide whether Apply is enabled — so with no
 * fire, Apply stayed grey however many settings you touched and the only way
 * out of the dialog was OK.
 *
 * <p>Nothing could have caught it from inside a module: each panel looked
 * complete on its own, and the missing half is a conversation with the host.
 * The population is derived from the sources — every class implementing
 * {@code OptionsPanelController} — so a fourth panel is held to the same rule
 * the day it is written.
 *
 * <p>The last law is BEHAVIOURAL, and it had to become so. Its first version
 * asked the source whether a listener was registered anywhere in the file,
 * which a listener doing nothing satisfies — and, more to the point, which a
 * panel satisfies when it wires two of its six controls. That is what shipped:
 * the Rack panel's KVASIR provider combo and its three cloud-token fields told
 * the dialog nothing, so a user who picked a provider or pasted a token and
 * touched nothing else still found Apply grey. The law now builds the real
 * panel each controller hands the dialog, listens the way the dialog listens,
 * moves every control in it, and asks whether the fire arrived — the outcome
 * rather than the mechanism (v2.19.1).
 */
class OptionsControllersSpeakTest {

    private static final List<Path> MODULES = List.of(
            Path.of("../core"), Path.of("../editor"), Path.of("../tools"),
            Path.of("../rack"), Path.of("../project"), Path.of("../ui"),
            Path.of("../infra"), Path.of("../apiclient"), Path.of("../dbstudio"),
            Path.of("../web3"));

    /** The fully-qualified name of the class in {@code src/main/java/…/X.java}. */
    private static String className(Path source) {
        String s = source.toString().replace('\\', '/');
        int at = s.indexOf("src/main/java/");
        return s.substring(at + "src/main/java/".length())
                .replace(".java", "").replace('/', '.');
    }

    /** Every product class that implements the platform's Options contract. */
    private static List<Path> controllers() throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path module : MODULES) {
            Path src = module.resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(src)) {
                for (Path p : s.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(p, StandardCharsets.UTF_8);
                    if (body.contains("extends OptionsPanelController")
                            || body.contains("extends org.netbeans.spi.options.OptionsPanelController")) {
                        found.add(p);
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("the product really has Options panels, so the law below is not vacuously green")
    void thePopulationIsFound() throws IOException {
        assertThat(controllers())
                .as("no OptionsPanelController found — the match stopped working, "
                        + "and a gate over an empty population proves nothing")
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("every Options panel fires PROP_CHANGED — an isChanged() nobody asks is half a contract")
    void everyControllerFiresWhenItChanges() throws IOException {
        List<String> silent = new ArrayList<>();
        for (Path p : controllers()) {
            String body = GateSources.stripComments(
                    Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            boolean fires = body.contains("firePropertyChange")
                    && body.contains("PROP_CHANGED");
            if (!fires) {
                silent.add(p.getFileName().toString());
            }
        }
        assertThat(silent)
                .as("these implement isChanged() and never tell the dialog, so Apply stays grey")
                .isEmpty();
    }

    /**
     * A control the user can move, and how to move it.
     *
     * <p>These are leaves: a {@code JSpinner} owns an editor that owns a text
     * field, and a {@code JComboBox} owns a button — moving those would be
     * moving the same control twice and calling it two.
     */
    private static boolean drive(java.awt.Component c) {
        if (c instanceof javax.swing.JComboBox<?> combo) {
            if (combo.getItemCount() < 2) {
                return false; // nothing to pick that is not already picked
            }
            combo.setSelectedIndex((combo.getSelectedIndex() + 1) % combo.getItemCount());
            return true;
        }
        if (c instanceof javax.swing.JSpinner spinner) {
            Object next = spinner.getModel().getNextValue();
            spinner.setValue(next != null ? next : spinner.getModel().getPreviousValue());
            return true;
        }
        if (c instanceof javax.swing.AbstractButton button) {
            button.setSelected(!button.isSelected());
            return true;
        }
        if (c instanceof javax.swing.text.JTextComponent text) {
            text.setText(text.getText() + "x");
            return true;
        }
        return false;
    }

    /** Every control a user can move, without descending into a control's own parts. */
    private static void controls(java.awt.Container in, List<java.awt.Component> found) {
        for (java.awt.Component c : in.getComponents()) {
            if (c instanceof javax.swing.JComboBox<?> || c instanceof javax.swing.JSpinner
                    || c instanceof javax.swing.AbstractButton
                    || c instanceof javax.swing.text.JTextComponent) {
                found.add(c);
            } else if (c instanceof java.awt.Container child) {
                controls(child, found);
            }
        }
    }

    @Test
    @DisplayName("moving ANY control in a real panel really fires PROP_CHANGED — the outcome, not a listener that might do nothing")
    void everyControlReachesTheFire() throws Exception {
        // The structural version of this law asked only that a listener be
        // REGISTERED somewhere in the file. A controller that adds a listener
        // doing nothing passed it; so did a controller that wired two of its
        // six controls, which is what shipped — the KVASIR provider combo and
        // three cloud-token fields left Apply grey (v2.19.1: gate the OUTCOME,
        // not the mechanism). This drives the real controller, through the
        // real panel it hands the dialog, and listens the way the dialog does.
        List<String> silent = new ArrayList<>();
        int moved = 0;
        for (Path p : controllers()) {
            Class<?> type = Class.forName(className(p));
            OptionsPanelController controller =
                    (OptionsPanelController) type.getDeclaredConstructor().newInstance();
            List<java.awt.Component> found = new ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger fires =
                    new java.util.concurrent.atomic.AtomicInteger();
            // Swing on the EDT, like the dialog that really shows these
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                javax.swing.JComponent panel = controller.getComponent(org.openide.util.Lookup.EMPTY);
                controller.addPropertyChangeListener(e -> {
                    if (OptionsPanelController.PROP_CHANGED.equals(e.getPropertyName())) {
                        fires.incrementAndGet();
                    }
                });
                controls(panel, found);
            });
            assertThat(found).as("%s builds a panel with no controls at all",
                    p.getFileName()).isNotEmpty();
            for (java.awt.Component c : found) {
                int before = fires.get();
                boolean[] driven = new boolean[1];
                javax.swing.SwingUtilities.invokeAndWait(() -> driven[0] = drive(c));
                if (!driven[0]) {
                    continue;
                }
                moved++;
                if (fires.get() == before) {
                    // the index distinguishes controls that carry no name of
                    // their own, which is how three identical token fields
                    // would otherwise read as one complaint said three times
                    silent.add(p.getFileName() + " · #" + found.indexOf(c) + " "
                            + c.getClass().getSimpleName() + " " + name(c));
                }
            }
        }
        assertThat(moved)
                .as("nothing was moved, so this proves nothing — the driver stopped "
                        + "recognising the controls these panels are built from")
                .isGreaterThanOrEqualTo(6);
        assertThat(silent)
                .as("moving these told the dialog nothing, so Apply stays grey for "
                        + "a user who changed only that")
                .isEmpty();
    }

    /** Whatever names a control for a human — the accessible name, else its text. */
    private static String name(java.awt.Component c) {
        javax.accessibility.AccessibleContext ctx = c.getAccessibleContext();
        String n = ctx == null ? null : ctx.getAccessibleName();
        if (n == null || n.isBlank()) {
            n = c instanceof javax.swing.AbstractButton b ? b.getText() : "";
        }
        return n == null ? "" : n;
    }
}
