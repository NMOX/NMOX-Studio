package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.nmox.studio.core.spi.DebugLauncher;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Config;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VS Code launch configurations in Quick Search (v3.1.0): "launch" in ⌘I
 * lists {@code Debug: Launch Program — ${workspaceFolder}/server.js}; Enter
 * asks Workspace Trust on the project and hands exactly the resolved file
 * and working folder (or page and web root) to the debugger facade — or
 * refuses out loud and hands over nothing.
 */
class VsCodeLaunchSearchProviderTest {

    private static final String LAUNCH = """
            {
              "version": "0.2.0",
              "configurations": [
                { "type": "node", "request": "launch", "name": "Launch Program",
                  "skipFiles": ["<node_internals>/**"], "program": "${workspaceFolder}/server.js" },
                { "type": "node", "request": "launch", "name": "Launch with args",
                  "program": "server.js", "args": ["--port", "3000"], "env": { "ROOT": "${workspaceFolder}" } },
                { "type": "node", "request": "launch", "name": "Launch with a runtime",
                  "program": "server.js", "runtimeExecutable": "nodemon" },
                { "type": "chrome", "request": "launch", "name": "Launch Chrome",
                  "url": "http://localhost:8080", "webRoot": "${workspaceFolder}/web" },
                { "type": "node", "request": "attach", "name": "Attach", "port": 9229 },
              ],
              "compounds": [ { "name": "Full stack", "configurations": ["Launch Program", "Launch Chrome"] } ],
            }
            """;

    @TempDir
    Path project;

    private final Predicate<File> realTrust = VsCodeLaunchSearchProvider.trustCheck;
    private final Supplier<DebugLauncher> realLauncher = VsCodeLaunchSearchProvider.launcher;
    private final Consumer<String> realStatus = VsCodeLaunchSearchProvider.statusSink;

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final List<String> asked = Collections.synchronizedList(new ArrayList<>());
    private final List<Object[]> handed = Collections.synchronizedList(new ArrayList<>());

    /** A debugger that records what it was handed and starts nothing. */
    private final class FakeLauncher implements DebugLauncher {
        @Override
        public boolean supports(File file) {
            return true;
        }

        @Override
        public void debug(File file) {
            handed.add(new Object[] {"file-only", file});
        }

        @Override
        public boolean debug(File file, File workingDir) {
            handed.add(new Object[] {"file", file, workingDir});
            return true;
        }

        @Override
        public boolean debug(File file, File workingDir, List<String> args, java.util.Map<String, String> env) {
            if (args.isEmpty() && env.isEmpty()) {
                return debug(file, workingDir);
            }
            handed.add(new Object[] {"file+", file, workingDir, args, env});
            return true;
        }

        @Override
        public boolean debugPage(String url, File webRoot) {
            handed.add(new Object[] {"page", url, webRoot});
            return true;
        }
    }

    @BeforeEach
    void seams() throws Exception {
        VsCodeLaunch.clearCache();
        Files.createDirectories(project.resolve(".vscode"));
        Files.createDirectories(project.resolve("web"));
        Files.writeString(project.resolve("server.js"), "require('http');\n");
        Files.writeString(project.resolve(".vscode/launch.json"), LAUNCH);
        VsCodeLaunchSearchProvider.statusSink = said::add;
        VsCodeLaunchSearchProvider.trustCheck = dir -> {
            asked.add(dir.getPath());
            return true;
        };
        FakeLauncher fake = new FakeLauncher();
        VsCodeLaunchSearchProvider.launcher = () -> fake;
    }

    @AfterEach
    void restore() {
        VsCodeLaunchSearchProvider.trustCheck = realTrust;
        VsCodeLaunchSearchProvider.launcher = realLauncher;
        VsCodeLaunchSearchProvider.statusSink = realStatus;
    }

