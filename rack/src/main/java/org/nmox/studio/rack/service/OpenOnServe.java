package org.nmox.studio.rack.service;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

/**
 * Press Run, see the page.
 *
 * <p>Before v1.212.0 the IDE knew everything it needed and still made you
 * do the last step by hand: {@code Run} started the dev server, the
 * {@link ServingRegistry} learned the URL, the status line grew a ⇄ chip
 * — and the page itself opened nowhere. The IDE ships a browser
 * (⌥⌘4) and knew the address, and you still had to go get it. Every
 * editor a web developer arrives from closes that loop for them.
 *
 * <p>This is the loop-closer. A Run <em>arms</em> it for one project;
 * the first WEB serving that appears for that project afterwards is
 * opened once in the in-app Browser, and the arm is spent.
 *
 * <h2>Why armed, rather than "open whatever appears"</h2>
 * Servings appear for reasons the user did not just ask for — session
 * resurrection after a crash (v1.1.0), a preset booting three devices, a
 * teammate's patch loading. Opening a browser tab for those would be the
 * IDE grabbing the wheel. The arm ties the tab to a gesture: you pressed
 * Run, so you get the page.
 *
 * <p>Bounded on both ends: only servings that appear AFTER the press
 * count (a project already serving is already on screen, so pressing Run
 * again opens nothing), and the arm expires — a Run that never serves
 * must not pop a browser minutes later when something unrelated does.
 *
 * <h2>Threading</h2>
 * {@link ServingRegistry} notifies on its own background thread, never
 * the EDT; the open marshals itself (the browser facade is EDT-safe and
 * documents that). Listener attach/detach are symmetric — armed adds,
 * spent or expired removes.
 */
public final class OpenOnServe {

    /** Preference key: open the served page after Run. On by default. */
    public static final String PREF_OPEN_ON_RUN = "run.openServedPage";

    /**
     * How long an arm waits for a dev server. Vite is up in under a
     * second; a cold webpack build can take tens. Past this the gesture
     * has gone stale and we would be opening a page nobody asked for.
     */
    static final long ARM_WINDOW_MS = 90_000;

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-open-on-serve", 1);

    private static final OpenOnServe INSTANCE = new OpenOnServe();

    /**
     * Private monitor. Synchronizing on {@code this} would publish the
     * lock — {@link #getDefault()} hands the singleton to anyone, so any
     * caller could hold it and stall the opener (find-sec-bugs
     * USO_UNSAFE_ACCESSIBLE_OBJECT_SYNCHRONIZATION). A lock nobody else
     * can reach cannot be contended by accident.
     */
    private final Object lock = new Object();

    /** The one live arm, or null. Guarded by {@link #lock}. */
    private Arm arm;

    private OpenOnServe() {
    }

    public static OpenOnServe getDefault() {
        return INSTANCE;
    }

    /** One pending "show me the page when it comes up". */
    private static final class Arm implements ServingRegistry.Listener {

        private final File projectDir;
        private final Set<String> urlsBefore;
        private final long deadline;
        private boolean spent;

        Arm(File projectDir, Set<String> urlsBefore, long deadline) {
            this.projectDir = projectDir;
            this.urlsBefore = urlsBefore;
            this.deadline = deadline;
        }

        @Override
        public void servingChanged() {
            OpenOnServe.getDefault().onServingChanged(this);
        }
    }

    /**
     * Arms for one project: the next NEW web serving for it opens.
     * Called from the Run command. A second arm replaces the first —
     * the newest gesture is the one the user is watching.
     *
     * @param projectDir the project being run; null or preference-off is
     *                   a no-op, so callers need no branching
     */
    public void arm(File projectDir) {
        if (projectDir == null || !enabled()) {
            return;
        }
        ServingRegistry registry = ServingRegistry.getDefault();
        Set<String> before = new HashSet<>();
        for (ServingRegistry.Serving s : registry.snapshot()) {
            if (isWebFor(s, projectDir)) {
                before.add(s.url());
            }
        }
        Arm fresh = new Arm(projectDir, before,
                System.currentTimeMillis() + ARM_WINDOW_MS);
        synchronized (lock) {
            disarmLocked();
            arm = fresh;
        }
        registry.addListener(fresh);
        // the window is also a real deadline: without this an arm that
        // never fires would keep a listener attached for the session
        RP.schedule(() -> expire(fresh), (int) ARM_WINDOW_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Registry thread. Opens the first new web serving, once. */
    private void onServingChanged(Arm which) {
        String url = null;
        synchronized (lock) {
            if (arm != which || which.spent) {
                return;
            }
            if (System.currentTimeMillis() > which.deadline) {
                disarmLocked();
                return;
            }
            for (ServingRegistry.Serving s : ServingRegistry.getDefault().snapshot()) {
                if (isWebFor(s, which.projectDir) && !which.urlsBefore.contains(s.url())) {
                    url = s.url();
                    break;
                }
            }
            if (url == null) {
                return;
            }
            which.spent = true;
            disarmLocked();
        }
        open(url);
    }

    private void expire(Arm which) {
        synchronized (lock) {
            if (arm == which) {
                disarmLocked();
            }
        }
    }

    /** Caller holds {@link #lock}. Detaches and forgets the current arm. */
    private void disarmLocked() {
        if (arm != null) {
            ServingRegistry.getDefault().removeListener(arm);
            arm = null;
        }
    }

    private static boolean isWebFor(ServingRegistry.Serving s, File projectDir) {
        return s.kind() == ServingRegistry.Kind.WEB
                && s.projectDir() != null
                && s.projectDir().equals(projectDir);
    }

    /**
     * Hands the URL to the in-app Browser through the core facade — the
     * soft-dependency idiom (v1.46.0), so rack never depends on ui. A
     * null lookup (stripped platform, plain unit test) means the feature
     * is quietly off rather than an error.
     */
    private static void open(String url) {
        org.nmox.studio.core.spi.EmbeddedBrowser browser =
                org.nmox.studio.core.spi.EmbeddedBrowser.find();
        if (browser != null) {
            browser.open(url);
        }
    }

    /** Package-private for the tests: is the behaviour switched on? */
    static boolean enabled() {
        return prefs().getBoolean(PREF_OPEN_ON_RUN, true);
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(OpenOnServe.class);
    }

    /** Test seam: is an arm currently standing? */
    boolean isArmedForTest() {
        synchronized (lock) {
            return arm != null;
        }
    }

    /** Test seam: forget any arm so cases don't leak into each other. */
    static void resetForTest() {
        synchronized (INSTANCE.lock) {
            INSTANCE.disarmLocked();
        }
    }
}
