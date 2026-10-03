package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
                 "runtimeExecutable":"nodemon","runtimeArgs":["--inspect"],"preLaunchTask":"build",
                 "postDebugTask":"clean","restart":true}
                """));
        assertThat(r.reason()).isEqualTo(Reason.FIELDS);
        assertThat(r.detail()).as("args, env, envFile and the runtime pass; the tasks and the rest still cannot")
                .isEqualTo("postDebugTask, preLaunchTask, restart");

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
    @DisplayName("a Python or Chrome attach, compounds and types without an adapter are refused, naming what they are")
    void requestTypeCompound() {
        for (String type : new String[] {"python", "debugpy", "chrome", "pwa-chrome"}) {
            Refused attach = refused(resolve("{\"type\":\"" + type + "\",\"request\":\"attach\",\"name\":\"a\",\"port\":9229}"));
            assertThat(attach.reason()).as(type + ": only Node is attached to").isEqualTo(Reason.REQUEST);
            assertThat(attach.detail()).isEqualTo("attach");
        }
        Refused unknown = refused(resolve("""
                {"type":"node","request":"restart","name":"a","program":"server.js"}
                """));
        assertThat(unknown.reason()).isEqualTo(Reason.REQUEST);
        assertThat(unknown.detail()).isEqualTo("restart");

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
        for (String only : new String[] {"${input:port}", "${command:pickPort}", "${config:app.port}",
                "${lineNumber}", "${selectedText}", "${unterminated"}) {
            Refused r = refused(resolve("{\"type\":\"node\",\"request\":\"launch\",\"name\":\"n\","
                    + "\"program\":\"server.js\",\"args\":[\"" + only + "\"]}"));
            assertThat(r.reason()).as(only).isEqualTo(Reason.VARIABLE);
            assertThat(r.detail()).as(only).isEqualTo(only);
        }
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
                  "windows":{"program":"win.js"},"linux":{"runtimeExecutable":"node18","stopOnEntry":true}}]}
                """;
        Refused linux = refused(VsCodeLaunch.resolve(VsCodeLaunch.parse(json, Os.LINUX).get(0), project.toFile(), NO_ENV));
        assertThat(linux.detail()).isEqualTo("stopOnEntry");
        Resolved mac = VsCodeLaunch.resolve(VsCodeLaunch.parse(json, Os.MAC).get(0), project.toFile(), NO_ENV);
        assertThat(mac).isInstanceOf(DebugFile.class);
        assertThat(((DebugFile) mac).runtime()).as("Linux's runtime is Linux's").isNull();
    }

    /* ------------------------------------------------------------- envFile */

    @Test
    @DisplayName("envFile's variables are added, and an explicit env entry wins over the file (VS Code's rule)")
    void envFileUnderEnv() throws Exception {
        Files.writeString(project.resolve(".env"), """
                # the service
                PORT=3000
                MODE=from-file
                export TOKEN="s3cret value"
                ROOT=${workspaceFolder}
                """);
        DebugFile f = (DebugFile) resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js",
                 "envFile":"${workspaceFolder}/.env","env":{"MODE":"from-env","EXTRA":"1"}}
                """);
        assertThat(f.env()).containsEntry("PORT", "3000")
                .containsEntry("MODE", "from-env")
                .containsEntry("TOKEN", "s3cret value")
                .containsEntry("EXTRA", "1")
                .as("a file's text is the file's: launch.json variables are not filled inside it")
                .containsEntry("ROOT", "${workspaceFolder}");
        assertThat(f.toString()).as("a record that reaches a log names the variables, never their values")
                .contains("TOKEN").doesNotContain("s3cret").doesNotContain("from-env");

        DebugFile relative = (DebugFile) resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py","envFile":"conf/py.env"}
                """, "conf/py.env", "A=1\nB='two words'\n");
        assertThat(relative.env()).containsExactly(Map.entry("A", "1"), Map.entry("B", "two words"));
    }

    /** Resolve after writing {@code text} to {@code path} inside the project. */
    private Resolved resolve(String configJson, String path, String text) throws Exception {
        Path file = project.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return resolve(configJson);
    }

    @Test
    @DisplayName("a missing envFile is refused by name — VS Code ignores it, and the program would run without its variables")
    void envFileMissing() {
        Refused r = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","envFile":"${workspaceFolder}/.env.local"}
                """));
        assertThat(r.reason()).isEqualTo(Reason.MISSING);
        assertThat(r.detail()).isEqualTo("${workspaceFolder}/.env.local");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","envFile":"app"}
                """)).reason()).as("a folder is not an env file").isEqualTo(Reason.MISSING);
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","envFile":" "}
                """)).detail()).isEqualTo("envFile");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","envFile":[".env"]}
                """)).detail()).isEqualTo("envFile");
    }

    @Test
    @DisplayName("an envFile outside the project is refused by the containment guard, read or not")
    void envFileOutside() throws Exception {
        Path outside = Files.createTempFile("nmox-launch-outside", ".env");
        try {
            Files.writeString(outside, "LEAK=1\n");
            Refused abs = refused(resolve("{\"type\":\"node\",\"request\":\"launch\",\"name\":\"n\",\"program\":\"server.js\","
                    + "\"envFile\":\"" + outside.toString().replace("\\", "\\\\") + "\"}"));
            assertThat(abs.reason()).isEqualTo(Reason.OUTSIDE);
            assertThat(refused(resolve("""
                    {"type":"python","request":"launch","name":"n","program":"main.py","envFile":"../.env"}
                    """)).reason()).isEqualTo(Reason.OUTSIDE);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("an envFile too large to be one is refused, not read; a line VS Code reads differently is refused by number, never by value")
    void envFileUnreadableOrUnplain() throws Exception {
        Refused big = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","envFile":"big.env"}
                """, "big.env", "A=" + "x".repeat((int) VsCodeEnvFile.MAX_BYTES) + "\n"));
        assertThat(big.reason()).isEqualTo(Reason.UNREADABLE);
        assertThat(big.detail()).isEqualTo("big.env");

        Refused unplain = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","envFile":".env"}
                """, ".env", "OK=1\n\nURL=http://localhost/#hunter2\n"));
        assertThat(unplain.reason()).isEqualTo(Reason.ENV_LINE);
        assertThat(unplain.detail()).as("the file and the line; the value stays in the file")
                .isEqualTo(".env:3").doesNotContain("hunter2");

        assertThat(resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py","envFile":".env"}
                """)).as("the same line is plain to VS Code's Python reader, which keeps a # in a value")
                .isInstanceOf(DebugFile.class);
        assertThat(refused(resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py","envFile":"py.env"}
                """, "py.env", "HOME_BIN=${HOME}/bin\n")).detail()).isEqualTo("py.env:1");
    }

    /* ------------------------------------------------------------- runtime */

    @Test
    @DisplayName("runtimeExecutable and runtimeArgs pass on: a name as written, an absolute path that is there")
    void runtime() throws Exception {
        DebugFile named = (DebugFile) resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js",
                 "runtimeExecutable":"tsx","runtimeArgs":["--inspect-wait","${workspaceFolder}"]}
                """);
        assertThat(named.runtime()).isEqualTo("tsx");
        assertThat(named.runtimeArgs()).hasSize(2).startsWith("--inspect-wait");
        assertThat(named.runtimeArgs().get(1)).doesNotContain("${");
        assertThat(named.program().getName()).isEqualTo("server.js");

        DebugFile flags = (DebugFile) resolve("""
                {"type":"pwa-node","request":"launch","name":"n","program":"server.js",
                 "runtimeArgs":["--experimental-strip-types"]}
                """);
        assertThat(flags.runtime()).as("no runtime named: the adapter's own node").isNull();
        assertThat(flags.runtimeArgs()).containsExactly("--experimental-strip-types");

        Files.createDirectories(project.resolve("node_modules/.bin"));
        Files.writeString(project.resolve("node_modules/.bin/tsx"), "#!/bin/sh\n");
        DebugFile local = (DebugFile) resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js",
                 "runtimeExecutable":"${workspaceFolder}/node_modules/.bin/tsx"}
                """);
        assertThat(canonical(new File(local.runtime())))
                .isEqualTo(canonical(project.resolve("node_modules/.bin/tsx").toFile()));

        Refused gone = refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js",
                 "runtimeExecutable":"${workspaceFolder}/node_modules/.bin/nodemon"}
                """));
        assertThat(gone.reason()).isEqualTo(Reason.MISSING);
        assertThat(gone.detail()).isEqualTo("${workspaceFolder}/node_modules/.bin/nodemon");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js",
                 "runtimeExecutable":"./node_modules/.bin/tsx"}
                """)).detail()).as("a relative path: VS Code looks it up as a name, from a folder that is not the project")
                .isEqualTo("runtimeExecutable");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","runtimeExecutable":""}
                """)).detail()).isEqualTo("runtimeExecutable");
    }

    @Test
    @DisplayName("runtimeArgs that is not a list of strings is refused by name")
    void runtimeArgsShape() {
        for (String shape : new String[] {"\"--inspect --trace-warnings\"", "[\"--max-old-space-size\", 4096]",
                "{\"a\":1}", "[[\"--x\"]]"}) {
            Refused r = refused(resolve("{\"type\":\"node\",\"request\":\"launch\",\"name\":\"n\","
                    + "\"program\":\"server.js\",\"runtimeArgs\":" + shape + "}"));
            assertThat(r.reason()).as(shape).isEqualTo(Reason.FIELDS);
            assertThat(r.detail()).as(shape).isEqualTo("runtimeArgs");
        }
    }

    @Test
    @DisplayName("a Node runtime can be the whole command (npm run dev); nothing else stands without a program")
    void runtimeWithoutProgram() {
        DebugFile npm = (DebugFile) resolve("""
                {"type":"node","request":"launch","name":"dev","runtimeExecutable":"npm","runtimeArgs":["run","dev"]}
                """);
        assertThat(npm.program()).isNull();
        assertThat(npm.runtime()).isEqualTo("npm");
        assertThat(npm.runtimeArgs()).containsExactly("run", "dev");

        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","runtimeArgs":["--version"]}
                """)).reason()).as("arguments to the default runtime name nothing to debug").isEqualTo(Reason.NO_TARGET);
        assertThat(refused(resolve("""
                {"type":"python","request":"launch","name":"p","python":"python3.12"}
                """)).reason()).isEqualTo(Reason.NO_TARGET);
    }

    @Test
    @DisplayName("a Python configuration's interpreter passes on; a Node runtime field on it is refused by name")
    void pythonInterpreter() throws Exception {
        Files.createDirectories(project.resolve(".venv/bin"));
        Files.writeString(project.resolve(".venv/bin/python"), "");
        DebugFile venv = (DebugFile) resolve("""
                {"type":"debugpy","request":"launch","name":"p","program":"main.py",
                 "python":"${workspaceFolder}/.venv/bin/python"}
                """);
        assertThat(venv.kind()).isEqualTo(Kind.PYTHON);
        assertThat(canonical(new File(venv.runtime()))).isEqualTo(canonical(project.resolve(".venv/bin/python").toFile()));
        assertThat(((DebugFile) resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py","python":"python3.12"}
                """)).runtime()).isEqualTo("python3.12");

        assertThat(refused(resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py","runtimeArgs":["-X","dev"]}
                """)).detail()).isEqualTo("runtimeArgs");
        assertThat(refused(resolve("""
                {"type":"python","request":"launch","name":"p","program":"main.py","python":["python3","-X","dev"]}
                """)).detail()).as("debugpy's list form carries interpreter arguments this debugger does not pass")
                .isEqualTo("python");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"n","program":"server.js","python":"python3"}
                """)).detail()).isEqualTo("python");
    }

    @Test
    @DisplayName("on Windows a runtime named without its extension is there when the file with one is")
    void windowsRuntimeExtension() throws Exception {
        Files.createDirectories(project.resolve("tools"));
        Files.writeString(project.resolve("tools/node.exe"), "");
        File bare = project.resolve("tools/node").toFile();
        assertThat(VsCodeLaunch.executableThere(bare, Os.WINDOWS)).isTrue();
        assertThat(VsCodeLaunch.executableThere(bare, Os.LINUX)).isFalse();
        assertThat(VsCodeLaunch.executableThere(project.resolve("tools/deno").toFile(), Os.WINDOWS)).isFalse();
        assertThat(VsCodeLaunch.executableThere(project.resolve("tools/node.exe").toFile(), Os.MAC)).isTrue();
    }

    /* ---------------------------------------------------- the editor's file */

    @Test
    @DisplayName("\"program\": \"${file}\" debugs the file the editor shows; with no file open it is refused naming the variable")
    void currentFile() throws Exception {
        Config current = only("""
                {"configurations":[{"type":"node","request":"launch","name":"Current File","program":"${file}",
                  "cwd":"${fileDirname}","args":["${fileBasename}","${fileBasenameNoExtension}","${relativeFile}",
                  "${workspaceFolderBasename}"],"env":{"SELF":"${file}"}}]}
                """);
        Files.writeString(project.resolve("app/worker.js"), "1;\n");
        Path shown = project.resolve("app/worker.js");

        DebugFile f = (DebugFile) VsCodeLaunch.resolve(current, project.toFile(), NO_ENV, shown);
        assertThat(canonical(f.program())).isEqualTo(canonical(shown.toFile()));
        assertThat(canonical(f.cwd())).isEqualTo(canonical(project.resolve("app").toFile()));
        assertThat(f.args()).containsExactly("worker.js", "worker", "app" + File.separator + "worker.js",
                project.toFile().getName());
        assertThat(f.env().get("SELF")).isEqualTo(shown.toAbsolutePath().normalize().toString());

        Refused none = refused(VsCodeLaunch.resolve(current, project.toFile(), NO_ENV, null));
        assertThat(none.reason()).isEqualTo(Reason.NO_FILE);
        assertThat(none.detail()).as("the first one met, fields in name order: cwd comes before program")
                .isEqualTo("${fileDirname}");
        assertThat(refused(resolve("""
                {"type":"node","request":"launch","name":"Current File","program":"${file}"}
                """)).detail()).isEqualTo("${file}");
        assertThat(refused(VsCodeLaunch.resolve(only("""
                {"configurations":[{"type":"node","request":"launch","name":"n","program":"server.js",
                  "args":["--only","${relativeFile}"]}]}
                """), project.toFile(), NO_ENV)).detail()).isEqualTo("${relativeFile}");
    }

    @Test
    @DisplayName("a ${file} outside the project, or one its type does not run, meets the rules a written program meets")
    void currentFileKeepsTheRules() throws Exception {
        Config current = only("""
                {"configurations":[{"type":"node","request":"launch","name":"Current File","program":"${file}"}]}
                """);
        Path outside = Files.createTempFile("nmox-launch-outside", ".js");
        try {
            Refused out = refused(VsCodeLaunch.resolve(current, project.toFile(), NO_ENV, outside));
            assertThat(out.reason()).isEqualTo(Reason.OUTSIDE);
            assertThat(out.detail()).isEqualTo("${file}");
        } finally {
            Files.deleteIfExists(outside);
        }
        assertThat(refused(VsCodeLaunch.resolve(current, project.toFile(), NO_ENV, project.resolve("main.py")))
                .reason()).isEqualTo(Reason.PROGRAM_KIND);
        assertThat(refused(VsCodeLaunch.resolve(current, project.toFile(), NO_ENV, project.resolve("gone.js")))
                .reason()).isEqualTo(Reason.MISSING);
    }

    /* -------------------------------------------------------------- attach */

    @Test
    @DisplayName("a Node attach resolves to the inspector's address and port: 9229 on localhost unless written")
    void attach() throws Exception {
        VsCodeLaunch.AttachNode generated = (VsCodeLaunch.AttachNode) resolve("""
                {"name":"Attach","port":9229,"request":"attach","skipFiles":["<node_internals>/**"],"type":"node"}
                """);
        assertThat(generated.address()).isEqualTo("localhost");
        assertThat(generated.port()).isEqualTo(9229);
        assertThat(canonical(generated.cwd())).isEqualTo(canonical(project.toFile()));

        VsCodeLaunch.AttachNode bare = (VsCodeLaunch.AttachNode) resolve("""
                {"type":"pwa-node","request":"attach","name":"a"}
                """);
        assertThat(bare.port()).isEqualTo(VsCodeLaunch.DEFAULT_INSPECT_PORT);

        VsCodeLaunch.AttachNode written = (VsCodeLaunch.AttachNode) resolve("""
                {"type":"node","request":"attach","name":"a","port":"9333","address":"127.0.0.1","cwd":"app"}
                """);
        assertThat(written.address()).isEqualTo("127.0.0.1");
        assertThat(written.port()).isEqualTo(9333);
        assertThat(canonical(written.cwd())).isEqualTo(canonical(project.resolve("app").toFile()));
        assertThat(((VsCodeLaunch.AttachNode) resolve("""
                {"type":"node","request":"attach","name":"a","address":"::1"}
                """)).address()).isEqualTo("::1");
    }

    @Test
    @DisplayName("an attach to an address that is not this machine is refused by name: this is not remote development")
    void attachElsewhere() {
        for (String address : new String[] {"192.168.1.20", "build-box.local", "0.0.0.0", "10.0.0.5", "127.0.0.2",
                "localhost.example.com"}) {
            Refused r = refused(resolve("{\"type\":\"node\",\"request\":\"attach\",\"name\":\"a\",\"port\":9229,"
                    + "\"address\":\"" + address + "\"}"));
            assertThat(r.reason()).as(address).isEqualTo(Reason.ADDRESS);
            assertThat(r.detail()).as(address).isEqualTo(address);
        }
    }

    @Test
    @DisplayName("an attach's port must be a port, and what else it sets is refused by name")
    void attachShape() {
        for (String port : new String[] {"0", "65536", "\"abc\"", "9229.5", "\"\"", "[9229]", "99999999999"}) {
            Refused r = refused(resolve("{\"type\":\"node\",\"request\":\"attach\",\"name\":\"a\",\"port\":" + port + "}"));
            assertThat(r.reason()).as(port).isEqualTo(Reason.FIELDS);
            assertThat(r.detail()).as(port).isEqualTo("port");
        }
        Refused remote = refused(resolve("""
                {"type":"node","request":"attach","name":"a","port":9229,"restart":true,
                 "localRoot":"${workspaceFolder}","remoteRoot":"/app","processId":"${command:PickProcess}"}
                """));
        assertThat(remote.reason()).isEqualTo(Reason.FIELDS);
        assertThat(remote.detail()).isEqualTo("localRoot, processId, remoteRoot, restart");
        assertThat(refused(resolve("""
                {"type":"node","request":"attach","name":"a","program":"server.js"}
                """)).detail()).as("an attach starts no program").isEqualTo("program");
    }

    /* ------------------------------------------------------- preLaunchTask */

    @Test
    @DisplayName("preLaunchTask is refused by name unless the caller says it runs tasks; then its label is carried")
    void preLaunchTaskSeam() {
        Config config = only("""
                {"configurations":[{"type":"node","request":"launch","name":"n","program":"server.js","preLaunchTask":"build"}]}
                """);
        Refused refusedToday = refused(VsCodeLaunch.resolve(config, project.toFile(), NO_ENV));
        assertThat(refusedToday.detail()).isEqualTo("preLaunchTask");
        assertThat(refused(VsCodeLaunch.resolve(config, project.toFile(), NO_ENV, null, false)).detail())
                .isEqualTo("preLaunchTask");

        assertThat(VsCodeLaunch.resolve(config, project.toFile(), NO_ENV, null, true)).isInstanceOf(DebugFile.class);
        assertThat(VsCodeLaunch.preLaunchTask(config)).isEqualTo("build");

        Config object = only("""
                {"configurations":[{"type":"node","request":"launch","name":"n","program":"server.js",
                  "preLaunchTask":{"type":"npm","script":"build"},"postDebugTask":"clean"}]}
                """);
        assertThat(VsCodeLaunch.preLaunchTask(object)).isNull();
        assertThat(refused(VsCodeLaunch.resolve(object, project.toFile(), NO_ENV, null, true)).detail())
                .as("VS Code's object form is not a label, and postDebugTask is nobody's yet")
                .isEqualTo("postDebugTask, preLaunchTask");
    }

    @Test
    @DisplayName("display: an attach shows its address and port, a runtime-only launch its command")
    void displayOfAttachAndRuntime() {
        List<Config> configs = VsCodeLaunch.parse("""
                {"configurations":[{"type":"node","request":"attach","name":"a","port":9230},
                  {"type":"node","request":"attach","name":"b"},
                  {"type":"node","request":"launch","name":"c","runtimeExecutable":"npm","runtimeArgs":["run","dev"]},
                  {"type":"node","request":"launch","name":"d","runtimeExecutable":"tsx","program":"server.ts"}]}
                """, Os.LINUX);
        assertThat(configs.stream().map(VsCodeLaunch::display))
                .containsExactly("localhost:9230", "localhost:9229", "npm run dev", "server.ts");
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
