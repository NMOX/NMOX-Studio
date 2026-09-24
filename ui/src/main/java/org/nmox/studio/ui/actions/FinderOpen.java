package org.nmox.studio.ui.actions;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.desktop.OpenFilesHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.api.sendopts.CommandException;
import org.netbeans.api.sendopts.CommandLine;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * A folder handed to NMOX Studio by macOS — dropped on the Dock icon,
 * picked in Finder's Open With, or passed to {@code open -a "NMOX Studio"}
 * — is AIMED, exactly as File ▸ Open Folder… and {@code nmox .} aim it. A
 * file handed over the same way opens in the editor through the platform's
 * own {@code --open} door, the one {@code nmox file.js} walks.
 *
 * <h2>What the platform did with a folder (measured 3.1.0)</h2>
 *
 * The applemenu module's {@code NbApplicationAdapter} (RELEASE310,
 * decompiled) installs the one {@link OpenFilesHandler} the JDK allows, from
 * its {@code ModuleInstall.restored()}, and its {@code openFiles} opens each
 * file through {@code OpenCookie}/{@code EditCookie}/{@code ViewCookie} and
 * skips every entry for which {@code File.isDirectory()} is true — a folder
 * was dropped without a word. And in the shipped 3.0.x bundle that handler
 * never ran at all, for files either: AWT only answers open-documents
 * events when the process's MAIN BUNDLE declares {@code CFBundleDocumentTypes},
 * and the JVM's main bundle was the runtime's own embedded Info.plist
 * ({@code lsappinfo} names the running IDE {@code com.azul.zulu.java}), not
 * {@code NMOX Studio.app}. A probe bundle of the exact launcher shape
 * received nothing; with {@code CFProcessPath} naming the bundle's
 * executable it received both kinds of event. So the bundle launcher now
 * exports {@code CFProcessPath}, and {@link LauncherEnvironment} takes it
 * back out of the environment before anything the IDE starts can inherit
 * it (a child that inherits it believes it IS NMOX Studio — measured).
 *
 * <h2>Who receives the event</h2>
 *
 * The JDK queues open-documents events that arrive before any handler
 * exists and replays them to the FIRST handler set (measured on JDK 25:
 * {@code _AppEventHandler$_QueuingAppEventDispatcher.setHandler}). The
 * event that LAUNCHES the IDE arrives during the splash, so whoever sets
 * a handler first gets the folder that started it. The platform sets its
 * handler in {@code restored()}; {@link FinderOpenInstall#validate()} runs
 * in {@code NbInstaller.prepare}, which the module manager calls for every
 * module before it calls any {@code restored()} (both read from the
 * bytecode) — so this handler is first, deterministically. The platform's
 * {@code restored()} then replaces it, and {@link FinderOpenShowing}
 * installs it again once the window shows, so every later event is ours
 * too. Between those two moments (the few seconds of module start) a
 * dropped folder would reach the platform's handler and be skipped.
 *
 * <p>Events received before the window shows are held and handed over
 * when it does: aiming needs the window system, and during module start
 * {@code WindowManager.getDefault()} can still be the platform's dummy.
 */
@Messages({
    "# {0} - the folder that was aimed",
    "FinderOpen_oneFolder=Aimed at {0}. NMOX Studio works in one folder at a time, so the other folders were not opened."
})
public final class FinderOpen {

    private static final Logger LOG = Logger.getLogger(FinderOpen.class.getName());

    /** The variable the macOS bundle launcher exports so AWT reads the bundle's Info.plist. */
    static final String CF_PROCESS_PATH = "CFProcessPath";

    private static final RequestProcessor OPEN_RP = new RequestProcessor("nmox-finder-open", 1);

    /** What one open-documents event asks for, sorted into the verb each item takes. */
    record Route(File folder, int otherFolders, List<File> files) {

        Route {
            files = List.copyOf(files);
        }

        boolean isEmpty() {
            return folder == null && files.isEmpty();
        }
    }

    /**
     * The first existing folder is aimed; the IDE is aimed at one folder at
     * a time, so any further folders are counted, not opened. Existing
     * files are opened, in the order given. A path that no longer exists is
     * dropped (logged), as the platform's own handler drops it. Pure.
     */
    static Route route(List<File> items) {
        File folder = null;
        int others = 0;
        List<File> files = new ArrayList<>();
        for (File f : items) {
            if (f == null) {
                continue;
            }
            File abs = f.toPath().toAbsolutePath().normalize().toFile();
            if (abs.isDirectory()) {
                if (folder == null) {
                    folder = abs;
                } else {
                    others++;
                }
            } else if (abs.isFile()) {
                files.add(abs);
            } else {
                LOG.log(Level.INFO, "Open request for {0}, which does not exist; ignored", abs);
            }
        }
        return new Route(folder, others, files);
    }

    // ------------------------------------------------------------ seams

    /** Where an aimed folder goes: the {@code --aim} door, so the two can never disagree. */
    static Consumer<File> aimer = dir -> AimOption.aimer.accept(dir);

    /** Where files go: the platform's own {@code --open}, off the EDT. */
    static Consumer<List<File>> opener = files -> OPEN_RP.post(() -> openWithPlatform(files));

    /** Where the one-folder note goes. */
    static Consumer<String> status = text -> StatusDisplayer.getDefault().setStatusText(
            org.nmox.studio.core.util.PlainStatus.text(text));

    /** Sets the handler on the JDK's Desktop; the test seam for {@link #install()}. */
    static java.util.function.BooleanSupplier installer = FinderOpen::install;

    /** Takes a variable out of the environment children inherit. */
    static java.util.function.Predicate<String> forgetter = LauncherEnvironment::forget;

    // ------------------------------------------------------------ state

    private static final Object LOCK = new Object();
    private static final List<File> PENDING = new ArrayList<>();
    private static boolean ready;

    /** The one handler instance; the JDK keeps exactly one per application. */
    private static final OpenFilesHandler HANDLER = e -> accept(e.getFiles());

    private FinderOpen() {
    }

    /**
     * An open-documents event's files: held until the window shows, handed
     * over at once after. Events held before the window shows are merged,
     * so a launch that brings two events still aims one folder.
     */
    static void accept(List<File> items) {
        LOG.log(Level.FINE, "macOS handed over {0}", items);
        synchronized (LOCK) {
            if (!ready) {
                PENDING.addAll(items);
                return;
            }
        }
        dispatch(route(items));
    }

    /** The window is up: hand over what was held, and take the handler back from the platform. */
    static void ready() {
        List<File> held;
        synchronized (LOCK) {
            ready = true;
            held = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        installer.getAsBoolean();
        if (!held.isEmpty()) {
            dispatch(route(held));
        }
    }

    static void dispatch(Route r) {
        if (r.folder() != null) {
            aimer.accept(r.folder());
            if (r.otherFolders() > 0) {
                status.accept(Bundle.FinderOpen_oneFolder(r.folder().getName()));
            }
        }
        if (!r.files().isEmpty()) {
            opener.accept(r.files());
        }
    }

    /**
     * Sets this handler as the application's open-files handler, when there
     * is a screen and the platform supports the action. Returns whether it
     * did. Only ever called on macOS: elsewhere the desktop hands a folder
     * to the launcher on its command line ({@code nmox}, the .desktop entry,
     * Explorer's menu), never through this API.
     */
    static boolean install() {
        if (!Utilities.isMac() || GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            LOG.info("This runtime cannot receive files from Finder (APP_OPEN_FILE unsupported)");
            return false;
        }
        desktop.setOpenFileHandler(HANDLER);
        return true;
    }

    /**
     * The earliest moment in the module system ({@link FinderOpenInstall}):
     * become the first handler, so the event that launched the IDE is ours,
     * then take {@code CFProcessPath} out of the environment children
     * inherit. The handler goes first because setting it initializes AWT's
     * application delegate, which is what reads the main bundle; once it
     * has, the variable has done its work.
     */
    static void early() {
        if (Utilities.isMac()) {
            earlyFor(CF_PROCESS_PATH);
        }
    }

    /** {@link #early()} for a named variable: install first, then forget it. */
    static void earlyFor(String variable) {
        boolean installed = installer.getAsBoolean();
        LOG.log(Level.FINE, "open-files handler installed before the platform''s: {0}", installed);
        if (System.getenv(variable) == null) {
            return;
        }
        if (forgetter.test(variable)) {
            LOG.log(Level.FINE, "{0} removed from the environment processes started by the IDE inherit", variable);
        } else {
            LOG.log(Level.WARNING, "{0} could not be removed from the environment processes started by"
                    + " the IDE inherit; a Mac app started from the IDE may take NMOX Studio''s identity", variable);
        }
    }

    private static void openWithPlatform(List<File> files) {
        List<String> args = new ArrayList<>();
        args.add("--open");
        for (File f : files) {
            args.add(f.getAbsolutePath());
        }
        try {
            CommandLine.getDefault().process(args.toArray(String[]::new));
        } catch (CommandException ex) {
            // the openfile module has already told the user, in a dialog, which
            // file it could not open (Handler.process -> notifyLater); this is the log
            LOG.log(Level.INFO, "The platform could not open " + files, ex);
        }
    }

    /** Tests only: back to the state a fresh JVM starts in. */
    static void resetForTest() {
        synchronized (LOCK) {
            ready = false;
            PENDING.clear();
        }
    }
}
