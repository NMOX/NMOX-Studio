package org.nmox.studio.ui.keymap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Switching the keymap profile the way the Options dialog does.
 *
 * <p>Read from the platform (RELEASE310 bytecode), not assumed. Pressing
 * Apply in Options ▸ Keymap runs {@code KeymapModel.setCurrentProfile}, which
 * hands the profile's id to every {@code KeymapManager} in the default
 * Lookup: the global one ({@code LayersBridge}, which sets the
 * {@code currentKeymap} attribute of the {@code Keymaps} folder, the thing
 * {@code NbKeymap} listens to) and the editor's (which tells
 * {@code EditorSettings}, so every open editor rebuilds its keymap). Nothing
 * else is written. So this asks the Lookup for the same managers and calls
 * the same method; it writes no file and no attribute of its own.
 *
 * <p>{@code KeymapManager} lives in a package the keymap module exports to
 * its friends only, so it is reached by name through the system class
 * loader, the arrangement {@code CloneRepository} uses for the git module.
 * {@code VsCodeKeymapResolutionTest} holds the class and the method to the
 * assembled cluster, so a platform that renames either fails the build
 * instead of the switch.
 */
final class KeymapProfiles {

    /** The profile's id: its folder under {@code Keymaps} and under {@code Editors/Keybindings}. */
    static final String VSCODE = "VSCode";
    static final String DEFAULT = "NetBeans";

    static final String MANAGER = "org.netbeans.core.options.keymap.spi.KeymapManager";
    static final String SET_CURRENT = "setCurrentProfile";

    enum Outcome {
        /** The profile is now the current one, in the global keymap and in the editors. */
        SWITCHED,
        /** It already was. */
        ALREADY,
        /** No layer registers the profile: there is nothing to switch to. */
        NO_PROFILE,
        /** The platform's keymap managers could not be reached, or did not take the profile. */
        FAILED
    }

    /** The platform, as far as a switch needs it; a seam so the decisions are tested without one. */
    interface Platform {

        /** Whether both halves of the profile are registered: the global folder and the editor's. */
        boolean registered(String profile);

        /** The id of the profile in force. */
        String current();

        /** One setter per registered keymap manager; each takes a profile id. */
        List<Setter> managers() throws ReflectiveOperationException;
    }

    interface Setter {
        void use(String profile) throws ReflectiveOperationException;
    }

    private KeymapProfiles() {
    }

    /**
     * Makes {@code profile} the keymap profile, or says why it did not.
     * Nothing is changed unless the profile is registered, and a switch is
     * reported only when the platform itself then names the profile as
     * current.
     */
    static Outcome use(String profile, Platform platform) {
        if (!platform.registered(profile)) {
            return Outcome.NO_PROFILE;
        }
        if (profile.equals(platform.current())) {
            return Outcome.ALREADY;
        }
        try {
            List<Setter> managers = platform.managers();
            if (managers.isEmpty()) {
                return Outcome.FAILED;
            }
            for (Setter manager : managers) {
                manager.use(profile);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unreachable) {
            return Outcome.FAILED;
        }
        return profile.equals(platform.current()) ? Outcome.SWITCHED : Outcome.FAILED;
    }

    /** The running platform. */
    static final Platform PLATFORM = new Platform() {

        @Override
        public boolean registered(String profile) {
            return FileUtil.getConfigFile("Keymaps/" + profile) != null
                    && FileUtil.getConfigFile("Editors/Keybindings/" + profile) != null;
        }

        @Override
        public String current() {
            FileObject keymaps = FileUtil.getConfigFile("Keymaps");
            Object current = keymaps == null ? null : keymaps.getAttribute("currentKeymap");
            return current instanceof String s && !s.isBlank() ? s : DEFAULT;
        }

        @Override
        public List<Setter> managers() throws ReflectiveOperationException {
            ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
            if (system == null) {
                return List.of();
            }
            Class<?> type = Class.forName(MANAGER, true, system);
            Method set = type.getMethod(SET_CURRENT, String.class);
            List<Setter> out = new ArrayList<>();
            for (Object manager : Lookup.getDefault().lookupAll(type)) {
                out.add(profile -> set.invoke(manager, profile));
            }
            return out;
        }
    };
}
