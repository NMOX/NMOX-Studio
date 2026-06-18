package org.nmox.studio.editor.lsp;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nmox.studio.rack.engine.ToolLocator;

/**
 * What each language's intelligence needs and how to get it. The
 * platform's LSP client delivers hover, go-to-definition, rename and
 * live errors for free once a server launches — but every server is an
 * external binary the developer installs. This catalog turns a missing
 * binary into an answer ("install gopls: go install …") instead of
 * silence, which is the difference between "45 languages" as a
 * highlighting claim and as a working-intelligence one.
 */
public final class LanguageServerCatalog {

    /** A language's server: the binary that must be on PATH, and how to install it. */
    public record Server(String language, String binary, String install) {
    }

    // keyed by the binary name the providers actually launch
    private static final Map<String, Server> BY_BINARY = new LinkedHashMap<>();

    private static void add(String language, String binary, String install) {
        BY_BINARY.put(binary, new Server(language, binary, install));
    }

    static {
        add("TypeScript / JavaScript", "typescript-language-server",
                "npm install -g typescript-language-server typescript");
        add("Python", "pyright-langserver", "npm install -g pyright");
        add("Go", "gopls", "go install golang.org/x/tools/gopls@latest");
        add("Rust", "rust-analyzer", "rustup component add rust-analyzer");
        add("PHP", "intelephense", "npm install -g intelephense");
        add("Ruby", "ruby-lsp", "gem install ruby-lsp");
        add("C / C++", "clangd", "install LLVM (brew install llvm) or Xcode Command Line Tools");
        add("Java", "jdtls", "brew install jdtls");
        add("C#", "csharp-ls", "dotnet tool install -g csharp-ls");
        add("F#", "fsautocomplete", "dotnet tool install -g fsautocomplete");
        add("Elixir", "elixir-ls", "brew install elixir-ls");
        add("Scala", "metals", "coursier install metals");
        add("Kotlin", "kotlin-language-server", "brew install kotlin-language-server");
        add("Swift", "sourcekit-lsp", "ships with the Swift toolchain / Xcode");
        add("Haskell", "haskell-language-server-wrapper", "ghcup install hls");
        add("Zig", "zls", "install zls from github.com/zigtools/zls");
        add("Erlang", "erlang_ls", "install erlang_ls from github.com/erlang-ls/erlang_ls");
        add("Clojure", "clojure-lsp", "brew install clojure-lsp/brew/clojure-lsp-native");
        add("Dart", "dart", "install the Dart SDK");
        add("Lua", "lua-language-server", "brew install lua-language-server");
        add("OCaml", "ocamllsp", "opam install ocaml-lsp-server");
        add("Julia", "julia", "install Julia, then the LanguageServer.jl package");
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

    /** True if the binary resolves on the IDE's augmented PATH. */
    public static boolean isInstalled(String binary) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (new File(dir, binary).canExecute()) {
                return true;
            }
        }
        return false;
    }
}
