package org.nmox.studio.tools.vscode;

import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.ServerCatalog;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Door;
import org.nmox.studio.tools.vscode.RecommendedExtensionsAction.Line;
import org.nmox.studio.tools.vscode.RecommendedExtensionsAction.Prepared;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sheet's two halves: what is read for a project off the event
 * thread, and what is shown for it — only while it is still the project
 * aimed, and never as a silent nothing.
 */
class RecommendedExtensionsActionTest {

    /** Records what would have been shown. */
    private static final class Recorder implements RecommendedExtensionsAction.Presenter {
        final List<String> refusals = new ArrayList<>();
        final List<File> sheets = new ArrayList<>();
        String heading;
        List<Line> lines = List.of();

        @Override
        public void refuse(String message) {
            refusals.add(message);
        }

        @Override
        public void sheet(File root, String heading, List<Line> lines, Map<Door, Action> doors) {
            sheets.add(root);
            this.heading = heading;
            this.lines = lines;
        }
    }

    private static Action named(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
    }

    /** A catalog that starts gopls (missing) and the ESLint server (installed), and nothing else. */
    private static final ServerCatalog CATALOG = (binary, projectDir) -> switch (binary) {
        case "gopls" -> new ServerCatalog.Server(binary, "Go", false, "go install golang.org/x/tools/gopls@latest");
        case "vscode-eslint-language-server" -> new ServerCatalog.Server(binary, "ESLint (JS / TS)", true, "npm i -g x");
        default -> null;
    };

    @TempDir
    Path project;

    private final Recorder shown = new Recorder();

    @BeforeEach
    void doors() {
        Map<Door, Action> resolved = new EnumMap<>(Door.class);
        resolved.put(Door.DOCKER_PANEL, named("&Docker Panel"));
        resolved.put(Door.LANGUAGE_SERVERS, named("Language Servers…"));
        resolved.put(Door.FORMAT_WITH_PRETTIER, named("Format with Prettier"));
        RecommendedExtensionsAction.doors = () -> resolved;
    }

    @AfterEach
    void restore() {
        RecommendedExtensionsAction.resetDoors();
    }

    private File extensions(String json) throws Exception {
        Files.createDirectories(project.resolve(".vscode"));
        Files.writeString(project.resolve(".vscode/extensions.json"), json);
        return project.toFile();
    }

    private Line line(String id) {
        return shown.lines.stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("each recommendation gets its row: what covers it here, and the door its Open would run")
    void rowsForARepository() throws Exception {
        File dir = extensions("""
                {"recommendations": ["dbaeumer.vscode-eslint", "golang.go", "ms-azuretools.vscode-docker",
                  "esbenp.prettier-vscode", "EditorConfig.EditorConfig", "somebody.unheard-of", "rust-lang.rust-analyzer",
                  "juanblanco.solidity"]}
                """);
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(dir, CATALOG), () -> dir, shown);
        assertThat(shown.refusals).isEmpty();
        assertThat(shown.sheets).containsExactly(dir);
        assertThat(shown.heading).isEqualTo("This repository recommends 8 VS Code extensions.");
        assertThat(shown.lines).extracting(Line::id).containsExactly("dbaeumer.vscode-eslint", "golang.go",
                "ms-azuretools.vscode-docker", "esbenp.prettier-vscode", "editorconfig.editorconfig",
                "somebody.unheard-of", "rust-lang.rust-analyzer", "juanblanco.solidity");
        assertThat(line("dbaeumer.vscode-eslint").here()).isEqualTo("Language server: vscode-eslint-language-server, installed");
        assertThat(line("golang.go").here())
                .isEqualTo("Language server: gopls, not installed (go install golang.org/x/tools/gopls@latest)");
        assertThat(line("golang.go").opens()).isEqualTo(Door.LANGUAGE_SERVERS);
        assertThat(line("ms-azuretools.vscode-docker").here()).as("the window's own name, mnemonic removed")
                .isEqualTo("See Docker Panel");
        assertThat(line("ms-azuretools.vscode-docker").opens()).isEqualTo(Door.DOCKER_PANEL);
        assertThat(line("esbenp.prettier-vscode").here()).startsWith("Built in: Format with Prettier");
        assertThat(line("esbenp.prettier-vscode").opens()).isNull();
        assertThat(line("editorconfig.editorconfig").here()).startsWith("Built in: .editorconfig");
        assertThat(line("somebody.unheard-of").here()).isEqualTo("Not known to NMOX Studio");
        assertThat(line("somebody.unheard-of").opens()).isNull();
        assertThat(line("rust-lang.rust-analyzer").here())
                .as("this catalog starts no rust-analyzer: the row does not say the product does")
                .isEqualTo("Not available in this installation");
        assertThat(line("juanblanco.solidity").here()).as("a window whose module did not resolve")
                .isEqualTo("Not available in this installation");
        assertThat(line("juanblanco.solidity").opens()).isNull();
    }

