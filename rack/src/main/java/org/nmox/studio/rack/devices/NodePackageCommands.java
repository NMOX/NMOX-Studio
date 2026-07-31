package org.nmox.studio.rack.devices;

import java.util.List;

/**
 * The argv for adding and removing one Node dependency, per package
 * manager.
 *
 * <p>Every Node lane in the product resolves the project's own manager
 * from its own contract — the corepack {@code packageManager} pin first,
 * then the lockfile (v1.60.0's "speaks your package manager" law, see
 * {@link ProjectInspector#nodePackageManager}). Project Studio's
 * dependency editor was the one surface that never got the message: it
 * shelled out to a hardcoded {@code npm install}, so on a pnpm or yarn
 * project the Add button wrote a {@code package-lock.json} beside the
 * real lockfile and desynchronized the tree (fixed v1.212.0).
 *
 * <p>The reason this is a class and not a string swap: the managers do
 * not merely have different NAMES, they have different VERBS. npm
 * installs and uninstalls; yarn and pnpm add and remove. Substituting
 * only the binary would produce {@code yarn install lodash}, which
 * ignores the argument and installs the whole tree — a silent wrong
 * answer, worse than the bug it replaced. Pure and unit-tested for
 * exactly that reason.
 */
public final class NodePackageCommands {

    private NodePackageCommands() {
    }

    /**
     * Argv that adds one package.
     *
     * @param manager one of npm/yarn/pnpm, as
     *                {@link ProjectInspector#nodePackageManager} returns;
     *                anything unrecognized is treated as npm, which is
     *                the ecosystem default and always present
     * @param pkg     the package name (optionally versioned)
     * @param dev     add to devDependencies
     */
    public static List<String> add(String manager, String pkg, boolean dev) {
        switch (manager == null ? "npm" : manager) {
            case "yarn":
                return dev ? List.of("yarn", "add", "--dev", pkg)
                        : List.of("yarn", "add", pkg);
            case "pnpm":
                return dev ? List.of("pnpm", "add", "--save-dev", pkg)
                        : List.of("pnpm", "add", pkg);
            default:
                return dev ? List.of("npm", "install", "--save-dev", pkg)
                        : List.of("npm", "install", pkg);
        }
    }

    /** Argv that removes one package. */
    public static List<String> remove(String manager, String pkg) {
        switch (manager == null ? "npm" : manager) {
            case "yarn":
                return List.of("yarn", "remove", pkg);
            case "pnpm":
                return List.of("pnpm", "remove", pkg);
            default:
                return List.of("npm", "uninstall", pkg);
        }
    }

    /** The command as a user would read it, for labels and prompts. */
    public static String describe(List<String> argv) {
        return String.join(" ", argv);
    }
}
