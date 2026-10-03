package org.nmox.studio.tools.vscode;

import java.util.EnumMap;
import java.util.Map;
import javax.swing.Action;

import org.nmox.studio.tools.vscode.ExtensionEquivalents.Door;
import org.openide.awt.Actions;

/**
 * The actions behind {@link Door}: where the VS Code extensions sheet
 * gets a window's own name, and what its Open button runs.
 *
 * <p>Each door is looked up by an id written here as a constant, one
 * lookup per door with both arguments named, because that is the shape
 * {@code ActionIdsResolveTest} reads: every pair below is held to the
 * assembled cluster, so a window renamed in another module fails the
 * build instead of leaving a row that says "not installed here". Most of
 * these actions live in modules this one does not depend on (the
 * studios, the editor, the ui), which is the point of going through the
 * Actions folder: an absent module is a lookup that answers null, and
 * the row then says so and its button stays disabled.
 *
 * <p>The system filesystem is the in-memory layer cache, so resolving is
 * not disk work; it is done on the event thread, where the actions will
 * be shown and run.
 */
final class ExtensionDoors {

    private ExtensionDoors() {
    }

    static final String WINDOW = "Window";
    static final String TOOLS = "Tools";
    static final String TEAM = "Team";
    static final String FILE = "File";
    static final String SOURCE = "Source";

    static final String DOCKER_PANEL = "org.nmox.studio.rack.docker.DockerPanelTopComponent";
    static final String DB_STUDIO = "org.nmox.studio.dbstudio.ui.DbStudioTopComponent";
    static final String API_STUDIO = "org.nmox.studio.apiclient.ui.ApiClientTopComponent";
    static final String CONTRACT_STUDIO = "org.nmox.studio.web3.ui.Web3StudioTopComponent";
    static final String TASK_RACK = "org.nmox.studio.rack.RackTopComponent";
    static final String TESTS = "org.nmox.studio.editor.testing.explorer.TestsExplorerTopComponent";
    static final String AGENT_PORT = "org.nmox.studio.rack.mcp.AgentPortAction";
    static final String LANGUAGE_SERVERS = "org.nmox.studio.editor.lsp.LanguageServerStatusAction";
    static final String PULL_REQUESTS = "org.nmox.studio.rack.service.PullRequestsAction";
    static final String NG_SCHEMATIC = "org.nmox.studio.ui.actions.NgSchematicAction";
    static final String FORMAT_WITH_PRETTIER = "org.nmox.studio.editor.format.FormatWithPrettierAction";
    static final String COMPILE_TO_CSS = "org.nmox.studio.editor.sass.SassCompileAction";

    /** The action behind {@code door}, or null when no loaded module registers it. */
    static Action action(Door door) {
        return switch (door) {
            case DOCKER_PANEL -> Actions.forID(WINDOW, DOCKER_PANEL);
            case DB_STUDIO -> Actions.forID(WINDOW, DB_STUDIO);
            case API_STUDIO -> Actions.forID(WINDOW, API_STUDIO);
            case CONTRACT_STUDIO -> Actions.forID(WINDOW, CONTRACT_STUDIO);
            case TASK_RACK -> Actions.forID(WINDOW, TASK_RACK);
            case TESTS -> Actions.forID(WINDOW, TESTS);
            case AGENT_PORT -> Actions.forID(TOOLS, AGENT_PORT);
            case LANGUAGE_SERVERS -> Actions.forID(TOOLS, LANGUAGE_SERVERS);
            case PULL_REQUESTS -> Actions.forID(TEAM, PULL_REQUESTS);
            case NG_SCHEMATIC -> Actions.forID(FILE, NG_SCHEMATIC);
            case FORMAT_WITH_PRETTIER -> Actions.forID(SOURCE, FORMAT_WITH_PRETTIER);
            case COMPILE_TO_CSS -> Actions.forID(SOURCE, COMPILE_TO_CSS);
        };
    }

    /**
     * Every door that resolves, with its action. A door whose lookup
     * throws (a broken registration in another module) is left out like
     * one that is absent: one bad action must not take the sheet with it.
     */
    static Map<Door, Action> resolve() {
        Map<Door, Action> out = new EnumMap<>(Door.class);
        for (Door door : Door.values()) {
            try {
                Action a = action(door);
                if (a != null) {
                    out.put(door, a);
                }
            } catch (RuntimeException | LinkageError broken) {
                java.util.logging.Logger.getLogger(ExtensionDoors.class.getName())
                        .log(java.util.logging.Level.FINE, "door did not resolve: " + door, broken);
            }
        }
        return out;
    }

    /** The action's display name without its mnemonic marker or trailing ellipsis; null when it has none. */
    static String nameOf(Action a) {
        Object n = a.getValue(Action.NAME);
        String s = n == null ? "" : Actions.cutAmpersand(n.toString()).strip();
        if (s.endsWith("...")) {
            s = s.substring(0, s.length() - 3);
        } else if (s.endsWith("…")) {
            s = s.substring(0, s.length() - 1);
        }
        s = s.strip();
        return s.isEmpty() ? null : s;
    }
}
