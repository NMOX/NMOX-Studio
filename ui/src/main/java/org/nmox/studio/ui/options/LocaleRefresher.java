package org.nmox.studio.ui.options;

import java.util.Map;
import java.util.MissingResourceException;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.util.UiLocale;
import org.openide.util.NbBundle;
import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Open windows follow a language change without a restart (v2.103.0).
 *
 * <p>The lookup half is free: {@code ResourceBundle.getBundle} keys its cache
 * on the CURRENT default locale, so once {@link UiLocale#applyLive} moves the
 * default, every later {@code NbBundle.getMessage} already resolves against
 * the new language — measured before this was written, and no cache needs
 * clearing. What does not follow by itself is text a component painted
 * earlier, and a window title is painted exactly once, when the window is
 * created from the layer.
 *
 * <p>So this walks the open windows and asks each to re-read its own title.
 * The key is {@code CTL_<window id>} — the convention twelve of the fifteen
 * registered windows already follow — and the three that do not are the same
 * three {@code BundleHeadGateTest} has aliased since v2.98.0. One vocabulary,
 * not two: if that map grows, both surfaces should read the same one.
 *
 * <p><b>The honest limit.</b> The platform's own menu bar and toolbar are
 * built once at startup and keep their language until a restart. The Options
 * panel says so rather than letting the user discover it.
 */
@OnShowing
public final class LocaleRefresher implements Runnable {

    /**
     * Windows whose title key is not {@code CTL_<id>} — the same two
     * BundleHeadGateTest records, kept in step by LocaleRefresherKeysTest.
     */
    static final Map<String, String> TITLE_KEY_ALIASES = Map.of(
            "InfraDesignerTopComponent", "CTL_InfraTopComponent",
            "DockerPanelTopComponent", "CTL_DockerPanelAction");

    @Override
    public void run() {
        UiLocale.addListener(() -> SwingUtilities.invokeLater(LocaleRefresher::relabelOpenWindows));
    }

    /** Every open window re-reads its own title in the language now current. */
    static void relabelOpenWindows() {
        WindowManager wm = WindowManager.getDefault();
        for (TopComponent tc : wm.getRegistry().getOpened()) {
            String title = titleFor(wm.findTopComponentID(tc), tc);
            if (title != null) {
                tc.setDisplayName(title);
            }
        }
    }

    /**
     * The window's own title in the current language, or null when its bundle
     * cannot name it — a window we cannot name is left exactly as it was,
     * never blanked.
     */
    static String titleFor(String id, TopComponent tc) {
        if (id == null) {
            return null;
        }
        String key = TITLE_KEY_ALIASES.getOrDefault(id, "CTL_" + id);
        try {
            return NbBundle.getMessage(tc.getClass(), key);
        } catch (MissingResourceException notNamedThatWay) {
            try {
                return NbBundle.getMessage(tc.getClass(), "CTL_" + tc.getClass().getSimpleName());
            } catch (MissingResourceException neither) {
                return null;
            }
        }
    }
}
