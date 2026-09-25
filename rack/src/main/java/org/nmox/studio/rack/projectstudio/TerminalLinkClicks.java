package org.nmox.studio.rack.projectstudio;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleText;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.engine.FileLink;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.OnShowing;

/**
 * ⌘-click ({@code Ctrl}-click off the Mac) on {@code src/app.ts:42:7} in the
 * Terminal opens the file at that line (3.2) - what a VS Code user's hand
 * already does to a tsc error, a jest frame or a rustc {@code -->} line.
 *
 * <p><b>Why this seam.</b> Read from the shipped RELEASE310 bytecode: the
 * platform's terminal does follow links, but only regions a program writes
 * with NetBeans' own escape ({@code ESC ] 10 ; url ; text BEL} -
 * {@code ActiveTerm.hyperlink} makes a link region, and the terminal
 * module's {@code Terminal$6} splits {@code path:line} on a plain click and
 * calls {@code OpenInEditorAction.post}). No tool prints that escape, and
 * OSC 8 is not recognised at all. The Term class that could be taught more
 * ({@code LineFilter.pushInto}, {@code Term.addListener}) lives in
 * {@code org.netbeans.lib.terminalemulator}, a FRIEND-only module this
 * product is not a friend of, and the component is created privately by
 * {@code TerminalContainerTopComponent}. So nothing here compiles against
 * the terminal and nothing reflects into it: the terminal's screen already
 * answers the public {@link AccessibleText} contract a screen reader uses -
 * {@code getIndexAtPoint}, and {@code getAtIndex(SENTENCE, i)} for the row
 * (the terminal's own word, {@code WORD}, when the row reader fails) - and
 * that is the whole door. Proven against the real RELEASE310 {@code Term}
 * fed tsc, node and rustc lines, with and without {@code -ea}.
 *
 * <p>One {@link AWTEventListener} for mouse events, installed once the
 * window is up; every event that is not a single modifier-click on a
 * terminal screen costs two integer tests. The file is looked at off the
 * EDT (a stat), the editor opened on it. A link to nothing is refused on
 * the status line, naming the path it tried, so a relative path that the
 * shell's own {@code cd} made relative to somewhere else says where it was
 * looked for rather than failing silently.
 */
@OnShowing
@Messages({
    "# {0} - the path the click named, resolved",
    "TerminalLinks_notAFile=Not a file: {0}",
    "# {0} - the relative path the click named",
    "TerminalLinks_noProject={0} is a relative path, and no project is open to find it in"
})
public final class TerminalLinkClicks implements Runnable {

    /** The terminal emulator's component, by name: the module is friend-only, so no import. */
    static final String TERM_CLASS = "org.netbeans.lib.terminalemulator.Term";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final RequestProcessor RP = new RequestProcessor("nmox-terminal-links", 1);

