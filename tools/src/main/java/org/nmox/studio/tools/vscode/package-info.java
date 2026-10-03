/**
 * A project's {@code .vscode/tasks.json} in Quick Search (⌘I / ⇧⌘P). Read
 * {@code VsCodeTasks} first: it is the pure half — JSONC, the per-OS
 * override, the variables this IDE can fill and the ones it refuses, the
 * working folder judged by {@code core.util.Containment} — and it spawns
 * nothing. {@code VsCodeTaskSearchProvider} lists what it read and, on
 * Enter, refuses out loud or asks Workspace Trust BEFORE spawning through
 * {@code CommandExecutor}, registering the run with {@code LiveRuns} so
 * the toolbar ■ stops it. An {@code npm}-type task goes to
 * {@code tools.npm.NpmService}'s own trust-gated lane instead.
 *
 * <p>One Enter may be several tasks. {@code VsCodeTaskPlan} (pure) turns
 * {@code dependsOn} / {@code dependsOrder} into the stages to run and
 * decides the whole run before anything starts; {@code VsCodeTaskEditor}
 * reads the editor on the event thread for {@code ${file}} and its
 * family; {@code VsCodeTaskPrompts} puts the file's {@code ${input:…}}
 * questions. Both are thin Swing over values the pure half defines.
 *
 * <p>{@code .vscode/launch.json} is the sibling pair: {@code VsCodeLaunch}
 * (pure, sharing the tasks half's JSONC, per-OS merge, variables and
 * containment) and {@code VsCodeLaunchSearchProvider}, which spawns
 * nothing itself — it hands the resolved program or page to the editor's
 * debugger through {@code core.spi.DebugLauncher}, after its own trust
 * question on the project.
 *
 * <p>{@code .vscode/extensions.json} is the third file, and the odd one:
 * nothing in it can run here, because VS Code extensions do not install
 * in this product. {@code VsCodeExtensions} (pure) reads the ids a
 * repository recommends; {@code ExtensionEquivalents} (pure) is the table
 * of what covers each one here — built in, a window, a rack device, a
 * language server and whether it is installed, or plainly nothing — with
 * every row a claim checked against this repository and an unlisted id
 * told "not known", never a guess. {@code RecommendedExtensionsAction}
 * reads off the event thread and shows {@code RecommendedExtensionsSheet}
 * only for the project still aimed; {@code ExtensionDoors} holds the
 * action ids the rows name and open. {@code VsCodeFilesNotice} is the
 * once-per-project balloon that says all three files were found.
 *
 * <p>The category is registered in this module's hand {@code layer.xml}
 * under {@code QuickSearch/VsCodeTasks} and {@code QuickSearch/VsCodeLaunches}; the bundle lives here, in a
 * package with no {@code @NbBundle.Messages}, so the two can never clobber
 * each other (the v1.79.0 lesson).
 */
package org.nmox.studio.tools.vscode;
