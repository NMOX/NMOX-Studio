package org.nmox.studio.rack.projectstudio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.core.process.ProcessSupport;

/**
 * The environment doctor: one honest answer to "what does this machine
 * actually have?" The IDE leans on dozens of external tools — the core
 * four every project touches, a toolchain per language lane, and an
 * interpreter per learning space — and each device already speaks up
 * when its own tool is missing. This sweeps them all at once: each
 * tool is probed with {@code --version} through the same hardened
 * launcher the devices use, so the verdict here is the verdict there.
 */
public final class EnvironmentDoctor {

    /** One probed tool: found (with its version line) or missing (with the fix). */
    public record Finding(String tool, String purpose, boolean found,
            String detail, String installHint) {
    }

    private EnvironmentDoctor() {
    }

    /** The full checkup list: core tools, toolchains, then REPL interpreters. */
    public static List<String[]> checklist() {
        // {binary, purpose, install hint} — hints favor Homebrew on the
        // assumption the doctor's OS hint column stays honest per-entry
        List<String[]> checks = new ArrayList<>(List.of(
                new String[]{"git", "version control — TIMELINE, project init", "brew install git"},
                new String[]{"node", "JavaScript runtime — most web tooling", "brew install node"},
                new String[]{"npm", "package manager — CRATE, NPM-9000", "ships with node"},
                new String[]{"pnpm", "Node package manager — AUTO lanes honor pnpm-lock", "brew install pnpm"},
                new String[]{"yarn", "Node package manager — AUTO lanes honor yarn.lock", "brew install yarn"},
                new String[]{"biome", "one-toolchain lint+format — PURITY/GLOSS auto lanes", "brew install biome"},
                new String[]{"cwebp", "WebP conversion — Image Kit (Web)", "brew install webp"},
                new String[]{"sass", "Sass → CSS — Compile to CSS in the SCSS editor", "npm i -g sass"},
                new String[]{"stylelint-lsp", "modern CSS lint diagnostics in the editor", "npm i -g stylelint-lsp stylelint"},
                new String[]{"docker", "containers — HARBOR, Docker panel", "Docker Desktop"},
                new String[]{"java", "JVM — Maven lanes, jshell space", "brew install openjdk"},
                new String[]{"mvn", "Maven builds", "brew install maven"},
                new String[]{"cargo", "Rust toolchain", "brew install rustup && rustup default stable"},
                // rustup ships a rust-analyzer PROXY that exists but dies
                // until the component is added — the v1.303.0 honesty row
                // ("found — but its version command failed") is the point
                new String[]{"rust-analyzer", "Rust language server", "rustup component add rust-analyzer"},
                new String[]{"golangci-lint", "Go aggregate linter", "brew install golangci-lint"},
                new String[]{"stellar", "Stellar/Soroban CLI (smart contracts)", "brew install stellar-cli"},
                new String[]{"solana", "Solana CLI (keys, airdrops, deploys)", "brew install solana"},
                new String[]{"anchor", "Anchor framework CLI (Solana programs)", "cargo install avm && avm install latest"},
                new String[]{"scarb", "Cairo/Starknet build tool (also serves the LSP)", "brew install scarb"},
                new String[]{"sui", "Sui CLI (Move smart contracts: build/test/localnet)", "brew install sui"},
                new String[]{"aptos", "Aptos CLI (Move smart contracts, Aptos dialect)", "brew install aptos"},
                new String[]{"clarinet", "Clarinet (Clarity/Stacks contracts: check/test/devnet)", "brew install clarinet"},
                new String[]{"aiken", "Aiken (Cardano validators: check/build)", "brew install aiken"},
                new String[]{"go", "Go toolchain", "brew install go"},
                new String[]{"python3", "Python — tooling and spaces", "brew install python"},
                new String[]{"ruby", "Ruby toolchain", "brew install ruby"},
                new String[]{"php", "PHP toolchain", "brew install php"},
                new String[]{"composer", "PHP package manager", "brew install composer"},
                new String[]{"mysql", "MySQL/MariaDB client", "brew install mysql-client"},
                new String[]{"nginx", "web server", "brew install nginx"},
                new String[]{"apachectl", "Apache HTTP server", "preinstalled on macOS / apt install apache2"},
                new String[]{"bun", "Bun runtime", "brew install oven-sh/bun/bun"},
                new String[]{"deno", "Deno runtime", "brew install deno"},
                new String[]{"mix", "Elixir/BEAM toolchain", "brew install elixir"},
                new String[]{"gleam", "Gleam — BEAM language + built-in LSP", "brew install gleam"},
                new String[]{"julia", "Julia — scientific computing, Pkg lanes", "brew install julia"},
                new String[]{"nim", "Nim — systemsy + expressive, nimble lanes", "brew install nim"},
                new String[]{"nimble", "Nim package manager — CRATE/VERITAS lanes", "ships with nim"},
                new String[]{"dub", "D package manager — dub run/build/test lanes", "brew install dub"},
                new String[]{"racket", "Racket — the language-oriented Lisp, raco lanes", "brew install minimal-racket"},
                new String[]{"elm", "Elm — no-runtime-exceptions web apps, reactor/make lanes", "brew install elm"},
                new String[]{"spago", "PureScript build tool — spago run/build/test lanes", "npm i -g spago"},
                new String[]{"purs", "PureScript compiler — powers spago + the REPL", "npm i -g purescript"},
                new String[]{"v", "V — fast-compiling, memory-safe; v run/./test lanes + vweb", "brew install vlang"},
                new String[]{"fpm", "Fortran Package Manager — fpm run/build/test lanes", "brew install fpm"},
                new String[]{"fortls", "Fortran language server — completion/hover in the editor", "pip install fortls"},
                new String[]{"v-analyzer", "V language server — completion/hover in the editor", "v install v-analyzer"},
                new String[]{"ada_language_server", "Ada language server — ships with GNAT/Alire toolchains", "alr toolchain --select"},
                new String[]{"svelteserver", "Svelte language server — completion/hover in .svelte files", "npm install -g svelte-language-server"},
                new String[]{"vscode-eslint-language-server", "ESLint language server — lint squiggles as you type (v1.213.0)", "npm install -g vscode-langservers-extracted"},
                new String[]{"ng", "Angular CLI — HALO's serve/build/test/generate lanes", "npm install -g @angular/cli"},
                new String[]{"gst", "GNU Smalltalk — the classic live-object language, REPL space", "brew install gnu-smalltalk"},
                new String[]{"swipl", "SWI-Prolog — logic programming, REPL space", "brew install swi-prolog"},
                new String[]{"tclsh", "Tcl — the embeddable scripting classic, REPL space", "ships with macOS / brew install tcl-tk"},
                new String[]{"guile", "GNU Guile — the GNU Scheme, REPL space", "brew install guile"},
                new String[]{"alr", "Alire — the Ada package manager, alr run/build lanes", "get alr from alire.ada.dev (no brew formula)"},
                new String[]{"odin", "Odin — data-oriented systems language, run space", "brew install odin"},
                new String[]{"instantfpc", "Free Pascal script runner — Pascal run space", "ships with fpc (brew install fpc)"},
                new String[]{"cobc", "GnuCOBOL compiler — COBOL run space", "brew install gnucobol"},
                new String[]{"haxe", "Haxe — one codebase, many targets; run space", "brew install haxe"},
                new String[]{"janet", "Janet — the embeddable Lisp, REPL space", "brew install janet"},
                new String[]{"wasm-tools", "wasm-tools (WebAssembly Component Model: validate/print .wit)", "brew install wasm-tools"},
                new String[]{"dotnet", ".NET SDK — C#/F# build/run/test", "brew install dotnet-sdk"},
                // v1.235.0: CMake became a first-class IDE lane in v1.233.0
                // (configure → build → ctest); the Doctor should say whether
                // the tool behind the buttons exists
                new String[]{"cmake", "CMake — configure/build lane + ctest", "brew install cmake"},
                new String[]{"dart", "Dart SDK", "brew install dart-sdk"},
                new String[]{"zig", "Zig toolchain", "brew install zig"},
                new String[]{"sbt", "Scala build tool", "brew install sbt"},
                new String[]{"stack", "Haskell Stack", "brew install haskell-stack"},
                new String[]{"dune", "OCaml build system", "brew install dune"},
                new String[]{"crystal", "Crystal toolchain", "brew install crystal"},
                new String[]{"swift", "Swift toolchain", "xcode-select --install"},
                new String[]{"gradle", "Gradle build tool", "brew install gradle"},
                new String[]{"forge", "Foundry — smart-contract build/test", "curl -L https://foundry.paradigm.xyz | bash"},
                new String[]{"anvil", "local EVM devnet — ANVIL device", "curl -L https://foundry.paradigm.xyz | bash"},
                new String[]{"cast", "Ethereum RPC/ABI multitool", "curl -L https://foundry.paradigm.xyz | bash"},
                new String[]{"chisel", "Solidity REPL — learning space", "curl -L https://foundry.paradigm.xyz | bash"},
                new String[]{"solc", "Solidity compiler", "brew install solidity"},
                new String[]{"slither", "static analysis for Solidity", "pip3 install slither-analyzer"},
                new String[]{"solhint", "Solidity linter — TYPEGUARD lane", "npm install -g solhint"},
                new String[]{"webpack", "classic bundler — FORGE webpack lane", "npm install -g webpack-cli"},
                new String[]{"grunt", "classic task runner — DYNAMO grunt lane", "npm install -g grunt-cli"},
                new String[]{"gulp", "classic task runner — DYNAMO gulp lane", "npm install -g gulp-cli"},
                new String[]{"bower", "classic web package manager — CRATE bower lane", "npm install -g bower"},
                new String[]{"coffee", "CoffeeScript compiler", "npm install -g coffeescript"}));
        // every distinct REPL interpreter the learning catalog can launch
        Map<String, String[]> repls = new LinkedHashMap<>();
        for (LearningCatalog.Space space : LearningCatalog.all()) {
            if (space.driver().kind() != LearningCatalog.DriverKind.REPL
                    || space.driver().command().isEmpty()) {
                continue;
            }
            String binary = space.driver().command().get(0);
            String hint = space.install().getOrDefault(LearningSpace.osKey(), "");
            repls.putIfAbsent(binary, new String[]{binary,
                "learning space: " + space.name(), hint});
        }
        for (String[] check : checks) {
            repls.remove(check[0]); // already covered above
        }
        checks.addAll(repls.values());
        return checks;
    }

