package org.nmox.studio.editor.lsp;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.nmox.studio.editor.lsp.LanguageServerCatalog.Server;
import org.openide.awt.NotificationDisplayer;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

/**
 * A language server that runs a project's code waits until the project is
 * trusted (3.5.6).
 *
 * <p>Most language servers do not only read. rust-analyzer builds the
 * project to analyse it, and a build runs {@code build.rs}; a Perl server
 * checks syntax with {@code perl -c}, which runs {@code BEGIN} blocks; R
 * starts in the project directory and sources its {@code .Rprofile};
 * typescript-language-server loads the TypeScript in the workspace's own
 * {@code node_modules}. The editor starts a server when a file is opened.
 * So opening a file of a stranger's repository ran that repository's code,
 * with no question asked: measured in 3.5.5 on a scratch Cargo project whose
 * {@code build.rs} wrote a marker file within a minute of {@code main.rs}
 * being opened.
 *
 * <p>The product's law is that nothing a project controls runs before the
 * project is trusted. v1.102.0 applied it to a server binary inside the
 * project and v1.216.0 to two servers whose configuration is code; this is
 * the same law for the rest, at the one place every server is launched.
 *
 * <p>Every binary {@link LanguageServers} launches is written down here as
 * one of three things, with the reason: it RUNS project code, it only
 * READS, or it is HELD, which means nobody has established that it runs
 * nothing, and it is treated as running until someone does. A binary that
 * is not written down is held too. {@code ServerTrustLedgerTest} derives
 * the list of binaries from the source, so a server added without a line
 * here fails the build.
 *
 * <p>A refused server says so once per project in a notification, and the
 * click is the trust question itself. A file that belongs to no project has
 * no project to trust: for it, only the servers that READ start.
 *
 * <p>When the project is trusted, through any door, the servers that were
 * refused start for the files already open (3.5.10): see {@link #granted}.
 */
final class ServerTrust {

    private static final Logger LOG = Logger.getLogger(ServerTrust.class.getName());

    private ServerTrust() {
    }

    /** What a server does with the project it is pointed at. */
    enum Reach {
        /** Runs code the project carries: a build, a macro, a plugin, a configuration that is a program. */
        RUNS,
        /** Not established to run nothing. Treated as RUNS until it is. */
        HELD,
        /** Parses. Anything it executes is the user's own tool on files it treats as data. */
        READS
    }

    record Entry(Reach reach, String why) {
    }

    /** What the launch site is told. */
    enum Verdict {
        START,
        /** The file's project has not been trusted. */
        UNTRUSTED,
        /** The file belongs to no project, so there is nothing to trust. */
        NO_PROJECT
    }

    /** Whether a directory is trusted; the silent check, never the prompt. Swapped by tests. */
    static Predicate<File> trusted = org.nmox.studio.rack.service.WorkspaceTrust::isTrusted;

    static final Map<String, Entry> SERVERS = servers();

