package org.nmox.studio.editor.lsp;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.core.process.ToolLocator;

/**
 * What each language's intelligence needs and how to get it. The
 * platform's LSP client delivers hover, go-to-definition, rename and
 * live errors for free once a server launches — but every server is an
 * external binary the developer installs, each in its ecosystem's own
 * idiom (npm, brew, go install, cargo, gem, dotnet tool, coursier,
 * opam). This catalog names the binary, the human install hint, and —
 * where there's a single clean command — the exact argv to run it.
 */
public final class LanguageServerCatalog {

    /**
     * A language's server: the binary that must be on PATH, the human
     * install hint, and the runnable install command ({@code command} is
     * empty when there's no single command — those stay manual).
     */
    public record Server(String language, String binary, String install, List<String> command) {

        /** True when this server can be installed by running one command. */
        public boolean autoInstallable() {
            return command != null && !command.isEmpty();
        }

        /** The package manager / toolchain the install command drives (npm, brew, go, …). */
        public String installer() {
            return autoInstallable() ? command.get(0) : "";
        }
    }

    private static final Map<String, Server> BY_BINARY = new LinkedHashMap<>();

    private static void add(String language, String binary, String hint, List<String> command) {
        BY_BINARY.put(binary, new Server(language, binary, hint, command));
    }

    static {
        add("TypeScript / JavaScript", "typescript-language-server",
                "npm install -g typescript-language-server typescript",
                List.of("npm", "install", "-g", "typescript-language-server", "typescript"));
        add("Python", "pyright-langserver", "npm install -g pyright",
                List.of("npm", "install", "-g", "pyright"));
        add("Go", "gopls", "go install golang.org/x/tools/gopls@latest",
                List.of("go", "install", "golang.org/x/tools/gopls@latest"));
        add("Rust", "rust-analyzer", "rustup component add rust-analyzer",
                List.of("rustup", "component", "add", "rust-analyzer"));
        add("PHP", "intelephense", "npm install -g intelephense",
                List.of("npm", "install", "-g", "intelephense"));
        add("Ruby", "ruby-lsp", "gem install ruby-lsp",
                List.of("gem", "install", "ruby-lsp"));
        add("C / C++", "clangd", "brew install llvm (or Xcode Command Line Tools)",
                List.of("brew", "install", "llvm"));
        add("Java", "jdtls", "brew install jdtls",
                List.of("brew", "install", "jdtls"));
        add("C#", "csharp-ls", "dotnet tool install -g csharp-ls",
                List.of("dotnet", "tool", "install", "-g", "csharp-ls"));
        add("F#", "fsautocomplete", "dotnet tool install -g fsautocomplete",
                List.of("dotnet", "tool", "install", "-g", "fsautocomplete"));
        add("Elixir", "elixir-ls", "brew install elixir-ls",
                List.of("brew", "install", "elixir-ls"));
        add("Scala", "metals", "coursier install metals",
                List.of("coursier", "install", "metals"));
        add("Kotlin", "kotlin-language-server", "brew install kotlin-language-server",
                List.of("brew", "install", "kotlin-language-server"));
        add("Swift", "sourcekit-lsp", "ships with the Swift toolchain / Xcode", List.of());
        add("Haskell", "haskell-language-server-wrapper", "ghcup install hls",
                List.of("ghcup", "install", "hls"));
        add("Zig", "zls", "install zls from github.com/zigtools/zls", List.of());
        add("Gleam", "gleam", "brew install gleam", List.of("brew", "install", "gleam"));
        add("Nim", "nimlangserver", "nimble install nimlangserver", List.of("nimble", "install", "nimlangserver"));
        add("D", "serve-d", "dub fetch serve-d (or brew install dub first)", List.of("dub", "fetch", "serve-d"));
        add("Racket", "racket", "raco pkg install racket-langserver", List.of("raco", "pkg", "install", "racket-langserver"));
        add("Erlang", "erlang_ls", "install erlang_ls from github.com/erlang-ls/erlang_ls", List.of());
        add("Clojure", "clojure-lsp", "brew install clojure-lsp/brew/clojure-lsp-native",
                List.of("brew", "install", "clojure-lsp/brew/clojure-lsp-native"));
        add("Dart", "dart", "install the Dart SDK", List.of());
        add("Lua", "lua-language-server", "brew install lua-language-server",
                List.of("brew", "install", "lua-language-server"));
        add("OCaml", "ocamllsp", "opam install ocaml-lsp-server",
                List.of("opam", "install", "ocaml-lsp-server"));
        add("Julia", "julia", "install Julia, then the LanguageServer.jl package", List.of());
        add("Solidity", "nomicfoundation-solidity-language-server",
                "npm install -g @nomicfoundation/solidity-language-server",
                List.of("npm", "install", "-g", "@nomicfoundation/solidity-language-server"));
    }

    private LanguageServerCatalog() {
    }

    /** The catalog entry for a launch binary, or null when uncatalogued. */
    public static Server forBinary(String binary) {
        return BY_BINARY.get(binary);
    }

    /** Every known server, in declaration order (web stack first). */
    public static java.util.Collection<Server> all() {
        return BY_BINARY.values();
    }

    /**
     * True if the binary resolves on the IDE's augmented PATH. Deliberately
     * NOT delegated to ToolLocator.resolve(): that caches misses for the JVM
     * lifetime, and this probe must see a server the user just installed
     * (the LSP health panel's install flow re-checks right after).
     */
    public static boolean isInstalled(String binary) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (foundIn(new File(dir), binary)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One PATH entry's verdict. On Windows nothing is executable under the
     * bare name — native servers ship {@code .exe}, npm shims ship
     * {@code .cmd} — so probe the same two suffixes ToolLocator resolves;
     * without them no language server was EVER detected on Windows.
     */
    static boolean foundIn(File dir, String binary) {
        return new File(dir, binary).canExecute()
                || new File(dir, binary + ".exe").isFile()
                || new File(dir, binary + ".cmd").isFile();
    }
}
