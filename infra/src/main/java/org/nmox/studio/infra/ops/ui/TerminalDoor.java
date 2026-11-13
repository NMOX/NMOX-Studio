package org.nmox.studio.infra.ops.ui;

import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import org.nmox.studio.core.process.ToolLocator;
import org.openide.awt.Actions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.IOContainer;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.TopComponent;

/**
 * Opens a command in the IDE's own Terminal: a tab in the platform's
 * terminal container running the user's {@code mosh} or {@code ssh}
 * under a real pty, so the session is interactive, resizes with the
 * tab, and survives the Hub closing. The platform exposes that door only
 * to its friends, so this class walks the same path its own
 * Terminal action takes (read from the shipped bytecode of
 * {@code TerminalSupportImpl}) through the system class loader — the way
 * the git chip reaches the History browser.
 *
 * <p>When any step of that path is missing (a platform change, a stripped
 * cluster), the door degrades honestly: the exact command lands on the
 * clipboard, the platform's plain terminal opens, and the caller is told
 * so it can say so. Nothing is typed into a shell on the user's behalf.
 */
@Messages({
    "# {0} - the host's label",
    "# {1} - mosh or ssh",
    "TerminalDoor_tabTitle={0} ({1})",
    "# {0} - the service's label",
    "TerminalDoor_logsTitle={0} logs",
    "# {0} - the service's label",
    "TerminalDoor_shellTitle={0} shell",
    "TerminalDoor_copiedFallback=The Terminal could not run it here; the command is on the clipboard — paste it into the terminal that opened"
})
public final class TerminalDoor {

    private static final Logger LOG = Logger.getLogger(TerminalDoor.class.getName());

    /** The platform's plain local terminal action, as ProjectTerminal names it (ActionIdsResolveTest checks the id). */
    static final String LOCAL_TERMINAL_ID = "LocalTerminalAction";

    /** How an open went. */
    public enum Outcome { OPENED, COPIED_FALLBACK, NO_TOOL }

    private TerminalDoor() {
    }

    private static final org.openide.util.RequestProcessor RP = new org.openide.util.RequestProcessor("Ops Hub terminal", 1, true);

    /**
     * A tab's title, made unable to be markup. A title is partly a
     * stranger's text: a service's label is whatever the HOST reports (a
     * compose label, a provider's server name), and in English it comes
     * first ("{0} logs"). The Terminal's tabs are a {@code JTabbedPane},
     * which renders a title that begins with {@code <html>} — so a label
     * beginning {@code <html><img src=…>} would have the IDE fetch that
     * address when the tab paints (the markup-render class, v2.86.0). No
     * tag can be written without an angle bracket, so the brackets become
     * the single guillemets that look like them; control characters go.
     */
    static String tabTitle(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        raw.codePoints().forEach(c -> {
            if (c == '<') {
                out.append('\u2039');
            } else if (c == '>') {
                out.append('\u203A');
            } else if (!Character.isISOControl(c)) {
                out.appendCodePoint(c);
            }
        });
        return out.toString();
    }