    /**
     * Probes one tool by running {@code tool --version} through the
     * hardened launcher with a short leash. Missing binary → not found;
     * a tool that launches but dislikes --version still counts as found.
     */
    public static Finding probe(String tool, String purpose, String installHint) {
        return probeWith(tool, purpose, installHint, versionCommand(tool));
    }

    /**
     * Probes one user-authored drop-in probe (v1.305.0, ~/.nmox/doctor.d).
     * Rides the same launcher, leash, and honest {@link #detailFor} wording
     * as the built-ins; the purpose carries the family's provenance marker
     * so the table says whose row this is. The command is the validated
     * bare tool name plus its flag-shaped args — {@link UserProbes} refuses
     * anything else before this is ever called.
     */
    public static Finding probeCustom(UserProbes.Custom custom) {
        return probeWith(custom.tool(), custom.purpose() + " · yours",
                custom.install(), customCommand(custom.tool(), custom.args()));
    }

    /** The argv for a drop-in probe: the tool, then its declared args. */
    static List<String> customCommand(String tool, List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(tool);
        cmd.addAll(args);
        return cmd;
    }

    /** The version-query dialect for a tool: {@code --version} or its holdout form. */
    static List<String> versionCommand(String tool) {
        // most tools speak --version; the holdouts get their own dialect
        return switch (tool) {
            case "go" -> List.of("go", "version"); // rejects --version
            case "zig" -> List.of("zig", "version"); // rejects --version
            // nginx has no --version and prints `nginx -v` to STDERR —
            // runBounded captures stderr, so the version line is found either way
            case "nginx" -> List.of("nginx", "-v");
            // cwebp rejects --version with a usage dump; -version prints "1.6.0"
            // (the Doctor walk found the usage banner's first line — "Usage:" —
            // rendered as the tool's version, v1.303.0)
            case "cwebp" -> List.of("cwebp", "-version");
            case "apachectl" -> List.of("apachectl", "-v"); // --version unsupported
            // the v2.85.0 Doctor walk: three real tools read "found — but its
            // version command failed" because their dialect is not --version
            case "lua" -> List.of("lua", "-v"); // "unrecognized option '--version'"
            case "odin" -> List.of("odin", "version"); // --version prints the tool's usage
            case "instantfpc" -> List.of("instantfpc", "-h"); // --version: "Missing source file"; -h prints "instantfpc 1.3"
            default -> List.of(tool, "--version");
        };
    }

