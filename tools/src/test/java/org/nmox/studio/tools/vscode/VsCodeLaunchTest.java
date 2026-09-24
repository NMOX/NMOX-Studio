package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Config;
import org.nmox.studio.tools.vscode.VsCodeLaunch.DebugFile;
import org.nmox.studio.tools.vscode.VsCodeLaunch.DebugPage;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Kind;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Reason;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Refused;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Resolved;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pure half of VS Code launch configurations (v3.1.0): what a
 * {@code .vscode/launch.json} declares, and for one configuration exactly
 * which file or page the debugger would start — or why not, out loud.
 */
class VsCodeLaunchTest {

    @TempDir
    Path project;

    private static final UnaryOperator<String> NO_ENV = name -> null;

    @BeforeEach
    void freshCache() throws Exception {
        VsCodeLaunch.clearCache();
        Files.writeString(project.resolve("server.js"), "console.log(1);\n");
        Files.writeString(project.resolve("main.py"), "print(1)\n");
        Files.createDirectories(project.resolve("app/public"));
        Files.writeString(project.resolve("app/public/index.html"), "<p>hi</p>\n");
    }

    private static Config only(String json) {
        List<Config> configs = VsCodeLaunch.parse(json, Os.LINUX);
        assertThat(configs).hasSize(1);
        return configs.get(0);
    }

    private Resolved resolve(String configJson) {
        return VsCodeLaunch.resolve(only("{\"configurations\":[" + configJson + "]}"), project.toFile(), NO_ENV);
    }

    private static Refused refused(Resolved r) {
        assertThat(r).isInstanceOf(Refused.class);
        return (Refused) r;
    }

    private static File canonical(File f) throws Exception {
        return f.getCanonicalFile();
    }

    @Test
    @DisplayName("VS Code's generated Node configuration debugs its program, skipFiles accepted, cwd the project")
    void nodeProgram() throws Exception {
        String json = """
                {
                  // the configuration VS Code writes for "Add Configuration… ▸ Node.js"
                  "version": "0.2.0",
                  "configurations": [
                    {
                      "type": "node",
                      "request": "launch",
                      "name": "Launch Program",
                      "skipFiles": ["<node_internals>/**"],
                      "program": "${workspaceFolder}/server.js",
                    },
                  ],
                }
                """;
        Resolved r = VsCodeLaunch.resolve(only(json), project.toFile(), NO_ENV);
        assertThat(r).isInstanceOf(DebugFile.class);
        DebugFile f = (DebugFile) r;
        assertThat(f.kind()).isEqualTo(Kind.NODE);
        assertThat(canonical(f.program())).isEqualTo(canonical(project.resolve("server.js").toFile()));
        assertThat(canonical(f.cwd())).isEqualTo(canonical(project.toFile()));
    }

    @Test
    @DisplayName("pwa-node with a relative program and a declared cwd inside the project")
    void nodeCwd() throws Exception {
        Resolved r = resolve("""
                {"type":"pwa-node","request":"launch","name":"n","program":"server.js","cwd":"${workspaceFolder}/app"}
                """);
        DebugFile f = (DebugFile) r;
        assertThat(canonical(f.cwd())).isEqualTo(canonical(project.resolve("app").toFile()));
    }

    @Test
    @DisplayName("a Python (debugpy) configuration debugs its .py program")
    void pythonProgram() throws Exception {
        DebugFile f = (DebugFile) resolve("""
                {"type":"debugpy","request":"launch","name":"py","program":"${workspaceFolder}/main.py","console":"integratedTerminal","justMyCode":false}
                """);
        assertThat(f.kind()).isEqualTo(Kind.PYTHON);
        assertThat(f.program().getName()).isEqualTo("main.py");
    }

