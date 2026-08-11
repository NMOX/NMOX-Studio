package org.nmox.studio.editor.lsp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.project.Project;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider;
import org.nmox.studio.core.process.ToolLocator;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Real language intelligence: each provider launches the language's
 * own server over stdio and hands it to the platform's LSP client -
 * definitions, hover, semantic completion, rename and live
 * diagnostics arrive wholesale. Servers are found through ToolLocator
 * (Homebrew, npm, cargo, go, dotnet tool, coursier, gem installs); a
 * missing server degrades silently to the TextMate-level experience.
 *
 * Where an ecosystem has competing servers the provider tries them in
 * preference order (ruby-lsp before solargraph, csharp-ls before
 * OmniSharp) so whichever one the developer actually installed wins.
 */
public final class LanguageServers {

    private LanguageServers() {
    }

    /** Launches a server command in the project root; null when unavailable. */
    static LanguageServerProvider.LanguageServerDescription launch(Lookup lookup, List<String> command) {
        try {
            File dir = projectDir(lookup);
            List<String> resolved = ToolLocator.resolveCommand(command);
            if (refusesCommand(resolved.get(0), command.get(0))) {
                return null;
            }
            ProcessBuilder pb = new ProcessBuilder(resolved);
            if (dir != null) {
                pb.directory(dir);
            }
            pb.environment().put("PATH", ToolLocator.augmentedPath());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            // the Lookup carries the languageId mapping: without it the
            // client sends the RAW MIME as didOpen's languageId and
            // id-keyed servers (ngserver above all) silently ignore the
            // document — see LspLanguageIds
            return LanguageServerProvider.LanguageServerDescription.create(
                    process.getInputStream(), process.getOutputStream(), process,
                    org.openide.util.lookup.Lookups.fixed(new LspLanguageIds()));
        } catch (IOException ex) {
            // no popup: a missing language server is a normal condition, but
            // the log should say why intelligence is absent for this mime
            java.util.logging.Logger.getLogger(LanguageServers.class.getName())
                    .log(java.util.logging.Level.INFO,
                            "Language server failed to launch: {0} ({1})",
                            new Object[]{command, ex.getMessage()});
            return null;
        }
    }

    /** A single-server provider call: launch, and on failure tell the user how to install it. */
    static LanguageServerProvider.LanguageServerDescription provide(Lookup lookup, List<String> command) {
        return reported(launch(lookup, command), command.get(0));
    }

    /** Notifies (once per session) how to install {@code primaryBinary} when the server didn't start. */
    static LanguageServerProvider.LanguageServerDescription reported(
            LanguageServerProvider.LanguageServerDescription result, String primaryBinary) {
        if (result == null) {
            LanguageServerHealth.reportMissing(primaryBinary);
        }
        return result;
    }

    /** The first candidate that launches wins; null when none can. */
    @SafeVarargs
    static LanguageServerProvider.LanguageServerDescription launchFirst(
            Lookup lookup, List<String>... candidates) {
        for (List<String> candidate : candidates) {
            LanguageServerProvider.LanguageServerDescription server = launch(lookup, candidate);
            if (server != null) {
                return server;
            }
        }
        return null;
    }

    /**
     * For npm-distributed servers: prefer the project's own
     * node_modules/.bin install over the global binary, so the server
     * version matches what the project pinned.
     */
    static LanguageServerProvider.LanguageServerDescription launchNpm(
            Lookup lookup, String bin, String... args) {
        File dir = projectDir(lookup);
        File local = dir == null ? null : new File(dir, "node_modules/.bin/" + bin);
        // A committed node_modules/.bin/<server> is attacker-controlled
        // code in a cloned repo; running it on file-open is RCE. Only
        // prefer the project-LOCAL binary when the workspace is trusted
        // (a SILENT check — the LSP client calls this constantly, so it
        // must never prompt); untrusted, fall back to the user's own
        // global tool on PATH. The debug actions already gate their
        // spawns; the LSP layer must too.
        boolean useLocal = local != null && local.canExecute()
                && org.nmox.studio.rack.service.WorkspaceTrust.isTrusted(dir);
        List<String> cmd = new ArrayList<>();
        cmd.add(useLocal ? local.getAbsolutePath() : bin);
        cmd.addAll(List.of(args));
        // report the package name, not the resolved node_modules path
        return reported(launch(lookup, cmd), bin);
    }