    private Config config(String name) {
        return VsCodeLaunch.read(project.toFile()).stream()
                .filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private void enter(String name) {
        VsCodeLaunchSearchProvider.run(project.toFile(), config(name)).waitFinished();
    }

    private static List<String> names(List<VsCodeLaunchSearchProvider.Item> items) {
        return items.stream().map(i -> i.config().name()).toList();
    }

    @Test
    @DisplayName("\"launch\" lists the configurations, labelled with the program or page as the file wrote it")
    void lists() {
        List<VsCodeLaunchSearchProvider.Item> items = VsCodeLaunchSearchProvider.itemsFor("launch program", project.toFile());
        assertThat(names(items)).startsWith("Launch Program");
        assertThat(items.get(0).label()).isEqualTo("Debug: Launch Program — ${workspaceFolder}/server.js");
        assertThat(names(VsCodeLaunchSearchProvider.itemsFor("debug", project.toFile())))
                .as("the vocabulary lists every configuration and compound, name order")
                .containsExactly("Attach", "Full stack", "Launch Chrome", "Launch Program", "Launch with a runtime",
                        "Launch with args");
        assertThat(names(VsCodeLaunchSearchProvider.itemsFor("chrome", project.toFile()))).containsExactly("Launch Chrome");
        assertThat(VsCodeLaunchSearchProvider.itemsFor("postgres", project.toFile())).isEmpty();
        assertThat(VsCodeLaunchSearchProvider.itemsFor("debug", null)).isEmpty();
        assertThat(VsCodeLaunchSearchProvider.itemsFor("debug", project.resolve("web").toFile()))
                .as("no launch.json: the category stays silent").isEmpty();
    }

    @Test
    @DisplayName("labels are escaped for the HTML renderer and clipped by code points")
    void labels() {
        assertThat(VsCodeLaunchSearchProvider.label("<b>x</b>", "a<i>&b.js"))
                .isEqualTo("Debug: &lt;b&gt;x&lt;/b&gt; — a&lt;i&gt;&amp;b.js");
        assertThat(VsCodeLaunchSearchProvider.label("Full stack", "")).isEqualTo("Debug: Full stack");
        assertThat(VsCodeLaunchSearchProvider.label("l", "x".repeat(200))).endsWith("…")
                .hasSize("Debug: l — ".length() + VsCodeLaunchSearchProvider.MAX_TARGET);
    }

    @Test
    @DisplayName("Enter asks trust on the project, then hands the debugger exactly the resolved program and working folder")
    void enterHandsTheProgram() throws Exception {
        enter("Launch Program");

        assertThat(asked).containsExactly(project.toFile().getPath());
        assertThat(handed).hasSize(1);
        assertThat(handed.get(0)[0]).isEqualTo("file");
        assertThat(((File) handed.get(0)[1]).getCanonicalFile())
                .isEqualTo(project.resolve("server.js").toFile().getCanonicalFile());
        assertThat(((File) handed.get(0)[2]).getCanonicalFile()).isEqualTo(project.toFile().getCanonicalFile());
        assertThat(said).containsExactly("Starting the debugger for \"Launch Program\"…");
    }

    @Test
    @DisplayName("Enter on a Chrome configuration hands the debugger the page and the web root")
    void enterHandsThePage() throws Exception {
        enter("Launch Chrome");
        assertThat(handed).hasSize(1);
        assertThat(handed.get(0)[0]).isEqualTo("page");
        assertThat(handed.get(0)[1]).isEqualTo("http://localhost:8080");
        assertThat(((File) handed.get(0)[2]).getCanonicalFile())
                .isEqualTo(project.resolve("web").toFile().getCanonicalFile());
    }

    @Test
    @DisplayName("Keep Safe hands the debugger nothing and says nothing more")
    void keepSafeHandsNothing() {
        VsCodeLaunchSearchProvider.trustCheck = dir -> {
            asked.add(dir.getPath());
            return false;
        };
        enter("Launch Program");
        assertThat(asked).hasSize(1);
        assertThat(handed).isEmpty();
        assertThat(said).isEmpty();
    }

    @Test
    @DisplayName("args and env reach the debugger, variables substituted (3.1.0)")
    void argsAndEnvReachTheDebugger() throws Exception {
        enter("Launch with args");
        assertThat(handed).hasSize(1);
        Object[] h = handed.get(0);
        assertThat(h[0]).isEqualTo("file+");
        assertThat(h[3]).isEqualTo(List.of("--port", "3000"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> env = (java.util.Map<String, String>) h[4];
        assertThat(new File(env.get("ROOT")).getCanonicalFile()).isEqualTo(project.toFile().getCanonicalFile());
        assertThat(asked).as("trust asked before the hand-off").hasSize(1);
    }

    @Test
    @DisplayName("refusals speak on the status line, hand over nothing and ask nothing")
    void refusalsSpeak() {
        enter("Launch with a runtime");
        enter("Attach");
        enter("Full stack");
        assertThat(handed).isEmpty();
        assertThat(asked).as("a refusal asks no trust question").isEmpty();
        assertThat(said).containsExactly(
                "Configuration \"Launch with a runtime\" sets runtimeExecutable, which the NMOX Studio debugger cannot pass on; "
                        + "it would debug something other than what the file says.",
                "Configuration \"Attach\" uses request attach; the NMOX Studio debugger only launches.",
                "Compound \"Full stack\" starts several sessions at once; the NMOX Studio debugger starts one at a time.");
    }

    @Test
    @DisplayName("without the editor's debugger the configuration says so, before any trust question")
    void noDebugger() {
        VsCodeLaunchSearchProvider.launcher = () -> null;
        enter("Launch Program");
        assertThat(asked).isEmpty();
        assertThat(said).containsExactly(
                "Configuration \"Launch Program\" did not start: the breakpoint debugger is not installed.");
    }

    @Test
    @DisplayName("every refusal reason renders a sentence naming the configuration")
    void everyReasonRenders() {
        for (VsCodeLaunch.Reason reason : VsCodeLaunch.Reason.values()) {
            String s = VsCodeLaunchSearchProvider.refusal("cfg", new VsCodeLaunch.Refused(reason, "D"));
            assertThat(s).as(reason.name()).contains("cfg").doesNotContain("{");
        }
    }

    @Test
    @DisplayName("Enter runs off the calling (EDT) thread, on the provider's own lane")
    void enterIsOffTheCaller() {
        AtomicReference<String> thread = new AtomicReference<>();
        VsCodeLaunchSearchProvider.trustCheck = dir -> {
            thread.set(Thread.currentThread().getName());
            return false;
        };
        enter("Launch Program");
        assertThat(thread.get()).isNotEqualTo(Thread.currentThread().getName()).contains("vscode-launch");
    }

    @Test
    @DisplayName("the trust gate is the default, the facade is the debugger, and the gate sits before the hand-off")
    void trustGateIsWired() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/tools/vscode/VsCodeLaunchSearchProvider.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        assertThat(code).contains("trustCheck = dir -> WorkspaceTrust.requestTrust(dir)")
                .contains("launcher = DebugLauncher::find")
                .doesNotContain("CommandExecutor")
                .doesNotContain("ProcessBuilder");
        int m = code.indexOf("static void execute(");
        String body = code.substring(m, code.indexOf("\n    }\n", m));
        int refuse = body.indexOf("instanceof Refused");
        int gate = body.indexOf("trustCheck.test(project)");
        int handFile = body.indexOf("debugger.debug(");
        int handPage = body.indexOf("debugger.debugPage(");
        assertThat(refuse).isPositive();
        assertThat(gate).as("the trust gate is present").isGreaterThan(refuse);
        assertThat(handFile).as("the file hand-off comes after the gate").isGreaterThan(gate);
        assertThat(handPage).as("the page hand-off comes after the gate").isGreaterThan(gate);
        assertThat(code).contains("RP.post(() -> execute(project, config))");
    }

    @Test
    @DisplayName("registered under QuickSearch/VsCodeLaunches at 279, with its keys in every shipped language")
    void registered() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        NodeList folders = f.newDocumentBuilder()
                .parse(Path.of("src/main/resources/org/nmox/studio/tools/layer.xml").toFile())
                .getElementsByTagName("folder");
        Element folder = null;
        for (int i = 0; i < folders.getLength(); i++) {
            if (((Element) folders.item(i)).getAttribute("name").equals("VsCodeLaunches")) {
                folder = (Element) folders.item(i);
            }
        }
        assertThat(folder).isNotNull();
        assertThat(((Element) folder.getParentNode()).getAttribute("name")).isEqualTo("QuickSearch");
        NodeList attrs = folder.getElementsByTagName("attr");
        Map<String, String> values = new java.util.HashMap<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            values.put(a.getAttribute("name"), a.hasAttribute("intvalue")
                    ? a.getAttribute("intvalue") : a.getAttribute("stringvalue"));
        }
        assertThat(values).containsEntry("position", "279")
                .containsEntry("SystemFileSystem.localizingBundle", "org.nmox.studio.tools.vscode.Bundle");
        String instance = ((Element) folder.getElementsByTagName("file").item(0)).getAttribute("name");
        Class<?> provider = Class.forName(instance.substring(0, instance.length() - ".instance".length())
                .replace('-', '.'));
        assertThat(SearchProvider.class.isAssignableFrom(provider)).isTrue();

        java.util.Properties base = bundle("");
        assertThat(base.getProperty("QuickSearch/VsCodeLaunches")).isEqualTo("VS Code Launch Configurations");
        for (String key : base.stringPropertyNames()) {
            assertThat(base.getProperty(key)).as("English " + key + ": no ASCII apostrophe in a MessageFormat value")
                    .doesNotContain("'");
        }
        for (String lang : new String[] {"_es", "_fr", "_de", "_ru", "_hi", "_uk", "_pl", "_pt",
                "_id", "_tl", "_vi", "_zh", "_he", "_ar"}) {
            java.util.Properties p = bundle(lang);
            assertThat(p.stringPropertyNames()).as("Bundle" + lang).isEqualTo(base.stringPropertyNames());
            assertThat(p.getProperty("QuickSearch/VsCodeLaunches")).contains("VS Code");
        }
    }

    private static java.util.Properties bundle(String lang) throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newBufferedReader(Path.of(
                "src/main/resources/org/nmox/studio/tools/vscode/Bundle" + lang + ".properties"),
                StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }
}
