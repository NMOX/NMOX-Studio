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
            // a bare unresolved name that doesn't exist anywhere: refuse quietly
            if (resolved.get(0).equals(command.get(0)) && !onPath(command.get(0))) {
                return null;
            }
            ProcessBuilder pb = new ProcessBuilder(resolved);
            if (dir != null) {
                pb.directory(dir);
            }
            pb.environment().put("PATH", ToolLocator.augmentedPath());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            return LanguageServerProvider.LanguageServerDescription.create(
                    process.getInputStream(), process.getOutputStream(), process);
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
        List<String> cmd = new ArrayList<>();
        cmd.add(local != null && local.canExecute() ? local.getAbsolutePath() : bin);
        cmd.addAll(List.of(args));
        // report the package name, not the resolved node_modules path
        return reported(launch(lookup, cmd), bin);
    }

    private static boolean onPath(String name) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (new File(dir, name).canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static File projectDir(Lookup lookup) {
        Project project = lookup.lookup(Project.class);
        return project == null ? null : FileUtil.toFile(project.getProjectDirectory());
    }

    /** TypeScript + JavaScript via typescript-language-server. */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/typescript", service = LanguageServerProvider.class),
        @MimeRegistration(mimeType = "text/javascript", service = LanguageServerProvider.class)
    })
    public static final class TypeScriptServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return launchNpm(lookup, "typescript-language-server", "--stdio");
        }
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

    /** Racket via racket-langserver (launched through racket -l). */
    @MimeRegistration(mimeType = "text/x-racket", service = LanguageServerProvider.class)
    public static final class RacketServer implements LanguageServerProvider {
        @Override
        public LanguageServerDescription startServer(Lookup lookup) {
            return provide(lookup, List.of("racket", "-l", "racket-langserver"));
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