    /**
     * True when the command's first word cannot possibly run: a bare
     * unresolved name that is nowhere on the PATH. An ABSOLUTE path is
     * judged by the file itself — v1.218.0: the old bare-name check ran
     * {@code new File(pathDir, absolutePath)} for every PATH entry,
     * which never resolves, so every absolute command was refused —
     * including the project-local {@code .bin/ngserver} the v1.216.0
     * fix resolves. The Angular Language Service could never launch.
     */
    static boolean refusesCommand(String resolvedFirst, String originalFirst) {
        File first = new File(resolvedFirst);
        if (first.isAbsolute()) {
            return !first.canExecute();
        }
        return resolvedFirst.equals(originalFirst) && !onPath(originalFirst);
    }

    private static boolean onPath(String name) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (new File(dir, name).canExecute()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The nearest directory at-or-above {@code start} carrying
     * {@code angular.json}, or null. Bounded walk — the v1.223.0 cure
     * applied to the LSP consumer.
     */
    static File angularRootAbove(File start) {
        File cursor = start;
        for (int up = 0; cursor != null && up < 8; up++, cursor = cursor.getParentFile()) {
            if (new File(cursor, "angular.json").isFile()) {
                return cursor;
            }
        }
        return null;
    }

    /** Diagnostics for the ALS chain, silent unless -Dnmox.ng.probe. */
    private static void ngProbe(String msg) {
        if (Boolean.getBoolean("nmox.ng.probe")) {
            System.err.println("[ng-probe] AngularServer " + msg);
        }
    }

    private static File projectDir(Lookup lookup) {
        Project project = lookup.lookup(Project.class);
        return project == null ? null : FileUtil.toFile(project.getProjectDirectory());
    }

    /**
     * TypeScript via typescript-language-server — except in Angular
     * workspaces, where the mime is ngserver's ALONE (ledger 81).
     *
     * <p>Why suppression and not capability games: the platform's rename
     * refactoring collects edit sets from EVERY server bound to the mime
     * — decompiled, {@code RenameRefactoringPlugin.lambda$prepare$6},
     * the capability predicate it filters bindings with, is
     * {@code iconst_1; ireturn}: always true, renameProvider never
     * consulted. With tsserver AND ngserver both bound, a class-property
     * rename applied tsserver's declaration edit AND ngserver's
     * declaration+template edits — {@code headingheading}, proven live
     * twice (the second time through a filter that verifiably stripped
     * tsserver's renameProvider, which changed nothing). The only lever
     * the platform leaves is WHICH servers are bound; ngserver wraps the
     * TypeScript language service (that is what --tsProbeLocations is
     * for), so it serves the .ts intelligence itself.
     */
    @MimeRegistration(mimeType = "text/typescript", service = LanguageServerProvider.class)
    public static final class TypeScriptServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            boolean angular = angularRootAbove(projectDir(lookup)) != null;
            ngProbe("tsserver start: projectDir=" + projectDir(lookup)
                    + " angular=" + angular);
            if (angular) {
                return null; // ngserver owns TypeScript here
            }
            return launchNpm(lookup, "typescript-language-server", "--stdio");
        }
    }

    /**
     * JavaScript rides the same typescript-language-server, registered
     * separately so the ledger-81 Angular suppression above cannot take
     * plain .js files down with it (ngserver serves .ts and templates,
     * not .js).
     */
    @MimeRegistration(mimeType = "text/javascript", service = LanguageServerProvider.class)
    public static final class JavaScriptTsServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "typescript-language-server", "--stdio");
        }
    }

    /**
     * eslint via {@code vscode-eslint-language-server}.
     *
     * <p>Until v1.213.0 the honest matrix for a fresh React/TypeScript
     * project was lopsided: TYPE errors arrived automatically, because
     * {@link TypeScriptServer} is registered on both JS and TS mimes and
     * the LSP client starts it on file-open — but ESLINT findings, the
     * ones a JS developer actually stares at all day, existed only if you
     * mounted the PURITY rack device and pressed its button. Lint was the
     * one diagnostic gated behind learning a metaphor.
     *
     * <p>This runs eslint the way every other editor does. The premise
     * that makes it safe was decompiled rather than assumed: the
     * platform's {@code LSPBindings} collects providers with
     * {@code MimeLookup.getLookup(mime).lookupAll(...)} — a Collection —
     * so a second server on a mime joins the first rather than replacing
     * it. tsserver keeps reporting types; eslint adds rules.
     *
     * <p>The rack lane is untouched and still useful: PURITY lints the
     * whole project on demand and feeds the Action Items window, where
     * the LSP reports the file you have open. Two answers to two
     * different questions.
     *
     * <p>Absent eslint means no diagnostics, not an error — the same
     * honest degradation every other entry here has (see
     * {@code LanguageServerCatalog}, which surfaces the install hint).
     */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/typescript", service = LanguageServerProvider.class)
    })
    public static final class EslintServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            File dir = projectDir(lookup);
            // v1.234.0 (arc review): no project means no trust anchor and
            // no config gate — but eslint resolves its config by walking
            // UP from the linted FILE, and an eslint config is executable
            // JavaScript. A lone .js opened out of an untrusted checkout
            // would hand that repo's config to the global server: the
            // v1.216.0 payload law with both gates skipped. No project,
            // no server — a file that belongs to no workspace has no
            // workspace that opted in.
            if (dir == null) {
                return null;
            }
            // v1.216.0 (arc review): launchNpm's trust gate covers the
            // server BINARY, but this server's payload is the
            // workspace itself — it resolves the eslint LIBRARY from
            // the project's node_modules and evaluates the project's
            // eslint.config.js / .eslintrc.js, which are plain
            // executable JavaScript. A trusted global binary running
            // an untrusted repo's config is still the v1.102.0 RCE on
            // file-open. Same silent gate, same honest degradation:
            // untrusted workspaces get no lint diagnostics.
            if (!org.nmox.studio.rack.service.WorkspaceTrust.isTrusted(dir)) {
                return null;
            }
            // No eslint config, no server: every JS/TS project would
            // otherwise pay a node process for a linter it never
            // adopted (the global binary ships in the same package as
            // the JSON/HTML/CSS servers, so having it installed does
            // not mean wanting eslint everywhere).
            if (!hasEslintConfig(dir)) {
                return null;
            }
            return launchNpm(lookup, "vscode-eslint-language-server", "--stdio");
        }
    }

    /**
     * stylelint via {@code stylelint-lsp} — the eslint story (v1.213.0),
     * told for stylesheets (v1.232.0, the Senior CSS3 pass).
     *
     * <p>The platform's own CSS grammar predates CSS Color 4 and flags
     * valid modern syntax ({@code color-mix()}, space-separated values)
     * as warnings — and tech-debt ledger 71 records, with decompiled
     * evidence, that those false positives cannot be silenced from
     * outside the platform module. stylelint is the linter that DOES
     * understand modern CSS (nesting, {@code @container},
     * {@code oklch()}), so a project that adopts it gets correct,
     * current diagnostics beside the legacy parser's noise — the same
     * two-linters-one-truth arrangement every modern editor ships.
     *
     * <p>Same laws as {@link EslintServer}: the server joins the css
     * family's other providers rather than replacing them (LSPBindings
     * collects ALL providers per mime); a stylelint config is executable
     * JavaScript and the library resolves from the project's
     * node_modules, so untrusted workspaces get no diagnostics (the
     * v1.216.0 payload law — gating only the binary is not a gate); and
     * no config means no server, because a stylesheet without stylelint
     * hasn't opted into stylelint's opinions.
     */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/css", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/scss", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/less", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/x-scss", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/x-less", service = LanguageServerProvider.class)
    })
    public static final class StylelintServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            File dir = projectDir(lookup);
            // no project, no server (v1.234.0 review): stylelint resolves
            // its config by walking up from the linted FILE, and the
            // config is executable JS — see EslintServer for the full
            // reasoning; this class replicated its shape and its hole.
            if (dir == null) {
                return null;
            }
            if (!org.nmox.studio.rack.service.WorkspaceTrust.isTrusted(dir)) {
                return null;
            }
            if (!hasStylelintConfig(dir)) {
                return null;
            }
            return launchNpm(lookup, "stylelint-lsp", "--stdio");
        }
    }

    /** Any of stylelint's config spellings. */
    static boolean hasStylelintConfig(File dir) {
        for (String name : new String[]{
            ".stylelintrc", ".stylelintrc.json", ".stylelintrc.yml",
            ".stylelintrc.yaml", ".stylelintrc.js", ".stylelintrc.cjs",
            ".stylelintrc.mjs", "stylelint.config.js",
            "stylelint.config.cjs", "stylelint.config.mjs"}) {
            if (new File(dir, name).isFile()) {
                return true;
            }
        }
        // package.json can carry a "stylelint" options key; the raw-scan
        // tradeoff is the same as hasEslintConfig's — a false positive
        // (e.g. a stylelint devDependency) starts a server that idles
        File pkg = new File(dir, "package.json");
        try {
            return pkg.isFile() && java.nio.file.Files.readString(pkg.toPath())
                    .contains("\"stylelint\"");
        } catch (IOException ex) {
            return false;
        }
    }

    /** Any of eslint's config spellings, current (flat) or legacy. */
    static boolean hasEslintConfig(File dir) {
        for (String name : new String[]{
            "eslint.config.js", "eslint.config.mjs", "eslint.config.cjs",
            "eslint.config.ts", ".eslintrc.js", ".eslintrc.cjs",
            ".eslintrc.json", ".eslintrc.yml", ".eslintrc.yaml", ".eslintrc"}) {
            if (new File(dir, name).isFile()) {
                return true;
            }
        }
        // package.json can carry an "eslintConfig" object; a raw scan is
        // enough here — a false positive just starts a server that then
        // idles, while parsing JSON on every file-open would cost more.
        File pkg = new File(dir, "package.json");
        try {
            return pkg.isFile() && java.nio.file.Files.readString(pkg.toPath())
                    .contains("\"eslintConfig\"");
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * The Angular Language Service — template intelligence, not just
     * TypeScript intelligence.
     *
     * <p>This is the one that makes an Angular IDE an Angular IDE:
     * completion for component members inside a binding, type checking
     * of the TEMPLATE against the component class, and go-to-definition
     * from <code>{{ user.name }}</code> to the property that backs it.
     * Without it the IDE sees an Angular template as anonymous HTML.
     *
     * <h2>Why this can't use {@link #launchNpm}</h2>
     * {@code ngserver} is unlike every other server here: it refuses to
     * start at all without being told where to find TypeScript and the
     * Angular compiler. Run bare it does not even print {@code --help} —
     * it throws {@code Failed to resolve 'typescript/lib/tsserverlibrary'
     * ... from []}. So the probe locations are mandatory, and they must
     * point at the PROJECT's own {@code node_modules}: an Angular
     * workspace pins its own Angular and TypeScript versions, and the
     * language service must match the compiler the project builds with.
     *
     * <h2>The TypeScript 7 trap (verified against the real binary)</h2>
     * {@code @angular/language-server} needs
     * {@code typescript/lib/tsserverlibrary.js}. TypeScript 7 — the
     * native rewrite, and what a bare {@code npm install typescript}
     * installs today — no longer ships that file, so the server cannot
     * start against it. TypeScript 5.9 works. Rather than let the client
     * crash-loop an unstartable process on every file open, we check for
     * the file and decline with an honest catalog message.
     *
     * <p>Registered on {@code text/typescript} only, and only actually
     * started inside an Angular workspace ({@code angular.json}) — every
     * other TypeScript project would otherwise pay for a server it has
     * no use for. It sits ALONGSIDE typescript-language-server and
     * eslint on that mime (the platform's {@code lookupAll} collection),
     * so this adds template intelligence rather than replacing anything.
     */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/typescript", service = LanguageServerProvider.class),
        // v1.217.0: templates too — ngserver's whole point is the HTML
        // half. The dedicated template mime keeps this off every plain
        // HTML file; the same trust gate covers the same payload.
        @MimeRegistration(mimeType = "text/x-ng-template", service = LanguageServerProvider.class)
    })
    public static final class AngularServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            // Angular's src/index.html is a STATIC-kind manifest, so the
            // file's OWNER project is often src/ rather than the
            // workspace (the v1.223.0 class, found again here by probe:
            // dir=.../ngdemo/src declined on src/angular.json) — locate
            // the real root by walking up for angular.json itself
            File dir = angularRootAbove(projectDir(lookup));
            ngProbe("startServer angular root=" + dir);
            if (dir == null) {
                ngProbe("decline: no angular.json above the owner project");
                return null; // not an Angular workspace: nothing to do, quietly
            }
            // v1.216.0 (arc review): the probe locations make ngserver
            // require() the project's OWN typescript and @angular packages
            // — repo-committed JavaScript executed on file-open, the
            // v1.102.0 RCE class with the payload one level down from the
            // binary. Same silent gate as launchNpm; there is no safe
            // global fallback because the probe dirs ARE the point.
            if (!org.nmox.studio.rack.service.WorkspaceTrust.isTrusted(dir)) {
                ngProbe("decline: workspace not trusted");
                return null;
            }
            File modules = angularProbeDir(dir);
            if (modules == null) {
                ngProbe("decline: no probe dir (no typescript install)");
                return null; // no install yet — npm install first, then reopen
            }
            if (!new File(modules, "typescript/lib/tsserverlibrary.js").isFile()) {
                // TypeScript 7+ (or no TypeScript): ngserver would throw on
                // startup and the client would keep retrying. Say so once
                // through the same channel every missing server uses.
                return reported(null, "ngserver");
            }
            // The catalog installs @angular/language-server INTO the
            // project (-D — it must match the workspace's Angular), so the
            // binary usually lives in the probe dir's own .bin, never on
            // PATH. Resolve it there first; the trust gate above already
            // covers running it. Bare "ngserver" stays as the fallback for
            // a deliberate global install. (v1.216.0: without this, the
            // catalog's own documented install produced a server the IDE
            // could never find.)
            File local = new File(modules, ".bin/ngserver");
            String bin = local.canExecute() ? local.getAbsolutePath() : "ngserver";
            ngProbe("launching bin=" + bin);
            return reported(launch(lookup, List.of(bin, "--stdio",
                    "--tsProbeLocations", modules.getAbsolutePath(),
                    "--ngProbeLocations", modules.getAbsolutePath())), "ngserver");
        }
    }

    /**
     * The {@code node_modules} an Angular workspace's language service
     * should probe: the Angular project's own, which in a monorepo is
     * the Node subproject's rather than the repo root's.
     *
     * <p>Chosen by probing for the FILE the service must load, not for a
     * bare directory (v1.216.0): npm/yarn workspaces hoist — the nested
     * {@code node_modules} exists but holds only {@code .bin} links while
     * typescript lives at the root. A directory test picked the empty
     * nested dir and declined a perfectly good install.
     */
    static File angularProbeDir(File projectDir) {
        File nested = new File(
                org.nmox.studio.rack.devices.ProjectInspector.kindDir(projectDir,
                        org.nmox.studio.rack.devices.ProjectInspector.ProjectKind.NODE),
                "node_modules");
        File root = new File(projectDir, "node_modules");
        for (File candidate : new File[]{nested, root}) {
            if (new File(candidate, "typescript/lib/tsserverlibrary.js").isFile()) {
                return candidate;
            }
        }
        // Neither carries a usable TypeScript: return an existing dir so
        // the TS-7 check upstream reports the honest catalog message, or
        // null when there is no install at all.
        if (nested.isDirectory()) {
            return nested;
        }
        return root.isDirectory() ? root : null;
    }

    /** Python via pyright. */
    @MimeRegistration(mimeType = "text/x-python", service = LanguageServerProvider.class)
    public static final class PythonServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("pyright-langserver", "--stdio"));
        }
    }

    /** Go via gopls. */
    @MimeRegistration(mimeType = "text/x-go", service = LanguageServerProvider.class)
    public static final class GoServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("gopls"));
        }
    }

    /** Rust via rust-analyzer. */
    @MimeRegistration(mimeType = "text/x-rust", service = LanguageServerProvider.class)
    public static final class RustServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("rust-analyzer"));
        }
    }

    /** Elixir via elixir-ls (brew wrapper or language_server.sh on PATH). */
    @MimeRegistration(mimeType = "text/x-elixir", service = LanguageServerProvider.class)
    public static final class ElixirServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return reported(launchFirst(lookup,
                    List.of("elixir-ls"),
                    List.of("language_server.sh")), "elixir-ls");
        }
    }

    /** C and C++ via clangd (ships with Xcode CLT and every LLVM install). */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/x-c", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/x-cpp", service = LanguageServerProvider.class)
    })
    public static final class ClangdServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("clangd", "--background-index"));
        }
    }

    /** Java via Eclipse JDT Language Server. */
    @MimeRegistration(mimeType = "text/x-java", service = LanguageServerProvider.class)
    public static final class JavaServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("jdtls"));
        }
    }

    /** C# via csharp-ls, falling back to OmniSharp. */
    @MimeRegistration(mimeType = "text/x-csharp", service = LanguageServerProvider.class)
    public static final class CSharpServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return reported(launchFirst(lookup,
                    List.of("csharp-ls"),
                    List.of("OmniSharp", "-lsp")), "csharp-ls");
        }
    }

    /** F# via fsautocomplete (dotnet tool install -g fsautocomplete). */
    @MimeRegistration(mimeType = "text/x-fsharp", service = LanguageServerProvider.class)
    public static final class FSharpServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("fsautocomplete"));
        }
    }

    /** PHP via intelephense (project-local first), falling back to phpactor. */
    @MimeRegistration(mimeType = "text/x-php5", service = LanguageServerProvider.class)
    public static final class PhpServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            LanguageServerDescription server = launchNpm(lookup, "intelephense", "--stdio");
            return server != null ? server
                    : launch(lookup, List.of("phpactor", "language-server"));
        }
    }

    /** Ruby via ruby-lsp, falling back to solargraph. */
    @MimeRegistration(mimeType = "text/x-ruby", service = LanguageServerProvider.class)
    public static final class RubyServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return reported(launchFirst(lookup,
                    List.of("ruby-lsp"),
                    List.of("solargraph", "stdio")), "ruby-lsp");
        }
    }

    /** Dart via the SDK's built-in language server. */
    @MimeRegistration(mimeType = "text/x-dart", service = LanguageServerProvider.class)
    public static final class DartServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("dart", "language-server", "--protocol=lsp"));
        }
    }

    /** Scala via Metals (coursier install metals). */
    @MimeRegistration(mimeType = "text/x-scala", service = LanguageServerProvider.class)
    public static final class ScalaServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("metals"));
        }
    }

    /** Kotlin via kotlin-language-server. */
    @MimeRegistration(mimeType = "text/x-kotlin", service = LanguageServerProvider.class)
    public static final class KotlinServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("kotlin-language-server"));
        }
    }

    /** Swift via sourcekit-lsp (ships with the Swift toolchain and Xcode). */
    @MimeRegistration(mimeType = "text/x-swift", service = LanguageServerProvider.class)
    public static final class SwiftServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("sourcekit-lsp"));
        }
    }

    /** Haskell via haskell-language-server (ghcup install hls). */
    @MimeRegistration(mimeType = "text/x-haskell", service = LanguageServerProvider.class)
    public static final class HaskellServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("haskell-language-server-wrapper", "--lsp"));
        }
    }

    /** Zig via zls. */
    @MimeRegistration(mimeType = "text/x-zig", service = LanguageServerProvider.class)
    public static final class ZigServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("zls"));
        }
    }

    /** Gleam via the compiler's built-in language server. */
    @MimeRegistration(mimeType = "text/x-gleam", service = LanguageServerProvider.class)
    public static final class GleamServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("gleam", "lsp"));
        }
    }

    /** Nim via nimlangserver (the official language server). */
    @MimeRegistration(mimeType = "text/x-nim", service = LanguageServerProvider.class)
    public static final class NimServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("nimlangserver"));
        }
    }

    /** D via serve-d. */
    @MimeRegistration(mimeType = "text/x-d", service = LanguageServerProvider.class)
    public static final class DServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("serve-d"));
        }
    }

    /** Elm via elm-language-server (npm: @elm-tooling/elm-language-server). */
    @MimeRegistration(mimeType = "text/x-elm", service = LanguageServerProvider.class)
    public static final class ElmServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "elm-language-server", "--stdio");
        }
    }

    /** ReScript via the editor-support server shipped in rescript-vscode. */
    @MimeRegistration(mimeType = "text/x-rescript", service = LanguageServerProvider.class)
    public static final class ReScriptServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "rescript-language-server", "--stdio");
        }
    }

    /** PureScript via purescript-language-server. */
    @MimeRegistration(mimeType = "text/x-purescript", service = LanguageServerProvider.class)
    public static final class PureScriptServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "purescript-language-server", "--stdio");
        }
    }

    /** Racket via racket-langserver (launched through racket -l). */
    @MimeRegistration(mimeType = "text/x-racket", service = LanguageServerProvider.class)
    public static final class RacketServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("racket", "-l", "racket-langserver"));
        }
    }

    /** V via v-analyzer (the official V language server). */
    @MimeRegistration(mimeType = "text/x-vlang", service = LanguageServerProvider.class)
    public static final class VServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("v-analyzer"));
        }
    }

    /** Fortran via fortls (the fortran-language-server). */
    @MimeRegistration(mimeType = "text/x-fortran", service = LanguageServerProvider.class)
    public static final class FortranServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("fortls"));
        }
    }

    /** Ada via the AdaCore ada_language_server (ships with GNAT/Alire). */
    @MimeRegistration(mimeType = "text/x-ada", service = LanguageServerProvider.class)
    public static final class AdaServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("ada_language_server"));
        }
    }

    /** Odin via ols. */
    @MimeRegistration(mimeType = "text/x-odin", service = LanguageServerProvider.class)
    public static final class OdinServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("ols"));
        }
    }

    /** Janet via janet-lsp (jpm install janet-lsp). */
    @MimeRegistration(mimeType = "text/x-janet", service = LanguageServerProvider.class)
    public static final class JanetServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("janet-lsp"));
        }
    }

    /** Erlang via erlang_ls. */
    @MimeRegistration(mimeType = "text/x-erlang", service = LanguageServerProvider.class)
    public static final class ErlangServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("erlang_ls", "--transport", "stdio"));
        }
    }

    /** Clojure via clojure-lsp. */
    @MimeRegistration(mimeType = "text/x-clojure", service = LanguageServerProvider.class)
    public static final class ClojureServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("clojure-lsp"));
        }
    }

    /** Common Lisp via cl-lsp, when someone has gone to the trouble. */
    @MimeRegistration(mimeType = "text/x-lisp", service = LanguageServerProvider.class)
    public static final class LispServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("cl-lsp"));
        }
    }

    /** Lua via lua-language-server. */
    @MimeRegistration(mimeType = "text/x-lua", service = LanguageServerProvider.class)
    public static final class LuaServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("lua-language-server"));
        }
    }

    /** OCaml via ocamllsp (opam install ocaml-lsp-server). */
    @MimeRegistration(mimeType = "text/x-ocaml", service = LanguageServerProvider.class)
    public static final class OCamlServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("ocamllsp"));
        }
    }

    /** Crystal via crystalline. */
    @MimeRegistration(mimeType = "text/x-crystal", service = LanguageServerProvider.class)
    public static final class CrystalServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("crystalline"));
        }
    }

    /** Julia via LanguageServer.jl, falling back fast if it isn't installed. */
    @MimeRegistration(mimeType = "text/x-julia", service = LanguageServerProvider.class)
    public static final class JuliaServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("julia", "--startup-file=no", "--history-file=no",
                    "-e", "using LanguageServer; runserver()"));
        }
    }

    /** R via the languageserver package. */
    @MimeRegistration(mimeType = "text/x-r", service = LanguageServerProvider.class)
    public static final class RServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("R", "--no-echo", "-e", "languageserver::run()"));
        }
    }

    /** Perl via PLS, falling back to Perl::LanguageServer. */
    @MimeRegistration(mimeType = "text/x-perl", service = LanguageServerProvider.class)
    public static final class PerlServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return reported(launchFirst(lookup,
                    List.of("pls"),
                    List.of("perl", "-MPerl::LanguageServer", "-e", "Perl::LanguageServer::run")), "pls");
        }
    }

    /** Groovy via groovy-language-server, when installed. */
    @MimeRegistration(mimeType = "text/x-groovy", service = LanguageServerProvider.class)
    public static final class GroovyServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("groovy-language-server"));
        }
    }

    /** Shell scripts via bash-language-server. */
    @MimeRegistration(mimeType = "text/sh", service = LanguageServerProvider.class)
    public static final class ShellServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "bash-language-server", "start");
        }
    }

    /** JSON via vscode-json-language-server (vscode-langservers-extracted). */
    @MimeRegistration(mimeType = "text/x-json", service = LanguageServerProvider.class)
    public static final class JsonServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "vscode-json-language-server", "--stdio");
        }
    }

    /** HTML via vscode-html-language-server (vscode-langservers-extracted). */
    @MimeRegistration(mimeType = "text/html", service = LanguageServerProvider.class)
    public static final class HtmlServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "vscode-html-language-server", "--stdio");
        }
    }

    /** CSS/SCSS/Less via vscode-css-language-server (vscode-langservers-extracted). */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/css", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/x-scss", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/x-less", service = LanguageServerProvider.class)
    })
    public static final class CssServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "vscode-css-language-server", "--stdio");
        }
    }

    // ---- the config layer -------------------------------------------------

    /** YAML via yaml-language-server: CI files, compose, k8s manifests. */
    @MimeRegistration(mimeType = "text/x-yaml", service = LanguageServerProvider.class)
    public static final class YamlServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "yaml-language-server", "--stdio");
        }
    }

    /** TOML via taplo (cargo/brew install taplo-cli). */
    @MimeRegistration(mimeType = "text/x-toml", service = LanguageServerProvider.class)
    public static final class TomlServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("taplo", "lsp", "stdio"));
        }
    }

    /** Dockerfile via dockerfile-language-server-nodejs. */
    @MimeRegistration(mimeType = "text/x-dockerfile", service = LanguageServerProvider.class)
    public static final class DockerfileServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "docker-langserver", "--stdio");
        }
    }

    /** GraphQL via graphql-language-service-cli. */
    @MimeRegistration(mimeType = "text/x-graphql", service = LanguageServerProvider.class)
    public static final class GraphqlServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "graphql-lsp", "server", "-m", "stream");
        }
    }

    /** Vue single-file components via the official language server. */
    @MimeRegistration(mimeType = "text/x-vue", service = LanguageServerProvider.class)
    public static final class VueServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "vue-language-server", "--stdio");
        }
    }

    /** Svelte components via svelte-language-server. */
    @MimeRegistration(mimeType = "text/x-svelte", service = LanguageServerProvider.class)
    public static final class SvelteServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "svelteserver", "--stdio");
        }
    }

    /** Astro components via @astrojs/language-server. */
    @MimeRegistration(mimeType = "text/x-astro", service = LanguageServerProvider.class)
    public static final class AstroServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "astro-ls", "--stdio");
        }
    }

    /** Prisma schemas via @prisma/language-server. */
    @MimeRegistration(mimeType = "text/x-prisma", service = LanguageServerProvider.class)
    public static final class PrismaServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "prisma-language-server", "--stdio");
        }
    }

    /** Solidity via @nomicfoundation/solidity-language-server. */
    @MimeRegistration(mimeType = "text/x-solidity", service = LanguageServerProvider.class)
    public static final class SolidityServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "nomicfoundation-solidity-language-server", "--stdio");
        }
    }

}
