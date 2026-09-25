package org.nmox.studio.editor.blame;

import java.io.File;
import java.util.Locale;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The note itself: what it says, that a stranger's author or summary is
 * painted as text, that it is registered where the status line looks, and
 * that View ▸ Line Blame turns it off.
 */
class BlameStatusLineTest {

    static final long NOW = 1_790_000_000_000L;
    private final Locale saved = Locale.getDefault();

    @AfterEach
    void restore() {
        Locale.setDefault(saved);
        BlamePrefs.setEnabled(true);
        BlameStatusLine.gestureSeen = false;
        BlameStatusLine.GESTURE_WATCH.set(false);
    }

    private static BlamePorcelain.Line line(String author, long secondsAgo, String summary) {
        return new BlamePorcelain.Line(BlamePorcelainTest.B, author, NOW / 1000 - secondsAgo, summary);
    }

    @Test
    @DisplayName("the note reads: author, when · summary")
    void text() {
        Locale.setDefault(Locale.ENGLISH);
        assertThat(BlameStatusLine.text(line("Ada Lovelace", 3 * 86_400, "Fix the parser"), NOW))
                .isEqualTo("Ada Lovelace, 3 days ago · Fix the parser");
        assertThat(BlameStatusLine.text(null, NOW)).isNull();
        BlamePorcelain.Line mine = new BlamePorcelain.Line(BlamePorcelainTest.ZERO,
                "Not Committed Yet", NOW / 1000, "Version of a from a");
        assertThat(BlameStatusLine.text(mine, NOW)).isEqualTo("Not committed yet");
    }

    @Test
    @DisplayName("the tooltip names the commit and the author")
    void tooltip() {
        Locale.setDefault(Locale.ENGLISH);
        String tip = BlameStatusLine.tooltip(line("Ada", 60, "s"));
        assertThat(tip).startsWith("Commit 6bc133a7 by Ada, ").contains("every line");
    }

    @Test
    @DisplayName("a long author or summary is clipped by code points, never splitting an emoji")
    void clip() {
        assertThat(BlameStatusLine.clip("short", 10)).isEqualTo("short");
        String emoji = "ab😀😀😀";
        String cut = BlameStatusLine.clip(emoji, 4);
        assertThat(cut).isEqualTo("ab😀…");
        // the pair before the ellipsis is whole: its low half is there
        assertThat(Character.isLowSurrogate(cut.charAt(cut.length() - 2))).isTrue();
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(4);
        assertThat(BlameStatusLine.clip(null, 3)).isEmpty();
    }

    @Test
    @DisplayName("a stranger's author or summary starting with <html> is painted as text")
    void markupRendersAsText() {
        Locale.setDefault(Locale.ENGLISH);
        BlameStatusLine.Strip strip = new BlameStatusLine.Strip(
                new LineBlame((a, d, t) -> null, new RequestProcessor("unused", 1)));
        File f = new File("x.js");
        strip.show(new LineBlame.Answer(1, f, 1,
                line("<html><img src='http://evil/x'>", 60, "<html><b>pwned</b>")));
        assertThat(strip.isVisible()).isTrue();
        assertThat(BasicHTML.isHTMLString(strip.getText())).isFalse();
        assertThat(strip.getClientProperty(BasicHTML.propertyKey)).isNull();
        assertThat(BasicHTML.isHTMLString(strip.getToolTipText())).isFalse();
        assertThat(strip.getText()).contains("<img src='http://evil/x'>");
        assertThat(strip.shownFile()).isEqualTo(f);
        assertThat(strip.getAccessibleContext().getAccessibleName()).isEqualTo("Line blame");
        // nothing to say: hidden, and nothing to click through to
        strip.show(new LineBlame.Answer(2, f, 1, null));
        assertThat(strip.isVisible()).isFalse();
        assertThat(strip.shownFile()).isNull();
    }

    @Test
    @DisplayName("registered where the status line looks for its elements")
    void registered() {
        assertThat(Lookup.getDefault().lookupAll(StatusLineElementProvider.class))
                .anyMatch(p -> p instanceof BlameStatusLine);
    }

    @Test
    @DisplayName("View ▸ Line Blame flips the preference; it starts on")
    void toggle() {
        BlamePrefs.prefs().remove(BlamePrefs.KEY);
        assertThat(BlamePrefs.enabled()).isTrue();
        ToggleLineBlameAction action = new ToggleLineBlameAction();
        action.actionPerformed(null);
        assertThat(BlamePrefs.enabled()).isFalse();
        assertThat(action.getMenuPresenter().isSelected()).isFalse();
        action.actionPerformed(null);
        assertThat(BlamePrefs.enabled()).isTrue();
    }

    @Test
    @DisplayName("zero processes at boot: a restored, focused editor asks git nothing until the user presses a key or the mouse")
    void nothingBeforeAGesture(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Files.createDirectories(tmp.resolve(".git"));
        java.nio.file.Files.writeString(tmp.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        java.nio.file.Path f = java.nio.file.Files.writeString(tmp.resolve("a.txt"), "x\n");
        org.openide.loaders.DataObject dob = org.openide.loaders.DataObject.find(
                org.openide.filesystems.FileUtil.toFileObject(org.openide.filesystems.FileUtil.normalizeFile(f.toFile())));
        java.util.concurrent.atomic.AtomicInteger spawns = new java.util.concurrent.atomic.AtomicInteger();
        RequestProcessor lane = new RequestProcessor("blame-test", 1);
        BlameStatusLine.Strip strip = new BlameStatusLine.Strip(new LineBlame((argv, dir, t) -> {
            spawns.incrementAndGet();
            return null;
        }, lane));
        javax.swing.JEditorPane pane = new javax.swing.JEditorPane();
        pane.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, dob);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            strip.attach(pane);
            strip.update();
        });
        lane.post(() -> { }).waitFinished();
        assertThat(spawns).as("before a gesture").hasValue(0);
        // the first press anywhere arms it
        int[] armed = {0};
        BlameStatusLine.armOnFirstGesture(() -> armed[0]++);
        javax.swing.JPanel anywhere = new javax.swing.JPanel();
        javax.swing.SwingUtilities.invokeAndWait(() -> anywhere.dispatchEvent(new java.awt.event.MouseEvent(anywhere,
                java.awt.event.MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false)));
        assertThat(BlameStatusLine.gestureSeen).isTrue();
        assertThat(armed[0]).isEqualTo(1);
        javax.swing.SwingUtilities.invokeAndWait(strip::update);
        lane.post(() -> { }).waitFinished();
        assertThat(spawns).as("after it, the line is asked about").hasValue(1);
    }
}