    /**
     * Runs one already-resolved version command under the 4s leash. Package
     * private so a test can inject a command that launches, prints, then holds
     * its pipe open — proving the timeout is real (the old hand-rolled probe
     * read to EOF before waitFor, so a wedged tool hung the whole sweep).
     */
    static Finding probeWith(String tool, String purpose, String installHint,
            List<String> versionCmd) {
        try {
            // runBounded drains both streams on their own threads while
            // waitFor runs FIRST — a tool that launches, prints nothing, and
            // holds its pipe open still hits the 4s leash instead of hanging
            // this whole sequential sweep. (The old hand-rolled probe read to
            // EOF before waitFor, so the timeout was unreachable.) The version
            // line lands on stdout for most tools and stderr for the holdouts
            // (nginx -v, apachectl -v), so we take the first non-blank of each.
            ProcessSupport.BoundedResult r =
                    ProcessSupport.runBounded(versionCmd, null, java.time.Duration.ofSeconds(4));
            String firstLine = firstNonBlankLine(r.stdout());
            if (firstLine.isBlank()) {
                firstLine = firstNonBlankLine(r.stderr());
            }
            return new Finding(tool, purpose, true,
                    detailFor(r.exitCode(), firstLine), installHint);
        } catch (IOException notFound) {
            // no such binary, or the sweep thread was interrupted mid-probe
            // (runBounded reasserts the interrupt flag before wrapping) — either
            // way this tool doesn't answer, so report it missing and move on
            return new Finding(tool, purpose, false, "not found", installHint);
        }
    }