    /**
     * Runs {@code argv} in a new Terminal tab. The first element is the
     * tool's name, resolved on this machine's PATH (the launcher's
     * augmented one); the rest are its arguments. The tab is made on the
     * EDT; the process is built and started on a worker, because the
     * platform's process builder refuses the AWT thread (walked 3.11.0:
     * {@code IllegalThreadStateException: Should never be called from AWT
     * thread}) — so the outcome arrives through the callback, on the EDT.
     */
    public static void open(String rawTitle, List<String> argv, String workingDir, java.util.function.Consumer<Outcome> then) {
        String title = tabTitle(rawTitle);
        String exe = ToolLocator.resolve(argv.get(0));
        if (exe == null) {
            then.accept(Outcome.NO_TOOL);
            return;
        }
        String[] args = argv.subList(1, argv.size()).toArray(String[]::new);
        InputOutput io;
        try {
            io = openTab(title);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notReachable) {
            LOG.log(Level.INFO, "The platform Terminal could not be driven; falling back to the clipboard", notReachable);
            fallback(argv);
            then.accept(Outcome.COPIED_FALLBACK);
            return;
        }
        RP.post(() -> {
            Outcome outcome;
            try {
                runInTab(io, title, exe, args, workingDir);
                outcome = Outcome.OPENED;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError notReachable) {
                LOG.log(Level.INFO, "The platform Terminal could not run the command; falling back to the clipboard", notReachable);
                io.closeInputOutput();
                fallback(argv);
                outcome = Outcome.COPIED_FALLBACK;
            }
            Outcome done = outcome;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (done == Outcome.OPENED) {
                    bringForward(io);
                }
                then.accept(done);
            });
        });
    }

    /**
     * Shows the tab that was asked for. The first time the Terminal
     * window opens it makes a local shell tab of its own and selects it
     * when that shell starts — after ours was selected — so the session
     * the user pressed a button for sat behind a prompt they did not ask
     * for (walked: the first Logs of a session). Selected again once our
     * process runs, and once more a moment later for the platform's tab
     * that starts after it.
     */
    private static void bringForward(InputOutput io) {
        io.select();
        javax.swing.Timer again = new javax.swing.Timer(1200, e -> {
            if (!io.isClosed()) {
                io.select();
            }
        });
        again.setRepeats(false);
        again.start();
    }

    /** The tab, on the EDT: the terminal container, one InputOutput in it, xterm emulation. */
    private static InputOutput openTab(String title) throws ReflectiveOperationException {
        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
        Class<?> containerClass = Class.forName(
                "org.netbeans.modules.dlight.terminal.ui.TerminalContainerTopComponent", true, cl);
        Object container = containerClass.getMethod("findInstance").invoke(null);
        TopComponent tc = (TopComponent) container;
        tc.open();
        tc.requestActive();
        IOContainer ioContainer = (IOContainer) containerClass.getMethod("getIOContainer").invoke(container);
        IOProvider provider = IOProvider.get("Terminal");
        InputOutput io = provider.getIO(title, new Action[0], ioContainer);
        io.select();
        Class<?> ioTerm = Class.forName("org.netbeans.modules.terminal.api.ui.IOTerm", true, cl);
        Object term = ioTerm.getMethod("term", InputOutput.class).invoke(null, io);
        if (term != null) {
            term.getClass().getMethod("setEmulation", String.class).invoke(term, "xterm");
        }
        return io;
    }

    /** The process, off the EDT: the platform's own builder and service, as its Terminal action uses them. */
    private static void runInTab(InputOutput io, String title, String exe, String[] args, String workingDir)
            throws ReflectiveOperationException {
        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
        Class<?> envFactory = Class.forName("org.netbeans.modules.nativeexecution.api.ExecutionEnvironmentFactory", true, cl);
        Class<?> envClass = Class.forName("org.netbeans.modules.nativeexecution.api.ExecutionEnvironment", true, cl);
        Object env = envFactory.getMethod("getLocal").invoke(null);
        Class<?> builderClass = Class.forName("org.netbeans.modules.nativeexecution.api.NativeProcessBuilder", true, cl);
        Object builder = builderClass.getMethod("newProcessBuilder", envClass).invoke(null, env);
        builderClass.getMethod("setExecutable", String.class).invoke(builder, exe);
        builderClass.getMethod("setArguments", String[].class).invoke(builder, (Object) args);
        if (workingDir != null) {
            builderClass.getMethod("setWorkingDirectory", String.class).invoke(builder, workingDir);
        }
        // mosh insists on a UTF-8 locale, and an app launched from the Finder may carry none
        Object macros = builderClass.getMethod("getEnvironment").invoke(builder);
        Method get = macros.getClass().getMethod("get", String.class);
        Method put = macros.getClass().getMethod("put", String.class, String.class);
        Object lang = get.invoke(macros, "LANG");
        if (lang == null || lang.toString().isBlank()) {
            put.invoke(macros, "LANG", "en_US.UTF-8");
        }
        put.invoke(macros, "TERM", "xterm-256color");

        Class<?> descriptorClass = Class.forName(
                "org.netbeans.modules.nativeexecution.api.execution.NativeExecutionDescriptor", true, cl);
        Object descriptor = descriptorClass.getConstructor().newInstance();
        descriptor = descriptorClass.getMethod("controllable", boolean.class).invoke(descriptor, true);
        descriptor = descriptorClass.getMethod("frontWindow", boolean.class).invoke(descriptor, true);
        descriptor = descriptorClass.getMethod("inputVisible", boolean.class).invoke(descriptor, true);
        descriptor = descriptorClass.getMethod("inputOutput", InputOutput.class).invoke(descriptor, io);
        Class<?> serviceClass = Class.forName(
                "org.netbeans.modules.nativeexecution.api.execution.NativeExecutionService", true, cl);
        Object service = serviceClass.getMethod("newService", builderClass, descriptorClass, String.class)
                .invoke(null, builder, descriptor, title);
        serviceClass.getMethod("run").invoke(service);
    }

    private static void fallback(List<String> argv) {
        StringBuilder sb = new StringBuilder();
        for (String a : argv) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(a.chars().anyMatch(Character::isWhitespace) ? "'" + a.replace("'", "'\\''") + "'" : a);
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        } catch (IllegalStateException | java.awt.HeadlessException noClipboard) {
            LOG.log(Level.FINE, "no clipboard", noClipboard);
        }
        Action plain = Actions.forID("Window", LOCAL_TERMINAL_ID);
        if (plain != null) {
            plain.actionPerformed(new java.awt.event.ActionEvent(TerminalDoor.class, 0, "open"));
        }
    }
}
