package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;

/**
 * The Welcome's "Clone Git Repository…" (3.1.0): the platform's own clone
 * wizard, opened from where a first hour starts.
 *
 * <p>The wizard's action is context-bound: it is registered with an
 * injectable {@code org.netbeans.modules.git.ui.actions.ContextHolder} and
 * EXACTLY_ONE selection, and the Team ▸ Git menu builds that holder itself.
 * Looked up and pressed from anywhere else it is disabled and does nothing
 * - walked: the first cut of this link opened no window and logged no line.
 * So the holder is made here the way the menu makes it, with a null
 * context (the holder falls back to the current one), by reflection
 * because the git module does not export the class; the same arrangement
 * the git chip's History has used since 1.40.0. If any of that is missing
 * the status line says so instead of failing in silence. Only the lookup
 * is caught: a failure inside the wizard itself is the platform's to
 * report, not "Git support is not available".
 */
@Messages("CloneRepository_fallback=Git support is not available in this installation, so the clone wizard cannot open.")
public final class CloneRepository {

    static final String CATEGORY = "Git";
    static final String ID = "org.netbeans.modules.git.ui.clone.CloneAction";
    static final String HOLDER = "org.netbeans.modules.git.ui.actions.ContextHolder";
    static final String VCS_CONTEXT = "org.netbeans.modules.versioning.spi.VCSContext";

    private CloneRepository() {
    }

    /** Opens the clone wizard; says where the menu door is when it cannot. */
    public static void open(ActionEvent e) {
        if (!tryOpen(e)) {
            StatusDisplayer.getDefault().setStatusText(Bundle.CloneRepository_fallback());
        }
    }

    static boolean tryOpen(ActionEvent e) {
        Action action = Actions.forID(CATEGORY, ID);
        if (!(action instanceof ContextAwareAction aware)) {
            return false;
        }
        Action bound;
        try {
            ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
            if (system == null) {
                return false;
            }
            Class<?> holderType = Class.forName(HOLDER, true, system);
            Class<?> contextType = Class.forName(VCS_CONTEXT, true, system);
            Object holder = holderType.getConstructor(contextType).newInstance((Object) null);
            bound = aware.createContextAwareInstance(Lookups.singleton(holder));
            if (!bound.isEnabled()) {
                return false;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return false;
        }
        bound.actionPerformed(e);
        return true;
    }
}