    /**
     * The Status cell for a tool that LAUNCHED. The old rule echoed the
     * first output line unconditionally, which rendered failure text as
     * a version: cwebp's usage banner ("Usage:"), and — worse — a
     * crashing corepack pnpm shim's stack-trace path shown with a ✓
     * beside it (the v1.303.0 Doctor walk; the Doctor is exactly the
     * surface that should have SAID pnpm was broken). A nonzero exit
     * means the output is an error, not a version — say that instead
     * of quoting it.
     */
    static String detailFor(int exitCode, String firstLine) {
        // exitCode -1 is the TIMEOUT sentinel (BoundedResult contract): a
        // wedged tool that already printed its version keeps it — the
        // pre-existing wedged-pipe test pins that, and it caught this rule
        // over-reaching on its first run. Only a real nonzero exit means
        // the output is an error rather than a version.
        if (exitCode > 0) {
            return "found — but its version command failed (exit " + exitCode + ")";
        }
        if (firstLine.isBlank()) {
            return "found (version unknown)";
        }
        return firstLine.length() > 72 ? firstLine.substring(0, 72) + "…" : firstLine;
    }

    /** The first non-blank line of a captured stream, stripped, or "". */
    private static String firstNonBlankLine(String captured) {
        if (captured == null || captured.isBlank()) {
            return "";
        }
        for (String line : captured.split("\\R", -1)) {
            String stripped = line.strip();
            if (!stripped.isBlank()) {
                return stripped;
            }
        }
        return "";
    }
}
