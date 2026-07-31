package org.nmox.studio.rack.devices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.212.0 lockfile-corruption fix: Project Studio's dependency
 * editor shelled out to a hardcoded {@code npm install}, so on a pnpm or
 * yarn project it wrote a {@code package-lock.json} beside the real
 * lockfile.
 *
 * <p>These pin the part that makes the fix non-trivial — the managers
 * have different VERBS, not just different names. The dangerous wrong
 * answer is {@code yarn install lodash}: yarn ignores the argument and
 * installs the whole tree, so the user sees success and gets nothing.
 */
class NodePackageCommandsTest {

    @Test
    @DisplayName("npm installs and uninstalls")
    void npmVerbs() {
        assertThat(NodePackageCommands.add("npm", "lodash", false))
                .containsExactly("npm", "install", "lodash");
        assertThat(NodePackageCommands.add("npm", "vitest", true))
                .containsExactly("npm", "install", "--save-dev", "vitest");
        assertThat(NodePackageCommands.remove("npm", "lodash"))
                .containsExactly("npm", "uninstall", "lodash");
    }

    @Test
    @DisplayName("yarn ADDS and REMOVES — never 'yarn install <pkg>'")
    void yarnVerbs() {
        assertThat(NodePackageCommands.add("yarn", "lodash", false))
                .containsExactly("yarn", "add", "lodash");
        assertThat(NodePackageCommands.add("yarn", "vitest", true))
                .containsExactly("yarn", "add", "--dev", "vitest");
        assertThat(NodePackageCommands.remove("yarn", "lodash"))
                .containsExactly("yarn", "remove", "lodash");
        // the silent-wrong-answer guard: yarn install ignores the package
        // name and resolves the whole tree instead
        assertThat(NodePackageCommands.add("yarn", "lodash", false))
                .as("yarn install <pkg> would look like success and do nothing")
                .doesNotContain("install");
    }

    @Test
    @DisplayName("pnpm adds and removes")
    void pnpmVerbs() {
        assertThat(NodePackageCommands.add("pnpm", "lodash", false))
                .containsExactly("pnpm", "add", "lodash");
        assertThat(NodePackageCommands.add("pnpm", "vitest", true))
                .containsExactly("pnpm", "add", "--save-dev", "vitest");
        assertThat(NodePackageCommands.remove("pnpm", "lodash"))
                .containsExactly("pnpm", "remove", "lodash");
        assertThat(NodePackageCommands.add("pnpm", "lodash", false))
                .doesNotContain("install");
    }

    @Test
    @DisplayName("an unknown or absent manager falls back to npm, never to nothing")
    void unknownFallsBackToNpm() {
        assertThat(NodePackageCommands.add(null, "lodash", false))
                .containsExactly("npm", "install", "lodash");
        assertThat(NodePackageCommands.add("bun-but-not-yet-supported", "lodash", false))
                .startsWith("npm");
        assertThat(NodePackageCommands.remove(null, "lodash"))
                .containsExactly("npm", "uninstall", "lodash");
    }

    @Test
    @DisplayName("describe() reads as the command the user will see run")
    void describeIsHonest() {
        assertThat(NodePackageCommands.describe(
                NodePackageCommands.remove("pnpm", "left-pad")))
                .isEqualTo("pnpm remove left-pad");
    }

    @Test
    @DisplayName("every manager's add carries the package name (the corruption guard)")
    void everyManagerActuallyNamesThePackage() {
        for (String mgr : new String[] {"npm", "yarn", "pnpm"}) {
            assertThat(NodePackageCommands.add(mgr, "react", false))
                    .as(mgr + " add must pass the package through")
                    .contains("react");
            assertThat(NodePackageCommands.remove(mgr, "react"))
                    .as(mgr + " remove must pass the package through")
                    .contains("react");
        }
    }
}
