package org.nmox.studio.tools.vscode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.ServerCatalog;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Door;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Equivalent;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Facts;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Kind;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Row;
import org.nmox.studio.tools.vscode.VsCodeExtensions.Entry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The table of what covers a VS Code extension here is a table of
 * claims. These tests hold its shape (lower-case ids, each once, every
 * row a sentence), its honesty (an id it does not hold is "not known",
 * never a neighbour's answer), and as much of each claim as a build can
 * check: the language servers are ones the editor launches, the doors
 * are actions some module registers, and every file a row cites is
 * still there.
 */
class ExtensionEquivalentsTest {

    /** The repository root, from this module's directory (where surefire runs). */
    private static final Path REPO = Path.of("..");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project", "ui", "rack",
            "apiclient", "dbstudio", "web3", "infra");

    /** Every door named, and the editor's catalog answering for every server: all installed. */
    private static Facts everything(boolean installed) {
        Map<Door, String> names = new EnumMap<>(Door.class);
        for (Door d : Door.values()) {
            names.put(d, "<" + d.name() + ">");
        }
        Map<String, ServerCatalog.Server> servers = new HashMap<>();
        for (Row row : ExtensionEquivalents.ROWS) {
            if (row.equivalent().kind() == Kind.SERVER) {
                String binary = row.equivalent().server();
                servers.put(binary, new ServerCatalog.Server(binary, "Lang", installed, "install " + binary));
            }
        }
        return new Facts(names, true, servers);
    }

    /** A product where nothing resolved: no door, no catalog. */
    private static final Facts NOTHING = new Facts(Map.of(), false, Map.of());

    private static Equivalent of(String id) {
        return ExtensionEquivalents.of(new Entry(id, true));
    }

    @Test
    @DisplayName("ids are lower case, shaped like extension ids, and each appears once")
    void idsAreCanonicalAndUnique() {
        Set<String> seen = new HashSet<>();
        List<String> twice = new ArrayList<>();
        for (Row row : ExtensionEquivalents.ROWS) {
            assertThat(row.id()).as("lower case, since a recommendation is folded before it is looked up")
                    .isEqualTo(row.id().toLowerCase(Locale.ROOT));
            assertThat(VsCodeExtensions.parse("{\"recommendations\": [\"" + row.id() + "\"]}").entries())
                    .as("%s must be an id the reader accepts, or the row can never be reached", row.id())
                    .containsExactly(new Entry(row.id(), true));
            if (!seen.add(row.id())) {
                twice.add(row.id());
            }
        }
        assertThat(twice).as("an id mapped twice: the second answer would be silently dropped").isEmpty();
        assertThat(ExtensionEquivalents.ROWS.size()).as("the table covers the extensions people actually recommend")
                .isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("every mapped id renders a sentence in English, with everything resolved and with nothing resolved")
    void everyRowRendersASentence() {
        for (Row row : ExtensionEquivalents.ROWS) {
            for (Facts facts : List.of(everything(true), everything(false), NOTHING)) {
                String sentence = ExtensionEquivalents.sentence(of(row.id()), facts);
                assertThat(sentence).as(row.id()).isNotBlank().doesNotContain("{0}").doesNotContain("{1}")
                        .doesNotContain("choice");
            }
            assertThat(of(row.id()).kind()).as(row.id()).isNotEqualTo(Kind.UNKNOWN);
        }
    }

    @Test
    @DisplayName("an id the table does not hold is not known: never built in, never a neighbour's answer")
    void unknownIdsAreNeverGuessed() {
        for (String id : List.of("someone.some-extension", "dbaeumer.vscode-eslint2", "dbaeumer.vscode",
                "github.copilot-labs", "esbenp.prettier", "x.y", "ms-python", "editorconfig.editorconfig.extra")) {
            Equivalent e = of(id);
            assertThat(e.kind()).as(id).isEqualTo(Kind.UNKNOWN);
            assertThat(e).isSameAs(ExtensionEquivalents.UNKNOWN);
            for (Facts facts : List.of(everything(true), NOTHING)) {
                assertThat(ExtensionEquivalents.sentence(e, facts)).as(id).isEqualTo("Not known to NMOX Studio");
                assertThat(ExtensionEquivalents.opens(e, facts)).as("an unknown id opens nothing").isNull();
            }
        }
    }

    @Test
    @DisplayName("a malformed entry is never looked up, even when its text reads like a mapped id")
    void malformedEntriesAreNotLookedUp() {
        assertThat(ExtensionEquivalents.of(new Entry("dbaeumer.vscode-eslint", false)))
                .isSameAs(ExtensionEquivalents.UNKNOWN);
        assertThat(ExtensionEquivalents.of(null)).isSameAs(ExtensionEquivalents.UNKNOWN);
        Entry hostile = VsCodeExtensions.parse("{\"recommendations\": [\"<html>editorconfig.editorconfig\"]}")
                .entries().get(0);
        assertThat(ExtensionEquivalents.of(hostile).kind()).isEqualTo(Kind.UNKNOWN);
    }

    @Test
    @DisplayName("a recommendation written in another case reaches its row")
    void caseFoldedRecommendationsReachTheirRow() {
        Entry entry = VsCodeExtensions.parse("{\"recommendations\": [\"EditorConfig.EditorConfig\"]}").entries().get(0);
        assertThat(ExtensionEquivalents.of(entry).kind()).isEqualTo(Kind.BUILT_IN);
    }

    @Test
    @DisplayName("a language-server row says what the editor's catalog said, and no more")
    void serverSentences() {
        Equivalent go = of("golang.go");
        assertThat(go.kind()).isEqualTo(Kind.SERVER);
        Map<Door, String> doors = Map.of(Door.LANGUAGE_SERVERS, "Language Servers");
        assertThat(ExtensionEquivalents.sentence(go, new Facts(doors, true,
                Map.of("gopls", new ServerCatalog.Server("gopls", "Go", true, "go install gopls")))))
                .isEqualTo("Language server: gopls, installed");
        assertThat(ExtensionEquivalents.sentence(go, new Facts(doors, true,
                Map.of("gopls", new ServerCatalog.Server("gopls", "Go", false, "go install gopls")))))
                .isEqualTo("Language server: gopls, not installed (go install gopls)");
        assertThat(ExtensionEquivalents.sentence(go, new Facts(doors, true,
                Map.of("gopls", new ServerCatalog.Server("gopls", null, false, null)))))
                .isEqualTo("Language server: gopls, not installed");
        assertThat(ExtensionEquivalents.sentence(go, new Facts(doors, false, Map.of())))
                .as("no editor to ask: the server is named, its state is not claimed")
                .isEqualTo("Language server: gopls");
        Facts denied = new Facts(doors, true, Map.of());
        assertThat(ExtensionEquivalents.sentence(go, denied))
                .as("the editor says it starts no such server: the row does not say it does")
                .isEqualTo("Not available in this installation");
        assertThat(ExtensionEquivalents.opens(go, denied)).isNull();
        assertThat(ExtensionEquivalents.opens(go, new Facts(doors, false, Map.of()))).isEqualTo(Door.LANGUAGE_SERVERS);
    }

    @Test
    @DisplayName("a window row is named by the window's own action, opens it, and says so when it is not there")
    void windowSentences() {
        Equivalent docker = of("ms-azuretools.vscode-docker");
        Facts present = new Facts(Map.of(Door.DOCKER_PANEL, "Docker Panel"), false, Map.of());
        assertThat(ExtensionEquivalents.sentence(docker, present)).isEqualTo("See Docker Panel");
        assertThat(ExtensionEquivalents.opens(docker, present)).isEqualTo(Door.DOCKER_PANEL);
        assertThat(ExtensionEquivalents.sentence(docker, NOTHING)).isEqualTo("Not available in this installation");
        assertThat(ExtensionEquivalents.opens(docker, NOTHING)).isNull();
    }

    @Test
    @DisplayName("a device row names the devices by their own titles and the rack by its window's name")
    void deviceSentences() {
        Facts rack = new Facts(Map.of(Door.TASK_RACK, "Task Rack"), false, Map.of());
        assertThat(ExtensionEquivalents.sentence(of("ms-playwright.playwright"), rack))
                .isEqualTo("Rack device SPECTER, in Task Rack");
        assertThat(ExtensionEquivalents.sentence(of("biomejs.biome"), rack))
                .isEqualTo("Rack devices PURITY, GLOSS, in Task Rack");
        assertThat(ExtensionEquivalents.opens(of("biomejs.biome"), rack)).isEqualTo(Door.TASK_RACK);
    }

    @Test
    @DisplayName("a built-in row that names an action shows the action's name, and an editor action is named but not run")
    void builtInSentences() {
        Facts facts = new Facts(Map.of(Door.FORMAT_WITH_PRETTIER, "Format with Prettier", Door.PULL_REQUESTS,
                "Pull Requests", Door.API_STUDIO, "API Studio"), false, Map.of());
        assertThat(ExtensionEquivalents.sentence(of("esbenp.prettier-vscode"), facts))
                .startsWith("Built in: Format with Prettier on the editor\u2019s right-click menu");
        assertThat(ExtensionEquivalents.opens(of("esbenp.prettier-vscode"), facts))
                .as("Format with Prettier acts on the focused editor; the sheet has none").isNull();
        assertThat(ExtensionEquivalents.sentence(of("github.vscode-pull-request-github"), facts))
                .isEqualTo("Built in: Pull Requests");
        assertThat(ExtensionEquivalents.opens(of("github.vscode-pull-request-github"), facts)).isEqualTo(Door.PULL_REQUESTS);
        assertThat(ExtensionEquivalents.sentence(of("humao.rest-client"), facts)).endsWith("open in API Studio");
        assertThat(ExtensionEquivalents.sentence(of("editorconfig.editorconfig"), NOTHING))
                .as("a note that names no door needs nothing resolved").startsWith("Built in: .editorconfig");
    }

    @Test
    @DisplayName("the rows that must not overclaim say exactly what the product's position is")
    void theHonestRows() {
        assertThat(of("bradlc.vscode-tailwindcss").kind()).isEqualTo(Kind.NOT_AVAILABLE);
        assertThat(ExtensionEquivalents.sentence(of("bradlc.vscode-tailwindcss"), NOTHING)).startsWith("Not available");
        assertThat(ExtensionEquivalents.sentence(of("github.copilot"), NOTHING))
                .contains("KVASIR").contains("on demand").contains("no always-on completion stream");
        assertThat(ExtensionEquivalents.sentence(of("vscodevim.vim"), NOTHING)).contains("no Vim mode");
        assertThat(of("firefox-devtools.vscode-firefox-debug").kind()).isEqualTo(Kind.NO_EQUIVALENT);
        assertThat(ExtensionEquivalents.sentence(of("ms-vscode-remote.remote-containers"), NOTHING))
                .isEqualTo("No equivalent in NMOX Studio");
        assertThat(ExtensionEquivalents.sentence(of("hashicorp.terraform"), everything(true)))
                .as("a grammar is syntax colouring and no more").isEqualTo("Syntax colouring");
        assertThat(ExtensionEquivalents.sentence(of("eamodio.gitlens"), NOTHING)).as("a subset, said plainly")
                .startsWith("Built in, in part");
    }

    @Test
    @DisplayName("each row's shape fits its kind")
    void shapesFitKinds() {
        for (Row row : ExtensionEquivalents.ROWS) {
            Equivalent e = row.equivalent();
            switch (e.kind()) {
                case SERVER -> {
                    assertThat(e.server()).as(row.id()).isNotBlank();
                    assertThat(e.door()).isEqualTo(Door.LANGUAGE_SERVERS);
                }
                case WINDOW -> assertThat(e.door()).as(row.id()).isNotNull().matches(d -> d.opens);
                case DEVICE -> {
                    assertThat(e.devices()).as(row.id()).isNotEmpty();
                    assertThat(e.devices()).allSatisfy(d -> assertThat(d.getTitle()).isNotBlank());
                    assertThat(e.door()).isEqualTo(Door.TASK_RACK);
                }
                case UNKNOWN -> assertThat(row.id()).as("the table holds no unknowns").isNull();
                default -> assertThat(e.server()).as(row.id()).isNull();
            }
        }
    }

    @Test
    @DisplayName("every language server a row names is one the editor launches and its trust ledger holds")
    void serverBinariesAreLaunchedByTheEditor() throws IOException {
        String launchers = read(REPO.resolve("editor/src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"));
        String ledger = read(REPO.resolve("editor/src/main/java/org/nmox/studio/editor/lsp/ServerTrust.java"));
        List<String> unknown = new ArrayList<>();
        for (Row row : ExtensionEquivalents.ROWS) {
            String binary = row.equivalent().server();
            if (binary != null && !(launchers.contains('"' + binary + '"') && ledger.contains('"' + binary + '"'))) {
                unknown.add(row.id() + " -> " + binary);
            }
        }
        assertThat(unknown).as("a row naming a language server the editor does not start is a false claim").isEmpty();
    }

    /** The census's own shapes ({@code ActionIdsResolveTest}): a constant, and a lookup by two of them. */
    private static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern CALL = Pattern.compile("case\\s+(\\w+)\\s*->\\s*Actions\\.forID\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*\\)");

    @Test
    @DisplayName("every door is looked up by an id that some module registers as an action")
    void doorsNameRegisteredActions() throws IOException {
        String doors = read(Path.of("src/main/java/org/nmox/studio/tools/vscode/ExtensionDoors.java"));
        Map<String, String> constants = new HashMap<>();
        Matcher c = CONSTANT.matcher(doors);
        while (c.find()) {
            constants.put(c.group(1), c.group(2));
        }
        String registrations = allMainSources();
        Set<Door> looked = EnumSet.noneOf(Door.class);
        List<String> missing = new ArrayList<>();
        Matcher m = CALL.matcher(doors);
        while (m.find()) {
            looked.add(Door.valueOf(m.group(1)));
            String category = constants.get(m.group(2));
            String id = constants.get(m.group(3));
            assertThat(category).as("the category of %s is a constant of the file", m.group(1)).isNotNull();
            assertThat(id).as("the id of %s is a constant of the file", m.group(1)).isNotNull();
            Pattern registered = Pattern.compile("ActionID\\(\\s*category\\s*=\\s*\"" + Pattern.quote(category)
                    + "\"\\s*,\\s*id\\s*=\\s*\"" + Pattern.quote(id) + "\"\\s*\\)");
            if (!registered.matcher(registrations).find()) {
                missing.add(m.group(1) + ": " + category + " / " + id);
            }
        }
        assertThat(looked).as("every door has its lookup").containsExactlyInAnyOrder(Door.values());
        assertThat(missing).as("a door whose action nobody registers would read \"not available\" forever").isEmpty();
    }

    @Test
    @DisplayName("every door is used by a row, so none is kept for nothing")
    void everyDoorIsUsed() {
        Set<Door> used = EnumSet.noneOf(Door.class);
        ExtensionEquivalents.ROWS.forEach(row -> {
            if (row.equivalent().door() != null) {
                used.add(row.equivalent().door());
            }
        });
        assertThat(used).containsExactlyInAnyOrder(Door.values());
    }

    /** A source or document path cited in a comment beside a row: {@code editor/spell/Foo.java}. */
    private static final Pattern CITED = Pattern.compile("(?<![\\w/.])((?:[a-z0-9]+/)+[A-Za-z][\\w.-]*\\.(?:java|md|json))");

    @Test
    @DisplayName("every file a row cites as its evidence still exists")
    void citedFilesExist() throws IOException {
        String source = read(Path.of("src/main/java/org/nmox/studio/tools/vscode/ExtensionEquivalents.java"));
        String table = source.substring(source.indexOf("private static List<Row> table()"),
                source.indexOf("static Equivalent of(VsCodeExtensions.Entry entry)"));
        List<String> gone = new ArrayList<>();
        int cited = 0;
        for (String line : table.split("\n")) {
            if (!line.strip().startsWith("//")) {
                continue;
            }
            Matcher m = CITED.matcher(line);
            while (m.find()) {
                cited++;
                if (!exists(m.group(1))) {
                    gone.add(m.group(1));
                }
            }
        }
        assertThat(cited).as("the rows cite their evidence").isGreaterThan(25);
        assertThat(gone).as("a cited file that is gone: check the claim again, then fix the row or delete it").isEmpty();
    }

    /** {@code docs/…} from the root; {@code module/rest} as a source or a resource of that module. */
    private static boolean exists(String cited) {
        if (cited.startsWith("docs/")) {
            return Files.isRegularFile(REPO.resolve(cited));
        }
        int slash = cited.indexOf('/');
        String module = cited.substring(0, slash);
        String rest = cited.substring(slash + 1);
        for (String kind : List.of("src/main/java", "src/main/resources")) {
            if (Files.isRegularFile(REPO.resolve(module).resolve(kind).resolve("org/nmox/studio").resolve(module).resolve(rest))) {
                return true;
            }
        }
        return false;
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Every module's main sources in one string, for a registration search. */
    private static String allMainSources() throws IOException {
        StringBuilder out = new StringBuilder();
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = read(p);
                    if (text.contains("ActionID(")) {
                        out.append(text).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }
}
