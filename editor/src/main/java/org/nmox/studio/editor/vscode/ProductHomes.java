package org.nmox.studio.editor.vscode;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.nmox.studio.editor.editing.ToggleWordWrapAction;
import org.nmox.studio.editor.format.FormatOnSave;
import org.nmox.studio.editor.minimap.MinimapPrefs;
import org.nmox.studio.editor.sticky.StickyPrefs;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Change;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Target;
import org.openide.util.Lookup;

/**
 * Where an imported setting is written: each through the path the
 * product's own menu or Options panel uses for it, so the running editors
 * follow without a restart.
 *
 * <ul>
 * <li><b>Indentation, wrap, right margin, whitespace, trailing
 *     whitespace on save</b>: the all-languages editor preferences
 *     ({@code MimeLookup} of the empty mime path), the node Options ▸
 *     Editor ▸ Formatting (All Languages) and the platform's own toggles
 *     write. The editor views listen to it ({@code DocumentViewOp},
 *     RELEASE310 bytecode); a language's own override still wins, as it
 *     does when Options writes. Line wrap is the one the view does not
 *     listen to: the open editors are told the way View ▸ Word Wrap tells
 *     them ({@link ToggleWordWrapAction#refreshEditors}).</li>
 * <li><b>Minimap, Sticky Scroll, Format on Save</b>: their own setters;
 *     the strips and bars listen, Format on Save is read at each save.</li>
 * <li><b>Autosave</b>: the platform module's preferences
 *     ({@code autoSaveActive}, {@code autoSaveInterval} in minutes,
 *     {@code autoSaveOnFocusLost}) and then its controller's
 *     {@code synchronize()}, exactly what its Options panel's Apply does
 *     (decompiled: {@code AutoSaveOptionsPanelController.applyChanges}).
 *     The module exports no package, so both are reached by name through
 *     the system class loader, as the Output window's font is
 *     ({@code present.OutputFont}); without the module the row is refused
 *     by name.</li>
 * </ul>
 *
 * <p>Every home is a seam the constructor takes, so a test can prove the
 * keys and their types against an in-memory node without touching the
 * developer's own preferences.
 */
final class ProductHomes implements VsCodeUserSettings.Homes {

    /** The platform's editor preference keys ({@code SimpleValueNames} spells the same strings). */
    static final String TAB_SIZE = "tab-size";
    static final String INDENT_SHIFT_WIDTH = "indent-shift-width";
    static final String SPACES_PER_TAB = "spaces-per-tab";
    static final String EXPAND_TABS = "expand-tabs";
    static final String TEXT_LINE_WRAP = "text-line-wrap";
    static final String TEXT_LIMIT_WIDTH = "text-limit-width";
    static final String TEXT_LIMIT_LINE_VISIBLE = "text-limit-line-visible";
    static final String NON_PRINTABLE_VISIBLE = "non-printable-characters-visible";
    static final String ON_SAVE_TRIM = "on-save-remove-trailing-whitespace";

    /** The autosave module's keys (its {@code AutoSaveController} constants). */
    static final String AUTOSAVE_ACTIVE = "autoSaveActive";
    static final String AUTOSAVE_INTERVAL = "autoSaveInterval";
    static final String AUTOSAVE_ON_FOCUS_LOST = "autoSaveOnFocusLost";

    static final String AUTOSAVE_CONTROLLER = "org.netbeans.modules.editor.autosave.AutoSaveController";

    private final Supplier<Preferences> allLanguages;
    private final Supplier<Preferences> autosave;
    private final Runnable autosaveSynchronize;
    private final Runnable refreshEditors;
    private final Consumer<Boolean> minimap;
    private final Consumer<Boolean> sticky;
    private final Consumer<Boolean> formatOnSave;