    @Override
    public void run() {
        if (GraphicsEnvironment.isHeadless() || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(new Listener(), AWTEvent.MOUSE_EVENT_MASK);
    }

    /**
     * The gesture: one click of the first button with the platform's menu
     * modifier (⌘ on macOS, Ctrl elsewhere) and no Alt. Pure.
     */
    static boolean isLinkGesture(int id, int button, int clickCount, int modifiersEx, int menuMaskEx) {
        return id == MouseEvent.MOUSE_CLICKED
                && button == MouseEvent.BUTTON1
                && clickCount == 1
                && (modifiersEx & menuMaskEx) != 0
                && (modifiersEx & InputEvent.ALT_DOWN_MASK) == 0;
    }

    /** True when {@code c} or an ancestor is the terminal emulator (a subclass counts). */
    static boolean insideTerm(Component c) {
        for (Component k = c; k != null; k = k.getParent()) {
            for (Class<?> t = k.getClass(); t != null; t = t.getSuperclass()) {
                if (TERM_CLASS.equals(t.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The link under {@code p} on a screen that answers {@link AccessibleText}
     * the way the terminal's does: positions run through the buffer, each
     * row's characters followed by one position for its line break, and
     * {@code getAtIndex(SENTENCE, i)} gives the row holding {@code i}. The
     * row's start is the index of the same row's left edge, so the clicked
     * column is the difference. When that column cannot be trusted (the
     * clicked character is not the row's character there - a scrolled or
     * odd buffer), the row's only link is taken, never a guess between two.
     */
    static TerminalLinks.Link linkAt(AccessibleText text, Point p) {
        int index = text.getIndexAtPoint(p);
        if (index < 0) {
            return null;
        }
        int rowStart = text.getIndexAtPoint(new Point(0, p.y));
        String row = rowText(text, rowStart < 0 ? index : rowStart);
        if (row == null) {
            // the row reader failed (the emulator's Line.toString asserts, so a
            // JVM run with -ea lands here): the word under the pointer, read by
            // the terminal's own word rule (textWithin), is the next best text
            return TerminalLinks.at(safe(() -> text.getAtIndex(AccessibleText.WORD, index)), -1);
        }
        int col = rowStart < 0 ? -1 : index - rowStart;
        if (col >= row.length()) {
            return null; // the click landed past the end of the text on that row
        }
        if (col >= 0) {
            String at = safe(() -> text.getAtIndex(AccessibleText.CHARACTER, index));
            if (at == null || at.length() != 1 || at.charAt(0) != row.charAt(col)) {
                col = -1;
            }
        }
        return TerminalLinks.at(row, col);
    }

    /**
     * The row's text. The terminal's row text is its buffer, which can carry
     * unused NUL cells past the text; they end the row. Bounded like the
     * matcher, and a failure of the screen's reader is no link, not a crash
     * (the emulator asserts in the method behind this call).
     */
    private static String rowText(AccessibleText text, int at) {
        String s = safe(() -> text.getAtIndex(AccessibleText.SENTENCE, at));
        if (s == null) {
            return null;
        }
        int nul = s.indexOf('\0');
        if (nul >= 0) {
            s = s.substring(0, nul);
        }
        return s.length() > TerminalLinks.MAX_LINE ? s.substring(0, TerminalLinks.MAX_LINE) : s;
    }

    private interface Read {
        String get();
    }

    private static String safe(Read read) {
        try {
            return read.get();
        } catch (RuntimeException | AssertionError unreadable) {
            return null;
        }
    }

    /** What following a link does, decided before the disk is asked: a file to try, or a refusal. */
    record Target(File file, String refusal) {
    }

    /** The file a link names, or the sentence that says why there is none to try. */
    static Target target(TerminalLinks.Link link, File projectDir, File home) {
        File f = TerminalLinks.candidate(link.path(), projectDir, home);
        if (f == null) {
            return new Target(null, Bundle.TerminalLinks_noProject(link.path()));
        }
        return new Target(f, null);
    }

    /** Off the EDT: the stat, then the editor (FileLink shows it on the EDT). */
    static void follow(TerminalLinks.Link link) {
        File project = aimedProject();
        File home = new File(System.getProperty("user.home"));
        RP.post(() -> {
            Target t = target(link, project, home);
            if (t.file() == null) {
                say(t.refusal());
                return;
            }
            if (!t.file().isFile()) {
                say(Bundle.TerminalLinks_notAFile(t.file().getPath()));
                return;
            }
            FileLink.open(new FileLink.Location(t.file(), link.line()), link.column());
        });
    }

    private static void say(String text) {
        SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(
                org.nmox.studio.core.util.PlainStatus.text(text)));
    }

    private static File aimedProject() {
        try {
            return org.nmox.studio.rack.service.RackService.getDefault().getRack().getProjectDir();
        } catch (RuntimeException noRack) {
            return null;
        }
    }

    private static final class Listener implements AWTEventListener {

        private int menuMask = -1;

        @Override
        public void eventDispatched(AWTEvent event) {
            if (event.getID() != MouseEvent.MOUSE_CLICKED || !(event instanceof MouseEvent e)) {
                return;
            }
            if (menuMask < 0) {
                menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            }
            if (!isLinkGesture(e.getID(), e.getButton(), e.getClickCount(), e.getModifiersEx(), menuMask)) {
                return;
            }
            Component src = e.getComponent();
            if (!(src instanceof JComponent jc) || !insideTerm(jc)) {
                return;
            }
            AccessibleContext ctx = jc.getAccessibleContext();
            AccessibleText text = ctx == null ? null : ctx.getAccessibleText();
            if (text == null) {
                return;
            }
            TerminalLinks.Link link = linkAt(text, e.getPoint());
            if (link != null) {
                follow(link);
            }
        }
    }
}