    @Test
    @DisplayName("aimed away before the answer could be shown: nothing is shown, and the status line says why")
    void aimedAwayShowsNothing() throws Exception {
        File dir = extensions("{\"recommendations\": [\"golang.go\"]}");
        Prepared prepared = RecommendedExtensionsAction.prepare(dir, CATALOG);
        RecommendedExtensionsAction.present(prepared, () -> new File(dir, "elsewhere"), shown);
        assertThat(shown.sheets).as("project A's list is never shown under project B").isEmpty();
        assertThat(shown.refusals).hasSize(1);
        assertThat(shown.refusals.get(0)).contains("project changed").contains(dir.getName());
        RecommendedExtensionsAction.present(prepared, () -> null, shown);
        assertThat(shown.sheets).as("no aim at all is not this project either").isEmpty();
        RecommendedExtensionsAction.present(prepared, () -> dir, shown);
        assertThat(shown.sheets).as("still aimed: shown").containsExactly(dir);
    }

    @Test
    @DisplayName("no extensions.json, or one that recommends nothing, is said on the status line")
    void nothingRecommendedSpeaks() throws Exception {
        File bare = project.toFile();
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(bare, CATALOG), () -> bare, shown);
        File empty = extensions("{\"recommendations\": []}");
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(empty, CATALOG), () -> empty, shown);
        assertThat(shown.sheets).isEmpty();
        assertThat(shown.refusals).hasSize(2).allMatch(m -> m.startsWith("No VS Code extensions are recommended in "
                + bare.getName()));
    }

    @Test
    @DisplayName("a file that does not parse is said to be unreadable, not empty")
    void unreadableSpeaks() throws Exception {
        File dir = extensions("{\"recommendations\": [\"golang.go\"");
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(dir, CATALOG), () -> dir, shown);
        assertThat(shown.sheets).isEmpty();
        assertThat(shown.refusals).hasSize(1);
        assertThat(shown.refusals.get(0)).contains("could not be read").contains(dir.getName());
    }

    @Test
    @DisplayName("past the ceiling the heading counts every recommendation and says how many are not listed")
    void theHeadingCountsWhatIsNotListed() throws Exception {
        StringBuilder json = new StringBuilder("{\"recommendations\": [");
        for (int i = 0; i < VsCodeExtensions.MAX_IDS + 3; i++) {
            json.append(i == 0 ? "" : ",").append("\"pub.ext-").append(i).append('"');
        }
        File dir = extensions(json.append("]}").toString());
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(dir, null), () -> dir, shown);
        assertThat(shown.lines).hasSize(VsCodeExtensions.MAX_IDS);
        assertThat(shown.heading).isEqualTo("This repository recommends " + (VsCodeExtensions.MAX_IDS + 3)
                + " VS Code extensions. Not listed: 3 more recommendations.");
    }

    @Test
    @DisplayName("one recommendation reads as one extension")
    void singularHeading() throws Exception {
        File dir = extensions("{\"recommendations\": [\"golang.go\"]}");
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(dir, null), () -> dir, shown);
        assertThat(shown.heading).isEqualTo("This repository recommends 1 VS Code extension.");
        assertThat(line("golang.go").here()).as("no editor to ask: named, with no state claimed")
                .isEqualTo("Language server: gopls");
    }

    @Test
    @DisplayName("the catalog is asked once per server, and only for servers a row names")
    void theCatalogIsAskedOncePerServer() throws Exception {
        List<String> asked = new ArrayList<>();
        ServerCatalog counting = (binary, projectDir) -> {
            asked.add(binary);
            return CATALOG.server(binary, projectDir);
        };
        File dir = extensions("{\"recommendations\": [\"ms-python.python\", \"ms-python.vscode-pylance\","
                + " \"golang.go\", \"eamodio.gitlens\", \"nobody.knows\"]}");
        Prepared prepared = RecommendedExtensionsAction.prepare(dir, counting);
        assertThat(asked).containsExactly("pyright-langserver", "gopls");
        assertThat(prepared.servers()).containsOnlyKeys("gopls");
        assertThat(prepared.catalog()).isTrue();
    }

    @Test
    @DisplayName("a hostile entry is a row of plain text that is not known, and opens nothing")
    void hostileEntriesAreRows() throws Exception {
        File dir = extensions("{\"recommendations\": [\"<html><img src='http://evil.example/'>\"]}");
        RecommendedExtensionsAction.present(RecommendedExtensionsAction.prepare(dir, CATALOG), () -> dir, shown);
        assertThat(shown.lines).hasSize(1);
        assertThat(shown.lines.get(0).id()).isEqualTo("<html><img src='http://evil.example/'>");
        assertThat(shown.lines.get(0).here()).isEqualTo("Not known to NMOX Studio");
        assertThat(shown.lines.get(0).opens()).isNull();
    }

    @Test
    @DisplayName("Open runs only an action that resolved and is enabled")
    void openRunsOnlyAnEnabledDoor() {
        Action enabled = named("Docker Panel");
        Action disabled = named("Language Servers");
        disabled.setEnabled(false);
        Map<Door, Action> doors = Map.of(Door.DOCKER_PANEL, enabled, Door.LANGUAGE_SERVERS, disabled);
        assertThat(RecommendedExtensionsSheet.runnable(new Line("a.b", "See Docker Panel", Door.DOCKER_PANEL), doors))
                .isSameAs(enabled);
        assertThat(RecommendedExtensionsSheet.runnable(new Line("a.b", "x", Door.LANGUAGE_SERVERS), doors)).isNull();
        assertThat(RecommendedExtensionsSheet.runnable(new Line("a.b", "x", Door.DB_STUDIO), doors)).isNull();
        assertThat(RecommendedExtensionsSheet.runnable(new Line("a.b", "x", null), doors)).isNull();
        assertThat(RecommendedExtensionsSheet.runnable(null, doors)).isNull();
    }

    @Test
    @DisplayName("the sheet's table is two read-only columns over the rows")
    void theModel() {
        RecommendedExtensionsSheet.Model model = new RecommendedExtensionsSheet.Model(
                List.of(new Line("golang.go", "Language server: gopls", Door.LANGUAGE_SERVERS)));
        assertThat(model.getRowCount()).isEqualTo(1);
        assertThat(model.getColumnCount()).isEqualTo(2);
        assertThat(model.getColumnName(0)).isEqualTo("Extension");
        assertThat(model.getColumnName(1)).isEqualTo("In NMOX Studio");
        assertThat(model.getValueAt(0, 0)).isEqualTo("golang.go");
        assertThat(model.getValueAt(0, 1)).isEqualTo("Language server: gopls");
        assertThat(model.isCellEditable(0, 0)).isFalse();
        assertThat(model.line(0).opens()).isEqualTo(Door.LANGUAGE_SERVERS);
        assertThat(model.line(-1)).isNull();
        assertThat(model.line(1)).isNull();
    }

    @Test
    @DisplayName("a door's name is its action's, without the mnemonic marker or the ellipsis")
    void doorNames() {
        assertThat(ExtensionDoors.nameOf(named("&Docker Panel"))).isEqualTo("Docker Panel");
        assertThat(ExtensionDoors.nameOf(named("Language Servers…"))).isEqualTo("Language Servers");
        assertThat(ExtensionDoors.nameOf(named("Pull Requests..."))).isEqualTo("Pull Requests");
        assertThat(ExtensionDoors.nameOf(named(""))).isNull();
        assertThat(ExtensionDoors.nameOf(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        })).isNull();
    }
}