    @Test
    @DisplayName("VS Code's generated Chrome configuration opens its URL with the project as web root")
    void chromeUrl() throws Exception {
        DebugPage p = (DebugPage) resolve("""
                {"type":"chrome","request":"launch","name":"Launch Chrome","url":"http://localhost:8080","webRoot":"${workspaceFolder}"}
                """);
        assertThat(p.url()).isEqualTo("http://localhost:8080");
        assertThat(canonical(p.webRoot())).isEqualTo(canonical(project.toFile()));

        DebugPage file = (DebugPage) resolve("""
                {"type":"pwa-chrome","request":"launch","name":"f","file":"${workspaceFolder}/app/public/index.html","webRoot":"app"}
                """);
        assertThat(file.url()).startsWith("file:").endsWith("index.html");
        assertThat(canonical(file.webRoot())).isEqualTo(canonical(project.resolve("app").toFile()));
    }

    @Test
    @DisplayName("args and env pass on in the shapes the adapters take; any other shape is refused by name")
    void argsAndEnv() throws Exception {
        Resolved ok = resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py",
                 "args":["--root","${workspaceFolder}"],"env":{"MODE":"dev","HOME_DIR":"${workspaceFolder}/x"}}
                """);
        assertThat(ok).isInstanceOf(VsCodeLaunch.DebugFile.class);
        VsCodeLaunch.DebugFile f = (VsCodeLaunch.DebugFile) ok;
        assertThat(f.args()).hasSize(2).startsWith("--root");
        assertThat(f.args().get(1)).doesNotContain("${");
        assertThat(f.env()).containsEntry("MODE", "dev").containsKey("HOME_DIR");
        assertThat(f.env().get("HOME_DIR")).doesNotContain("${").endsWith("x");

        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","args":"--port 3000"}
                """)).detail()).as("VS Code's one-string args is split by a shell this debugger does not run")
                .isEqualTo("args");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","args":["--port",3000]}
                """)).detail()).isEqualTo("args");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","env":{"DEBUG":null}}
                """)).detail()).as("a null unsets a variable, which cannot be passed on").isEqualTo("env");
        Refused v = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","args":["${input:port}"]}
                """));
        assertThat(v.reason()).isEqualTo(Reason.VARIABLE);
        assertThat(refused(resolve("""
                {"type":"chrome","request":"launch","name":"c","url":"http://localhost:1","args":["--x"]}
                """)).detail()).as("a page takes no program arguments").isEqualTo("args");
    }

    @Test
    @DisplayName("every field the debugger cannot pass on is refused BY NAME — never silently dropped")
    void unsupportedFieldsRefused() {
        Refused r = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js",
                 "args":["--port","3000"],"env":{"DEBUG":"*"},"envFile":".env",
                 "runtimeExecutable":"nodemon","runtimeArgs":["--inspect"],"preLaunchTask":"build"}
                """));
        assertThat(r.reason()).isEqualTo(Reason.FIELDS);
        assertThat(r.detail()).as("args and env pass since 3.1.0; the rest still cannot")
                .isEqualTo("envFile, preLaunchTask, runtimeArgs, runtimeExecutable");

        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","port":9229}
                """)).detail()).as("a field this class was never taught is refused, not ignored").isEqualTo("port");
        assertThat(refused(resolve("""
                {"type":"chrome","request":"launch","name":"c","url":"http://localhost:1","runtimeExecutable":"/x/chrome"}
                """)).detail()).isEqualTo("runtimeExecutable");
        assertThat(refused(resolve("""
                {"type":"chrome","request":"launch","name":"c","url":"http://localhost:1","file":"a.html"}
                """)).detail()).as("url and file together name two pages").isEqualTo("file");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":["server.js"]}
                """)).detail()).as("an honoured field in a shape we cannot pass is refused too").isEqualTo("program");
        assertThat(refused(resolve("""
                {"type":"chrome","request":"launch","name":"c","url":"http://localhost:1","cwd":"app"}
                """)).detail()).as("cwd is a Node/Python field; a Chrome launch cannot honour it").isEqualTo("cwd");
    }

    @Test
    @DisplayName("attach, compounds and types without an adapter are refused, naming what they are")
    void requestTypeCompound() {
        Refused attach = refused(resolve("""
                {"type":"node","request":"attach","name":"a","port":9229}
                """));
        assertThat(attach.reason()).isEqualTo(Reason.REQUEST);
        assertThat(attach.detail()).isEqualTo("attach");

        for (String type : new String[] {"go", "cppdbg", "msedge", "pwa-msedge", "java"}) {
            Refused t = refused(resolve("{\"type\":\"" + type + "\",\"request\":\"launch\",\"name\":\"x\",\"program\":\"server.js\"}"));
            assertThat(t.reason()).as(type).isEqualTo(Reason.TYPE);
            assertThat(t.detail()).isEqualTo(type);
        }

        List<Config> configs = VsCodeLaunch.parse("""
                {"configurations":[{"type":"node","request":"launch","name":"a","program":"server.js"}],
                 "compounds":[{"name":"Server + Client","configurations":["a","b"]}]}
                """, Os.LINUX);
        assertThat(configs).extracting(Config::name).containsExactly("a", "Server + Client");
        Refused compound = refused(VsCodeLaunch.resolve(configs.get(1), project.toFile(), NO_ENV));
        assertThat(compound.reason()).isEqualTo(Reason.COMPOUND);
    }

    @Test
    @DisplayName("a variable only VS Code can fill is refused naming the variable; env and workspace variables substitute")
    void variables() throws Exception {
        Refused file = refused(resolve("""
                {"type":"node","request":"launch","name":"Current File","program":"${file}"}
                """));
        assertThat(file.reason()).isEqualTo(Reason.VARIABLE);
        assertThat(file.detail()).isEqualTo("${file}");
        assertThat(refused(resolve("""
                {"type":"chrome","request":"launch","name":"c","url":"http://localhost:${input:port}"}
                """)).detail()).isEqualTo("${input:port}");

        Config env = only("""
                {"configurations":[{"type":"node","request":"launch","name":"e","program":"${env:ENTRY}"}]}
                """);
        DebugFile f = (DebugFile) VsCodeLaunch.resolve(env, project.toFile(), name -> "ENTRY".equals(name) ? "server.js" : null);
        assertThat(f.program().getName()).isEqualTo("server.js");
    }

    @Test
    @DisplayName("paths outside the project are refused by the containment guard — program, cwd, webRoot and file URLs")
    void containment() throws Exception {
        Path outside = Files.createTempFile("nmox-launch-outside", ".js");
        try {
            Refused abs = refused(resolve("{\"type\":\"node\",\"request\":\"launch\",\"name\":\"n\",\"program\":\""
                    + outside.toString().replace("\\", "\\\\") + "\"}"));
            assertThat(abs.reason()).isEqualTo(Reason.OUTSIDE);
            assertThat(refused(resolve("""
                    {"type":"node","request":"launch","name":"n","program":"../../../etc/passwd.js"}
                    """)).reason()).isEqualTo(Reason.OUTSIDE);
            assertThat(refused(resolve("""
                    {"type":"node","request":"launch","name":"n","program":"server.js","cwd":".."}
                    """)).reason()).isEqualTo(Reason.OUTSIDE);
            assertThat(refused(resolve("""
                    {"type":"chrome","request":"launch","name":"c","url":"http://localhost:1","webRoot":"../.."}
                    """)).reason()).isEqualTo(Reason.OUTSIDE);
            assertThat(refused(resolve("{\"type\":\"chrome\",\"request\":\"launch\",\"name\":\"c\",\"url\":\""
                    + outside.toUri() + "\"}")).reason()).isEqualTo(Reason.OUTSIDE);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("a missing program, a program its type does not run, no target at all, and a non-web URL each speak")
    void missingKindTargetUrl() {
        Refused missing = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"dist/nope.js"}
                """));
        assertThat(missing.reason()).isEqualTo(Reason.MISSING);
        assertThat(missing.detail()).isEqualTo("dist/nope.js");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"main.py"}
                """)).reason()).isEqualTo(Reason.PROGRAM_KIND);
        assertThat(refused(resolve("""
                {"type":"python","request":"launch","name":"n","program":"server.js"}
                """)).reason()).isEqualTo(Reason.PROGRAM_KIND);
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n"}
                """)).reason()).isEqualTo(Reason.NO_TARGET);
        assertThat(refused(resolve("""
                {"type":"chrome","request":"launch","name":"c"}
                """)).reason()).isEqualTo(Reason.NO_TARGET);
        for (String url : new String[] {"javascript:alert(1)", "chrome://settings", "data:text/html,x", "http://"}) {
            assertThat(refused(resolve("{\"type\":\"chrome\",\"request\":\"launch\",\"name\":\"c\",\"url\":\"" + url + "\"}"))
                    .reason()).as(url).isEqualTo(Reason.URL);
        }
    }

    @Test
    @DisplayName("the running OS's override is merged over the configuration; the others are ignored")
    void osOverride() {
        String json = """
                {"configurations":[{"type":"node","request":"launch","name":"n","program":"server.js",
                  "windows":{"program":"win.js"},"linux":{"runtimeExecutable":"node18"}}]}
                """;
        Refused linux = refused(VsCodeLaunch.resolve(VsCodeLaunch.parse(json, Os.LINUX).get(0), project.toFile(), NO_ENV));
        assertThat(linux.detail()).isEqualTo("runtimeExecutable");
        Resolved mac = VsCodeLaunch.resolve(VsCodeLaunch.parse(json, Os.MAC).get(0), project.toFile(), NO_ENV);
        assertThat(mac).isInstanceOf(DebugFile.class);
    }

    @Test
    @DisplayName("configurations without a name are skipped; display shows the program or page as written")
    void namesAndDisplay() {
        List<Config> configs = VsCodeLaunch.parse("""
                {"configurations":[{"type":"node","program":"x.js"},
                  {"type":"node","request":"launch","name":"b","program":"${workspaceFolder}/server.js"},
                  {"type":"chrome","request":"launch","name":"c","url":"http://localhost:4200"}]}
                """, Os.LINUX);
        assertThat(configs).extracting(Config::name).containsExactly("b", "c");
        assertThat(VsCodeLaunch.display(configs.get(0))).isEqualTo("${workspaceFolder}/server.js");
        assertThat(VsCodeLaunch.display(configs.get(1))).isEqualTo("http://localhost:4200");
    }

    @Test
    @DisplayName("read: no file lists nothing; a malformed or oversize file lists nothing and does not throw")
    void readsBounded() throws Exception {
        assertThat(VsCodeLaunch.read(project.toFile())).isEmpty();
        Files.createDirectories(project.resolve(".vscode"));
        Path launch = project.resolve(".vscode/launch.json");

        Files.writeString(launch, "{ \"configurations\": [ { \"name\": ");
        assertThat(VsCodeLaunch.read(project.toFile())).isEmpty();

        Files.writeString(launch, "{\"configurations\":[{\"type\":\"node\",\"request\":\"launch\",\"name\":\"ok\","
                + "\"program\":\"server.js\"}],\"pad\":\"" + "x".repeat((int) VsCodeLaunch.MAX_BYTES) + "\"}");
        VsCodeLaunch.clearCache();
        assertThat(VsCodeLaunch.read(project.toFile())).as("over the cap: refused, not read").isEmpty();

        Files.writeString(launch, "{\"configurations\":[{\"type\":\"node\",\"request\":\"launch\",\"name\":\"ok\","
                + "\"program\":\"server.js\"}]}");
        VsCodeLaunch.clearCache();
        assertThat(VsCodeLaunch.read(project.toFile())).extracting(Config::name).containsExactly("ok");
    }

    @Test
    @DisplayName("parse throws on text that is not a JSON object (read turns that into an empty list)")
    void parseThrows() {
        assertThatThrownBy(() -> VsCodeLaunch.parse("[]", Os.LINUX)).isInstanceOf(org.json.JSONException.class);
    }
}