    ProductHomes(Supplier<Preferences> allLanguages, Supplier<Preferences> autosave, Runnable autosaveSynchronize,
            Runnable refreshEditors, Consumer<Boolean> minimap, Consumer<Boolean> sticky,
            Consumer<Boolean> formatOnSave) {
        this.allLanguages = allLanguages;
        this.autosave = autosave;
        this.autosaveSynchronize = autosaveSynchronize;
        this.refreshEditors = refreshEditors;
        this.minimap = minimap;
        this.sticky = sticky;
        this.formatOnSave = formatOnSave;
    }

    /** The product's homes. Event thread: the editors and the autosave timer are Swing's. */
    static ProductHomes forProduct() {
        return new ProductHomes(
                () -> MimeLookup.getLookup(MimePath.EMPTY).lookup(Preferences.class),
                () -> (Preferences) autosaveCall("prefs", null),
                () -> autosaveCall("synchronize", autosaveCall("getInstance", null)),
                ToggleWordWrapAction::refreshEditors,
                MinimapPrefs::setEnabled,
                StickyPrefs::setEnabled,
                FormatOnSave::setEnabled);
    }

    /**
     * A no-argument method of the autosave controller, on {@code target}
     * (null: static), reached by name. Throws {@link IllegalStateException}
     * naming the module when it is not there.
     */
    static Object autosaveCall(String method, Object target) {
        try {
            ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> controller = Class.forName(AUTOSAVE_CONTROLLER, true,
                    system != null ? system : ProductHomes.class.getClassLoader());
            Method m = controller.getMethod(method);
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            throw new IllegalStateException("the platform autosave module is not available", ex);
        }
    }

    @Override
    public void write(Change change) {
        Object v = change.value();
        switch (change.target()) {
            case TAB_SIZE -> node().putInt(TAB_SIZE, (Integer) v);
            case INDENT -> {
                Preferences p = node();
                p.putInt(INDENT_SHIFT_WIDTH, (Integer) v);
                p.putInt(SPACES_PER_TAB, (Integer) v);
            }
            case EXPAND_TABS -> node().putBoolean(EXPAND_TABS, (Boolean) v);
            case LINE_WRAP -> node().put(TEXT_LINE_WRAP, (String) v);
            case RULER_WIDTH -> node().putInt(TEXT_LIMIT_WIDTH, (Integer) v);
            case RULER_VISIBLE -> node().putBoolean(TEXT_LIMIT_LINE_VISIBLE, (Boolean) v);
            case WHITESPACE_VISIBLE -> node().putBoolean(NON_PRINTABLE_VISIBLE, (Boolean) v);
            case TRIM_ON_SAVE -> node().put(ON_SAVE_TRIM, (String) v);
            case MINIMAP -> minimap.accept((Boolean) v);
            case STICKY_SCROLL -> sticky.accept((Boolean) v);
            case FORMAT_ON_SAVE -> formatOnSave.accept((Boolean) v);
            case AUTOSAVE_ACTIVE -> autosaveNode().putBoolean(AUTOSAVE_ACTIVE, (Boolean) v);
            case AUTOSAVE_MINUTES -> autosaveNode().putInt(AUTOSAVE_INTERVAL, (Integer) v);
            case AUTOSAVE_ON_FOCUS_LOST -> autosaveNode().putBoolean(AUTOSAVE_ON_FOCUS_LOST, (Boolean) v);
            default -> throw new IllegalStateException("no home for " + change.target());
        }
    }

    @Override
    public void settled(Set<Target> written) {
        if (written.contains(Target.LINE_WRAP)) {
            refreshEditors.run();
        }
        if (written.contains(Target.AUTOSAVE_ACTIVE) || written.contains(Target.AUTOSAVE_MINUTES)
                || written.contains(Target.AUTOSAVE_ON_FOCUS_LOST)) {
            autosaveSynchronize.run();
        }
    }

    private Preferences node() {
        Preferences p = allLanguages.get();
        if (p == null) {
            throw new IllegalStateException("the editor preferences are not available");
        }
        return p;
    }

    private Preferences autosaveNode() {
        Preferences p = autosave.get();
        if (p == null) {
            throw new IllegalStateException("the platform autosave module is not available");
        }
        return p;
    }
}