    private static Map<String, Entry> servers() {
        Map<String, Entry> m = new LinkedHashMap<>();
        // -- runs: the mechanism is the server's documented way of working
        runs(m, "rust-analyzer", "builds through cargo: build.rs and procedural macros run (measured, 3.5.5)");
        runs(m, "elixir-ls", "compiles the project: mix.exs and macros are Elixir");
        runs(m, "language_server.sh", "elixir-ls under its script name");
        runs(m, "jdtls", "imports Gradle and Maven builds: build scripts and annotation processors run");
        runs(m, "OmniSharp", "evaluates the project with MSBuild: targets, analyzers and source generators run");
        runs(m, "csharp-ls", "evaluates the project with MSBuild: targets, analyzers and source generators run");
        runs(m, "fsautocomplete", "MSBuild evaluation, and F# type providers run at design time");
        runs(m, "ruby-lsp", "sets up a bundle: the Gemfile is Ruby");
        runs(m, "solargraph", "loads the bundle and the plugins .solargraph.yml names");
        runs(m, "dart", "runs the analyzer plugins analysis_options.yaml names");
        runs(m, "metals", "imports the build through sbt or Bloop: build.sbt is Scala");
        runs(m, "kotlin-language-server", "resolves the classpath by running Gradle or Maven");
        runs(m, "sourcekit-lsp", "SwiftPM compiles and runs Package.swift and build plugins");
        runs(m, "haskell-language-server-wrapper", "runs Cabal or Stack setup; Template Haskell runs while type-checking");
        runs(m, "zls", "runs the build runner on build.zig");
        runs(m, "nimlangserver", "evaluates NimScript configuration and compile-time macros, which may call staticExec");
        runs(m, "scarb", "builds the procedural-macro plugins the manifest names");
        runs(m, "elm-language-server", "runs elm, elm-test and elm-format from the project's node_modules when they are there");
        runs(m, "rescript-language-server", "runs the compiler binaries in the project's node_modules");
        runs(m, "purescript-language-server", "starts purs ide and the build the project configures");
        runs(m, "racket", "expands macros: the file's compile-time code runs");
        runs(m, "janet-lsp", "evaluates top-level forms and macros to check a file");
        runs(m, "erlang_ls", "compiles with the parse transforms the project defines");
        runs(m, "clojure-lsp", "finds the classpath by running the project's build tool: project.clj is Clojure");
        runs(m, "cl-lsp", "compiles and loads code in a live Lisp image");
        runs(m, "lua-language-server", "loads the plugin a .luarc.json names");
        runs(m, "ocamllsp", "runs the PPX rewriters the project's dune files build");
        runs(m, "crystalline", "runs compile-time macros, which may call run and system");
        runs(m, "julia", "loads the packages the project's environment names");
        runs(m, "R", "starts in the project directory and sources its .Rprofile");
        runs(m, "perl", "syntax checking is perl -c, which runs BEGIN blocks");
        runs(m, "pls", "syntax checking is perl -c, which runs BEGIN blocks");
        runs(m, "groovy-language-server", "compiles with AST transformations and @Grab");
        runs(m, "graphql-lsp", "loads graphql.config.js, which is JavaScript");
        runs(m, "svelteserver", "loads svelte.config.js, which is JavaScript");
        runs(m, "astro-ls", "loads TypeScript and Astro packages from the project's node_modules");
        runs(m, "nomicfoundation-solidity-language-server", "loads hardhat.config through the project's own Hardhat");
        runs(m, "phpactor", "includes the project's Composer autoloader");
        runs(m, "deno", "runs the lint plugins deno.json names");
        runs(m, "vscode-eslint-language-server", "evaluates the project's ESLint configuration (its own gate since v1.216.0)");
        runs(m, "stylelint-lsp", "evaluates the project's stylelint configuration (its own gate since v1.232.0)");
        runs(m, "ngserver", "loads TypeScript and the Angular language service from the project (its own gate since v1.216.0)");
        runs(m, "vue-language-server", "loads TypeScript from the project (its own gate since v2.14.0)");
        runs(m, "typescript-language-server",
                "loads the TypeScript in the workspace's node_modules when there is one; see runsHere");
        // -- held: not established either way
        held(m, "gleam", "compiles the project and fetches its dependencies; not established that nothing of the project runs");
        held(m, "serve-d", "resolves through dub, whose recipes can carry commands; not established which it runs");
        held(m, "prisma-language-server", "not established whether it loads a prisma.config.ts");
        // -- reads
        reads(m, "vscode-json-language-server", "a parser; fetches the schema a file names, runs nothing");
        reads(m, "vscode-html-language-server", "a parser for the markup; runs nothing in it");
        reads(m, "vscode-css-language-server", "a parser for the stylesheet");
        reads(m, "yaml-language-server", "a parser; fetches the schema a file names, runs nothing");
        reads(m, "taplo", "a parser for TOML");
        reads(m, "docker-langserver", "a parser for the Dockerfile; builds nothing");
        reads(m, "bash-language-server", "a parser; ShellCheck, the user's own, reads the script and does not run it");
        reads(m, "clangd", "runs no compiler a compile_commands.json names unless started with --query-driver, which this product never passes");
        reads(m, "gopls", "drives the user's own go list; Go has no build scripts, and cgo flags are held to an allowlist by the toolchain");
        reads(m, "intelephense", "a static analyser; includes nothing from the project");
        reads(m, "pyright-langserver", "runs the user's own interpreter on a script of its own, with the working directory"
                + " taken off the import path; a configuration file names a venv to read, not an interpreter to run,"
                + " and this client never names one (measured, 3.5.10: scripts/probes/pyright-trust)");
        reads(m, "fortls", "a parser for Fortran sources");
        reads(m, "ada_language_server", "reads project files, which cannot name a command");
        reads(m, "ols", "a parser for Odin sources");
        reads(m, "v-analyzer", "a parser and an indexer of its own");
        reads(m, "move-analyzer", "compiles Move in process; Move has no build scripts, and a fetched dependency is not run");
        return java.util.Collections.unmodifiableMap(m);
    }

