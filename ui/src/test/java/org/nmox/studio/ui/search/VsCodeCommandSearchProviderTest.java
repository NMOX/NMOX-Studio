package org.nmox.studio.ui.search;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick Search answers in VS Code's words. The platform's actions provider
 * needs the whole query as one substring of an action's name, so a
 * switcher typing "toggle terminal" or "git commit" found nothing; these
 * rows carry VS Code's titles through the product's one matcher.
 */
class VsCodeCommandSearchProviderTest {

    private static Action named(String name, boolean enabled) {
        Action a = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        a.setEnabled(enabled);
        return a;
    }

    /** Every table row resolves to an enabled action called after its id's last segment. */
    private static final VsCodeCommandSearchProvider.Resolver ALL = (category, id) ->
            named(id.substring(id.lastIndexOf('.') + 1), true);

    private static List<String> titles(String query, VsCodeCommandSearchProvider.Resolver r) {
        return VsCodeCommandSearchProvider.hits(query, r).stream().map(h -> h.cmd().title()).toList();
    }

    @Test
    @DisplayName("VS Code's palette words find the action that does the same thing here")
    void palettewordsFindTheirAction() {
        assertThat(titles("toggle terminal", ALL)).containsExactly("View: Toggle Terminal");
        assertThat(titles("git commit", ALL)).containsExactly("Git: Commit");
        assertThat(titles("settings", ALL)).containsExactly("Preferences: Open Settings");
        assertThat(titles("keyboard shortcuts", ALL)).containsExactly("Preferences: Open Keyboard Shortcuts");
        assertThat(titles("problems", ALL)).containsExactly("View: Toggle Problems");
        assertThat(titles("install extensions", ALL)).containsExactly("Extensions: Install Extensions");
        assertThat(titles("screencast", ALL)).containsExactly("Developer: Toggle Screencast Mode");
        assertThat(titles("terminal", ALL)).contains("View: Toggle Terminal", "Terminal: Create New Terminal");
    }

    @Test
    @DisplayName("the label is VS Code's title, then the action's own name without mnemonic or ellipsis")
    void theLabelTeachesTheName() {
        VsCodeCommandSearchProvider.Resolver r = (c, id) -> named("&Options...", true);
        var hit = VsCodeCommandSearchProvider.hits("open settings", r).get(0);
        assertThat(hit.label()).isEqualTo("Preferences: Open Settings — Options");
    }

    @Test
    @DisplayName("an action that is missing or disabled is not offered - a row that ran nothing would fail in silence")
    void missingOrDisabledIsNotOffered() {
        assertThat(titles("git commit", (c, id) -> null)).isEmpty();
        assertThat(titles("git commit", (c, id) -> named("Commit", false))).isEmpty();
    }

    @Test
    @DisplayName("a blank or one-letter query offers nothing")
    void shortQueriesOfferNothing() {
        assertThat(titles("", ALL)).isEmpty();
        assertThat(titles(" g ", ALL)).isEmpty();
        assertThat(titles(null, ALL)).isEmpty();
    }

    @Test
    @DisplayName("choosing a hit runs its action on the EDT, and not once it went disabled")
    void choosingAHitRunsTheAction() throws Exception {
        java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean onEdt = new java.util.concurrent.atomic.AtomicBoolean();
        Action a = new AbstractAction("Commit") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ran.incrementAndGet();
                onEdt.set(java.awt.EventQueue.isDispatchThread());
            }
        };
        var hit = VsCodeCommandSearchProvider.hits("git commit", (c, id) -> a).get(0);
        VsCodeCommandSearchProvider.runner(hit).run();
        java.awt.EventQueue.invokeAndWait(() -> { });
        assertThat(ran.get()).isEqualTo(1);
        assertThat(onEdt.get()).as("run on the EDT").isTrue();
        a.setEnabled(false);
        VsCodeCommandSearchProvider.runner(hit).run();
        java.awt.EventQueue.invokeAndWait(() -> { });
        assertThat(ran.get()).as("a disabled action is not run").isEqualTo(1);
    }

    @Test
    @DisplayName("an editor row runs the kit's action against the editor's own pane, under the kit's own name")
    void editorRowsRunAgainstThePane() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Object> source = new java.util.concurrent.atomic.AtomicReference<>();
        Action kit = new AbstractAction("format") {
            @Override
            public void actionPerformed(ActionEvent e) {
                source.set(e.getSource());
            }
        };
        kit.putValue(Action.SHORT_DESCRIPTION, "Format");
        javax.swing.JEditorPane pane = new javax.swing.JEditorPane();
        Action bound = VsCodeCommandSearchProvider.bound(kit, pane);
        assertThat(bound.getValue(Action.NAME)).isEqualTo("Format");
        assertThat(bound.isEnabled()).as("a pane that is not on screen is no target").isFalse();
        bound.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(source.get()).as("the pane is the event's source, as for a keystroke").isSameAs(pane);

        assertThat(titles("format document", (c, id) -> VsCodeCommandSearchProvider.EDITOR_KIT.equals(c)
                && "format".equals(id) ? named("Format", true) : null)).containsExactly("Format Document");
        assertThat(titles("format document", (c, id) -> null)).as("no editor, no row").isEmpty();
    }

    @Test
    @DisplayName("every title finds its own row, and no title appears twice")
    void everyRowIsReachable() {
        Set<String> seen = new HashSet<>();
        Map<String, Integer> byTitle = new HashMap<>();
        for (VsCodeCommandSearchProvider.Cmd c : VsCodeCommandSearchProvider.COMMANDS) {
            assertThat(seen.add(c.title())).as("title %s appears once", c.title()).isTrue();
            assertThat(titles(c.title(), ALL)).as("typing %s finds it", c.title()).contains(c.title());
            byTitle.merge(c.title(), 1, Integer::sum);
        }
        assertThat(byTitle).hasSizeGreaterThanOrEqualTo(40);
    }
}