    private static void runs(Map<String, Entry> m, String binary, String why) {
        m.put(binary, new Entry(Reach.RUNS, why));
    }

    private static void held(Map<String, Entry> m, String binary, String why) {
        m.put(binary, new Entry(Reach.HELD, why));
    }

    private static void reads(Map<String, Entry> m, String binary, String why) {
        m.put(binary, new Entry(Reach.READS, why));
    }

    /** The name a command is known by: its file name, whether it was written bare or resolved to a path. */
    static String binaryOf(String command) {
        String name = new File(command).getName();
        // npm's Windows shims, and a launcher written with its extension
        for (String suffix : new String[] {".cmd", ".exe", ".bat"}) {
            if (name.toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    static Reach reach(String binary) {
        Entry entry = SERVERS.get(binary);
        return entry == null ? Reach.HELD : entry.reach();
    }

    /**
     * Whether this server would run this project's code. For most servers
     * that is what RUNS means. typescript-language-server is the exception
     * worth making: it runs the workspace's own TypeScript only when the
     * workspace has one, and otherwise the user's. A cloned repository
     * before {@code npm install} keeps its TypeScript and JavaScript
     * intelligence, and one that arrives with a {@code node_modules} waits.
     */
    static boolean runsHere(String binary, File projectDir) {
        if (reach(binary) == Reach.READS) {
            return false;
        }
        if ("typescript-language-server".equals(binary)) {
            // the same candidate the server and TsServerPrecheck look at
            return projectDir != null && new File(projectDir, "node_modules/typescript").exists();
        }
        return true;
    }

    /** The decision, with no side effect: whether the server for this command may start for this project. */
    static Verdict decide(String command, File projectDir) {
        String binary = binaryOf(command);
        if (!runsHere(binary, projectDir)) {
            return Verdict.START;
        }
        if (projectDir == null) {
            return Verdict.NO_PROJECT;
        }
        return trusted.test(projectDir) ? Verdict.START : Verdict.UNTRUSTED;
    }

    /** Spoken once per project, and once per binary for files that have none. */
    private static final Set<String> SPOKEN = ConcurrentHashMap.newKeySet();

    /**
     * The decision at the launch site: true when the server must not start,
     * having said why.
     */
    static boolean refuses(String command, File projectDir) {
        Verdict verdict = decide(command, projectDir);
        if (verdict == Verdict.START) {
            return false;
        }
        REFUSED.set(Boolean.TRUE);
        if (verdict == Verdict.UNTRUSTED) {
            WAITING.putIfAbsent(projectDir, binaryOf(command));
        }
        speak(binaryOf(command), projectDir, verdict);
        return true;
    }

    private static void speak(String binary, File projectDir, Verdict verdict) {
        // a walk reads logs, not balloons: the refusal is in the log every time it is the first
        String key = projectDir == null ? "no project: " + binary : projectDir.getAbsolutePath();
        if (!SPOKEN.add(key)) {
            return;
        }
        LOG.info(verdict == Verdict.UNTRUSTED
                ? binary + " was not started: it runs code from the project, and " + projectDir + " is not trusted"
                : binary + " was not started: it runs code from the project it reads, and this file belongs to no project");
        if (LanguageServerHealth.forgeRun()) {
            return; // a docs picture shows the product, not this machine's trust store
        }
        Server server = LanguageServerCatalog.forBinary(binary);
        String language = server != null ? server.language() : binary;
        String title = NbBundle.getMessage(ServerTrust.class, "ServerTrust_title", language);
        if (verdict == Verdict.NO_PROJECT) {
            NotificationDisplayer.getDefault().notify(title, icon(),
                    NbBundle.getMessage(ServerTrust.class, "ServerTrust_noProject", binary), null);
            return;
        }
        if (!noticeFirst.test(projectDir)) {
            // the git chip already said this folder, or one above it, is waiting, and its
            // click is the same question: one decision gets one notice (3.5.10)
            return;
        }
        NotificationDisplayer.getDefault().notify(title, icon(),
                NbBundle.getMessage(ServerTrust.class, "ServerTrust_detail", binary, projectDir.getName()),
                e -> ask(projectDir));
    }

    /** Whether a folder's notice is the first this session, across git and the servers. Swapped by tests. */
    static Predicate<File> noticeFirst = org.nmox.studio.rack.service.TrustNotices::firstFor;

    /** The click: the trust question itself. What follows a yes is {@link #granted}, as for any other door. */
    private static void ask(File projectDir) {
        org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(projectDir);
    }

    /** The projects a server was refused for, and one of the servers, for the message if a restart cannot be done. */
    private static final Map<File, String> WAITING = new ConcurrentHashMap<>();

    /** Re-opens every open editor in its servers; false when it could not. Swapped by tests. */
    static java.util.function.BooleanSupplier restart = ServerRestart::reopenEditorsInServers;

    /** Where the restart runs: it starts processes. Swapped by tests. */
    static java.util.function.Consumer<Runnable> lane =
            new org.openide.util.RequestProcessor("Language server trust", 1)::post;

    /** What the status line is told. Swapped by tests. */
    static java.util.function.Consumer<String> status = text ->
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(text));

    /**
     * A folder was trusted. If a server was waiting for it, or for a
     * project inside it, the servers start now for the files already open.
     *
     * <p>3.5.6 said "reopen the file": the platform's client opens a
     * document in a server once, when its editor appears, so a server
     * started later knew none of the open files. The client has its own
     * way to send them again, the one it uses when a server is connected
     * by hand, and this asks for it ({@link ServerRestart}). When that way
     * is not there, the old sentence is said instead, so nothing is
     * promised that did not happen.
     *
     * @return whether anything was waiting under the folder
     */
    static boolean granted(File trustedDir) {
        if (trustedDir == null) {
            return false;
        }
        String root = trustedDir.getAbsolutePath();
        String binary = null;
        for (java.util.Iterator<Map.Entry<File, String>> it = WAITING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<File, String> waiting = it.next();
            String path = waiting.getKey().getAbsolutePath();
            if (path.equals(root) || path.startsWith(root + File.separator)) {
                binary = waiting.getValue();
                it.remove();
                // a later refusal for this project, should trust be withdrawn by hand, speaks again
                SPOKEN.remove(path);
            }
        }
        if (binary == null) {
            return false;
        }
        String waited = binary;
        lane.accept(() -> status.accept(restart.getAsBoolean()
                ? NbBundle.getMessage(ServerTrust.class, "ServerTrust_started")
                : NbBundle.getMessage(ServerTrust.class, "ServerTrust_reopen", waited)));
        return true;
    }

    static {
        org.nmox.studio.rack.service.WorkspaceTrust.addGrantListener(ServerTrust::granted);
    }

    private static javax.swing.Icon icon() {
        return LanguageServerHealth.ICON;
    }

    /**
     * Whether the launch that just returned nothing on this thread was a
     * refusal here. The caller reports a server that did not start as
     * missing, with how to install it, and a server that is installed and
     * waiting for trust is not missing. Asked once: it forgets its answer.
     */
    static boolean tookRefusal() {
        boolean refused = Boolean.TRUE.equals(REFUSED.get());
        REFUSED.remove();
        return refused;
    }

    private static final ThreadLocal<Boolean> REFUSED = new ThreadLocal<>();

    static void forgetForTest() {
        SPOKEN.clear();
        WAITING.clear();
    }
}
