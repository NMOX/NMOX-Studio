package org.nmox.studio.rack.devices;

import java.io.File;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;

/**
 * Reads the project's package.json so devices can resolve their AUTO
 * positions to the tools the project actually uses: a project with a
 * "build" script gets `npm run build`, a vite project gets `npx vite`,
 * a jest project gets `npx jest`, and so on.
 */
public final class ProjectInspector {

    private ProjectInspector() {
    }

    /**
     * The toolchain a project belongs to, detected from its manifest.
     *
     * <p>Each constant declares three facts about itself, in this order:
     * {@code KIND(run target, test runner, manifests…)}. The two tokens
     * are the vocabulary IGNITION's TARGET knob and VERITAS's RUNNER knob
     * speak, and {@code null} means "this toolchain names no such verb" —
     * never a stand-in for another toolchain's. What a consumer DOES with
     * a null is the consumer's decision: IGNITION greys honestly, VERITAS
     * falls back to the project's own npm harness.
     *
     * <p>This used to be four hand-kept mirrors (a knob array, a
     * {@code ProjectKind -> String} switch and its inverse in RunDevice,
     * the same shape again in TestDevice) that agreed only by discipline,
     * and both inverses answered {@code NODE} by default — so a kind
     * nobody had decided about compiled, shipped, and ran a node command
     * under another toolchain's name. Adding a language is the most
     * repeated change in this repository; it costs one line here now.
     */
    public enum ProjectKind {
        BUN("bun", "bun", "bun.lock", "bun.lockb", "bunfig.toml"),
        DENO("deno", "deno", "deno.json", "deno.jsonc"),
        // CLARITY outranks NODE deliberately: every `clarinet new` scaffold
        // (and our Contract Kit's) carries a package.json whose only job is
        // the vitest/simnet test harness — NODE-first would shadow the
        // contract toolchain forever. The inverse of the ELM rule below,
        // for the inverse reason: there the app is the point, here the
        // contracts are. Its tests ARE that harness, so it names no runner.
        CLARITY("clarity", null, "Clarinet.toml"),
        // NODE names no runner of its own: which one a Node project uses is
        // read from its package.json (the "test" script, then the declared
        // framework), not from the kind.
        NODE("node", null, "package.json"),
        RUST("rust", "cargo", "Cargo.toml"),
        // a Foundry repo builds and tests contracts; there is nothing to RUN
        FOUNDRY(null, "forge", "foundry.toml"),
        GO("go", "go", "go.mod"),
        ELIXIR("elixir", "mix", "mix.exs"),
        ERLANG("erlang", "rebar3", "rebar.config"),
        GLEAM("gleam", "gleam", "gleam.toml"),
        CLOJURE("clojure", "clojure", "deps.edn", "project.clj"),
        SWIFT("swift", "swift", "Package.swift"),
        DOTNET("dotnet", "dotnet"), // *.csproj / *.fsproj / *.sln - glob-detected below
        DART("dart", "dart", "pubspec.yaml"),
        SCALA("scala", "sbt", "build.sbt"),
        HASKELL("haskell", "stack", "stack.yaml", "cabal.project"),
        ZIG("zig", "zig", "build.zig"),
        OCAML("ocaml", "dune", "dune-project"),
        CRYSTAL("crystal", "crystal", "shard.yml"),
        JULIA("julia", "julia", "Project.toml", "JuliaProject.toml"),
        NIM("nim", "nim"), // *.nimble - glob-detected below, like DOTNET
        DLANG("dlang", "dlang", "dub.json", "dub.sdl"),
        RACKET("racket", "racket", "info.rkt"),
        VLANG("vlang", "vlang", "v.mod"),
        CAIRO("cairo", "cairo", "Scarb.toml"),
        MOVE("move", "move", "Move.toml"),
        AIKEN("aiken", "aiken", "aiken.toml"),
        FORTRAN("fortran", "fortran", "fpm.toml"),
        ADA("ada", "ada", "alire.toml"),
        // the functional web: these almost always sit beside a package.json,
        // so NODE outranks them in detectKind — detectKinds still lists them
        // and explicit knob targets speak their toolchains
        ELM("elm", "elm", "elm.json"),
        RESCRIPT("rescript", "rescript", "rescript.json", "bsconfig.json"),
        PURESCRIPT("purescript", "purescript", "spago.yaml", "spago.dhall"),
        // Tact (TON) is npm-carried by design: the compiler is an npm dep
        // and the kit's build/test are package.json scripts, so NODE
        // rightly outranks — detectKinds still lists it and ROSETTA can
        // dial it explicitly. Its tests ride that npm harness.
        TACT("tact", null, "tact.config.json"),
        MAVEN("maven", "mvn", "pom.xml"),
        GRADLE("gradle", "gradle", "build.gradle", "build.gradle.kts"),
        PYTHON("python", "pytest", "pyproject.toml", "requirements.txt", "setup.py"),
        RUBY("ruby", "rspec", "Gemfile", "Rakefile"),
        PHP("php", "phpunit", "composer.json"),
        // both run through the Makefile; neither names a test verb of its own
        CMAKE("make", null, "CMakeLists.txt"),
        MAKE("make", null, "Makefile"),
        // ---- classic web (v1.34): every real toolchain manifest above
        // outranks these, and they outrank NONE. Their runnable artifact IS
        // their folder, so they all name the `static` serve target ----
        WEBPACK("webpack", null, "webpack.config.js", "webpack.config.cjs", "webpack.config.mjs"),
        GRUNT("static", null, "Gruntfile.js", "Gruntfile.coffee"),
        GULP("static", null, "gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs"),
        BOWER("static", null, "bower.json"),
        /**
         * The last resort, root-only, and only when nothing else matched:
         * a bare directory with an index.html is a project too — the
         * oldest stack on the web deserves to open like any other.
         */
        STATIC("static", null, "index.html", "index.htm"),
        /**
         * A learning space (v2.58.0): the catalog's marker file IS its
         * manifest, and the rack's pre-wired driver IS its toolchain. The
         * truest last resort — after STATIC, so a space with a root
         * index.html keeps serving and a space with package.json stays
         * NODE. The v2.58.0 walk found every run-kind space with no
         * known manifest (c, cobol, odin, pascal, haxe, perl, graphql,
         * and the new WIT space) DEAD on RUN: SOLDER refused
         * "NO PROJECT MANIFEST" because detectKind saw NONE.
         *
         * <p>It names neither verb: the space's own SOLDER driver is its
         * toolchain, and guessing `node index.js` for a COBOL space is
         * exactly the lie this vocabulary exists to stop telling.
         */
        LEARN(null, null, ".nmox-learn"),
        NONE(null, null);

        private final String runTarget;
        private final String testRunner;
        private final String[] manifests;

        /**
         * @param runTarget IGNITION's TARGET token for this toolchain, or
         *     null when it names no run verb
         * @param testRunner VERITAS's RUNNER token, or null when the kind
         *     names no runner of its own
         * @param manifests every file name this kind is detected by, most
         *     canonical first
         */
        ProjectKind(String runTarget, String testRunner, String... manifests) {
            this.runTarget = runTarget;
            this.testRunner = testRunner;
            this.manifests = manifests;
        }

        /**
         * IGNITION's TARGET token for this toolchain — {@code cargo run}'s
         * "rust", {@code go run .}'s "go" — or null when the toolchain has
         * no run verb at all (a Foundry repo, a learning space, an unaimed
         * rack). Null routes to the honest-grey path every console already
         * has; it must never be answered with another toolchain's token.
         */
        public String runTarget() {
            return runTarget;
        }

        /**
         * VERITAS's RUNNER token for this toolchain, or null when the kind
         * names no runner of its own — Node, Clarity and Tact projects run
         * the harness their own package.json declares, and the classic-web
         * kinds have no suite to run.
         */
        public String testRunner() {
            return testRunner;
        }

        /**
         * The kind that owns a TARGET token, or null when no kind names it.
         *
         * <p>Derived by walking {@link #values()}, so it cannot drift from
         * the declarations above. Two tokens are named by several kinds —
         * `static` (the classic-web trio plus STATIC) and `make` (CMAKE and
         * MAKE) — and the owner is the LAST declaration, which is the
         * lowest-precedence kind: those tokens name a generic fallback
         * command ("serve this folder", "run the Makefile") and the generic
         * kind is by construction the least specific, which is why
         * precedence order already puts it last.
         */
        public static ProjectKind forRunTarget(String target) {
            return owner(target, ProjectKind::runTarget);
        }

        /** The kind that owns a RUNNER token, or null when no kind names it. */
        public static ProjectKind forTestRunner(String runner) {
            return owner(runner, ProjectKind::testRunner);
        }

        private static ProjectKind owner(String token,
                java.util.function.Function<ProjectKind, String> fact) {
            if (token == null) {
                return null;
            }
            ProjectKind owner = null;
            for (ProjectKind kind : values()) {
                if (token.equals(fact.apply(kind))) {
                    owner = kind; // last declaration wins; see forRunTarget
                }
            }
            return owner;
        }

        public String manifest() {
            return manifests.length > 0 ? manifests[0] : "";
        }

        /**
         * EVERY marker this kind is detected by, not just the first.
         *
         * <p>Only {@link #manifest()} used to be reachable, so a consumer that
         * needed the whole set had to keep its own copy — and
         * {@code WebProjectFactory}, whose javadoc says "every manifest the
         * rack understands makes a real platform project", kept a hand-written
         * list of 60 that had drifted from this one. It was missing
         * {@code setup.py}, {@code Rakefile} and {@code bun.lockb}, so a
         * Python project carrying only a {@code setup.py} had working rack
         * lanes and no door: it could not be opened as a project at all. That
         * file already records the same class being found and fixed once, for
         * {@code CMakeLists.txt} and {@code Makefile} — *the lanes existed;
         * the door didn't*. It recurred because the fix was a longer list
         * rather than one home.
         */
        public String[] manifests() {
            return manifests.clone();
        }
    }

    /** Directories never scanned for nested project manifests — one home
     *  since ledger 110, plus {@code vendor} (a Go module's checked-in
     *  dependencies each carry a {@code go.mod}, and none of them is a
     *  project the user opened). The framework caches the merge added are
     *  a real fix rather than a tidy: Next.js standalone output writes
     *  {@code .next/standalone/package.json}, which this scan would have
     *  reported as a nested NODE project that nobody wrote. */
    private static final java.util.Set<String> SKIP_DIRS =
            org.nmox.studio.core.util.HeavyDirs.plus("vendor");
    private static final int MAX_CHILD_SCAN = 40;

    /**
     * Detects EVERY toolchain present, each mapped to the directory that
     * carries its manifest: the project root first, then one level of
     * subdirectories - the classic monorepo with frontend/package.json
     * and backend/Cargo.toml yields both, with their own directories.
     * Iteration order expresses precedence (Node first).
     */
    public static java.util.LinkedHashMap<ProjectKind, File> detectKinds(File projectDir) {
        java.util.LinkedHashMap<ProjectKind, File> found = new java.util.LinkedHashMap<>();
        for (ProjectKind kind : ProjectKind.values()) {
            if (kind == ProjectKind.NONE || kind == ProjectKind.STATIC
                    || kind == ProjectKind.LEARN) {
                continue; // the two last resorts are decided below, in order
            }
            File dir = manifestDirFor(projectDir, kind);
            if (dir != null) {
                found.put(kind, dir);
            }
        }
        // STATIC is the true last resort: a ROOT index.html, and only when
        // no real manifest matched anywhere — so a Vite app (root
        // index.html beside package.json) never grows a spurious kind.
        if (found.isEmpty() && hasManifestAt(projectDir, ProjectKind.STATIC)) {
            found.put(ProjectKind.STATIC, projectDir);
        }
        // LEARN is the resort after the last resort: a learning space whose
        // files carry no manifest at all is still a project — its marker
        // says so — and its SOLDER driver must be allowed to run
        if (found.isEmpty() && hasManifestAt(projectDir, ProjectKind.LEARN)) {
            found.put(ProjectKind.LEARN, projectDir);
        }
        return found;
    }

    /** True when one of the kind's manifests sits directly in this directory. */
    public static boolean hasManifestAt(File dir, ProjectKind kind) {
        for (String manifest : kind.manifests) {
            if (new File(dir, manifest).isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The directory carrying this kind's manifest — the root, then one
     * level of subdirectories — or null when the kind is absent.
     */
    public static File manifestDir(File projectDir, ProjectKind kind) {
        return manifestDirFor(projectDir, kind);
    }

    private static File manifestDirFor(File root, ProjectKind kind) {
        if (kind == ProjectKind.DOTNET) {
            return dotnetDir(root);
        }
        if (kind == ProjectKind.NIM) {
            return globDir(root, n -> n.endsWith(".nimble"));
        }
        if (kind == ProjectKind.STATIC) {
            // STATIC never walks subdirectories: it means "serve THIS folder",
            // not "some docs/ dir happens to hold an index.html"
            return hasManifestAt(root, kind) ? root : null;
        }
        for (String manifest : kind.manifests) {
            if (new File(root, manifest).isFile()) {
                return root;
            }
        }
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
        int scanned = 0;
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || SKIP_DIRS.contains(name) || ++scanned > MAX_CHILD_SCAN) {
                continue;
            }
            for (String manifest : kind.manifests) {
                if (new File(child, manifest).isFile()) {
                    return child;
                }
            }
        }
        return null;
    }

    /** .NET projects carry *.csproj/*.fsproj/*.sln - extension-detected. */
    private static File dotnetDir(File root) {
        return globDir(root, n -> n.endsWith(".csproj")
                || n.endsWith(".fsproj") || n.endsWith(".sln"));
    }

    /** Extension-detected manifests (dotnet, nim): root first, then one level down. */
    private static File globDir(File root, java.util.function.Predicate<String> manifest) {
        if (hasGlobManifest(root, manifest)) {
            return root;
        }
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
        int scanned = 0;
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || SKIP_DIRS.contains(name) || ++scanned > MAX_CHILD_SCAN) {
                continue;
            }
            if (hasGlobManifest(child, manifest)) {
                return child;
            }
        }
        return null;
    }

    private static boolean hasGlobManifest(File dir, java.util.function.Predicate<String> manifest) {
        // dotfiles excluded and plain files required: nimble's package
        // cache is a DIRECTORY named ~/.nimble, and without this filter
        // a rack aimed at $HOME detected NIM (v1.234.0 review — the
        // WebProjectFactory half of the same bug made $HOME a project)
        String[] names = dir.list((d, name) -> !name.startsWith(".")
                && manifest.test(name) && new File(d, name).isFile());
        return names != null && names.length > 0;
    }

    /**
     * The primary toolchain (highest-precedence detected kind). Mixed
     * projects can steer every AUTO knob with the ROSETTA selector
     * instead.
     */
    public static ProjectKind detectKind(File projectDir) {
        var kinds = detectKinds(projectDir);
        return kinds.isEmpty() ? ProjectKind.NONE : kinds.keySet().iterator().next();
    }

    /**
     * The directory commands for this kind should run in: where its
     * manifest lives, falling back to the project root.
     */
    public static File kindDir(File projectDir, ProjectKind kind) {
        File dir = kind == ProjectKind.NONE ? null : manifestDirFor(projectDir, kind);
        return dir != null ? dir : projectDir;
    }

    /** True when the directory carries any recognized project manifest. */
    public static boolean hasProjectManifest(File projectDir) {
        return detectKind(projectDir) != ProjectKind.NONE;
    }

    private static final java.util.Map<File, CacheEntry> packageJsonCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CacheEntry {
        final long lastModified;
        final JSONObject json;

        CacheEntry(long lastModified, JSONObject json) {
            this.lastModified = lastModified;
            this.json = json;
        }
    }

    /**
     * True when the project (or its Node lane) opts into Biome — the
     * one-toolchain lint+format successor to eslint+prettier. PURITY and
     * GLOSS AUTO must run the project's own toolchain, not ours.
     */
    public static boolean hasBiome(File projectDir) {
        File dir = kindDir(projectDir, ProjectKind.NODE);
        return new File(dir, "biome.json").isFile()
                || new File(dir, "biome.jsonc").isFile();
    }

    /**
     * True when the project is a Deno workspace — deno ships its own
     * lint, fmt, test, and language server, so every AUTO lane must
     * speak {@code deno <verb>} instead of reaching for npx-resolved
     * Node tooling that may not even have a node_modules to live in.
     */
    /**
     * True when the project opts into golangci-lint — the Go
     * community's aggregate linter. Config spellings per its docs.
     */
    public static boolean hasGolangci(File projectDir) {
        File dir = kindDir(projectDir, ProjectKind.GO);
        for (String name : new String[]{".golangci.yml", ".golangci.yaml",
                ".golangci.toml", ".golangci.json"}) {
            if (new File(dir, name).isFile()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasDeno(File projectDir) {
        File dir = kindDir(projectDir, ProjectKind.DENO);
        return new File(dir, "deno.json").isFile()
                || new File(dir, "deno.jsonc").isFile();
    }

    /**
     * The Node package manager this project actually uses. The corepack
     * {@code "packageManager"} pin in package.json wins (it is the
     * project's explicit contract), then the lockfile on disk, then npm.
     * AUTO lanes must never run the wrong manager: npm in a pnpm/yarn
     * repo writes a second lockfile and a broken node_modules.
     */
    public static String nodePackageManager(File projectDir) {
        JSONObject json = read(projectDir);
        if (json != null) {
            String pin = json.optString("packageManager", "");
            int at = pin.indexOf('@');
            String name = at > 0 ? pin.substring(0, at) : pin;
            switch (name) {
                case "npm": case "yarn": case "pnpm":
                    return name;
                default:
                    // unknown or absent pin: fall through to the lockfile
            }
        }
        File dir = kindDir(projectDir, ProjectKind.NODE);
        if (new File(dir, "pnpm-lock.yaml").isFile()) {
            return "pnpm";
        }
        if (new File(dir, "yarn.lock").isFile()) {
            return "yarn";
        }
        return "npm";
    }

    private static JSONObject read(File projectDir) {
        File pkg = new File(kindDir(projectDir, ProjectKind.NODE), "package.json");
        if (!pkg.isFile()) {
            return null;
        }
        long currentMod = pkg.lastModified();
        CacheEntry entry = packageJsonCache.get(pkg);
        if (entry != null && entry.lastModified == currentMod) {
            return entry.json;
        }
        try {
            // every toolchain answer this class gives starts here, and it
            // runs on AIM: the manifest is whatever the clone brought
            JSONObject json = new JSONObject(BoundedReads.read(pkg.toPath()));
            packageJsonCache.put(pkg, new CacheEntry(currentMod, json));
            return json;
        } catch (Exception ex) {
            return null;
        }
    }

    /** True if package.json declares the named script. */
    public static boolean hasScript(File projectDir, String name) {
        JSONObject json = read(projectDir);
        return json != null && json.optJSONObject("scripts") != null
                && json.getJSONObject("scripts").has(name);
    }

    /**
     * The scripts declared in package.json (name → command line), empty
     * when there is no readable package.json or no scripts block. The
     * ONE package.json script parse rack devices share (NPM-9000's knob;
     * tools/project keep their own readers — out of this module).
     */
    public static java.util.Map<String, String> scripts(File projectDir) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        JSONObject json = read(projectDir);
        JSONObject scripts = json == null ? null : json.optJSONObject("scripts");
        if (scripts != null) {
            for (String key : scripts.keySet()) {
                result.put(key, scripts.optString(key, ""));
            }
        }
        return result;
    }

    /**
     * Counts of [dependencies, devDependencies] declared in package.json,
     * or null when there is no readable package.json.
     */
    public static int[] dependencyCounts(File projectDir) {
        JSONObject json = read(projectDir);
        if (json == null) {
            return null;
        }
        JSONObject deps = json.optJSONObject("dependencies");
        JSONObject devDeps = json.optJSONObject("devDependencies");
        return new int[]{
            deps == null ? 0 : deps.length(),
            devDeps == null ? 0 : devDeps.length()
        };
    }

    /** True when the project (or its Node subproject) is an Angular workspace. */
    public static boolean hasAngular(File projectDir) {
        return new File(projectDir, "angular.json").isFile()
                || new File(kindDir(projectDir, ProjectKind.NODE), "angular.json").isFile();
    }

    /** Extracts a dependency version from mix.exs using the given pattern. */
    public static String mixDependencyVersion(File projectDir, java.util.regex.Pattern pattern) {
        File mixExs = new File(projectDir, "mix.exs");
        if (!mixExs.isFile()) {
            return null;
        }
        try {
            java.util.regex.Matcher m = pattern.matcher(BoundedReads.read(mixExs.toPath()));
            return m.find() ? m.group(1) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * The locked version of a package from composer.lock (searching
     * packages, then packages-dev), with composer's "v" prefix stripped:
     * "v11.9.2" -> "11.9.2". Null when there is no readable lock or the
     * package is not in it.
     */
    /** Cargo.toml text ceiling — a manifest is small; a crafted one stays cheap. */
    private static final int CARGO_SCAN_CAP = 256 * 1024;

    /**
     * True when this directory's Cargo project speaks Soroban: its
     * Cargo.toml mentions {@code soroban-sdk}, or (the {@code stellar
     * contract init} workspace layout) any one-level member's does.
     * Text scan, not a TOML parse — the crate name is distinctive.
     */
    public static boolean hasSorobanSdk(File dir) {
        if (cargoTomlMentions(new File(dir, "Cargo.toml"), "soroban-sdk")) {
            return true;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return false;
        }
        int scanned = 0;
        for (File child : children) {
            if (SKIP_DIRS.contains(child.getName()) || ++scanned > MAX_CHILD_SCAN) {
                break;
            }
            if (cargoTomlMentions(new File(child, "Cargo.toml"), "soroban-sdk")) {
                return true;
            }
            // stellar contract init nests members under contracts/<name>/
            File[] grandchildren = child.listFiles(File::isDirectory);
            if (grandchildren != null) {
                for (File grandchild : grandchildren) {
                    if (cargoTomlMentions(new File(grandchild, "Cargo.toml"), "soroban-sdk")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean cargoTomlMentions(File cargoToml, String needle) {
        if (!cargoToml.isFile()) {
            return false;
        }
        try {
            // this one always had its own ceiling; it goes through the shared
            // one so the class has a single way of reading a stranger's file
            return BoundedReads.read(cargoToml.toPath(), CARGO_SCAN_CAP).contains(needle);
        } catch (Exception unreadable) {
            return false;
        }
    }

    /** The two Move dialects: one Move.toml, two CLIs with different verbs. */
    public enum MoveDialect { SUI, APTOS }

    /**
     * Which Move dialect this project speaks. Sui-first (the documented
     * default since v1.137.0); APTOS only when Move.toml names the
     * AptosFramework dependency — {@code aptos move init} writes it as a
     * dotted table ({@code [dependencies.AptosFramework]}), hand-written
     * manifests use the inline form, and a plain text scan catches both.
     */
    public static MoveDialect moveDialect(File dir) {
        return cargoTomlMentions(new File(dir, "Move.toml"), "AptosFramework")
                ? MoveDialect.APTOS : MoveDialect.SUI;
    }

    /** The dialect's build command — Sui says {@code build}, Aptos says {@code compile}. */
    public static java.util.List<String> moveBuildCommand(File dir) {
        return moveDialect(dir) == MoveDialect.APTOS
                ? java.util.List.of("aptos", "move", "compile")
                : java.util.List.of("sui", "move", "build");
    }

    /** The dialect's test command — both CLIs agree on the verb. */
    public static java.util.List<String> moveTestCommand(File dir) {
        return moveDialect(dir) == MoveDialect.APTOS
                ? java.util.List.of("aptos", "move", "test")
                : java.util.List.of("sui", "move", "test");
    }

    /**
     * The locked version of a crate from Cargo.lock, or null. Cargo.lock
     * is INI-ish TOML: {@code [[package]]} blocks with {@code name = "x"}
     * then {@code version = "y"} on the next lines.
     */
    public static String cargoLockVersion(File dir, String crate) {
        File lock = new File(dir, "Cargo.lock");
        if (!lock.isFile()) {
            return null;
        }
        try {
            boolean inCrate = false;
            for (String line : BoundedReads.readLines(lock.toPath())) {
                String t = line.trim();
                if (t.startsWith("name = ")) {
                    inCrate = t.equals("name = \"" + crate + "\"");
                } else if (inCrate && t.startsWith("version = \"") && t.endsWith("\"")) {
                    return t.substring("version = \"".length(), t.length() - 1);
                }
            }
        } catch (Exception ex) {
            // unreadable or malformed lock: report unknown
        }
        return null;
    }

    public static String composerLockVersion(File dir, String packageName) {
        File lock = new File(dir, "composer.lock");
        if (!lock.isFile()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(BoundedReads.read(lock.toPath()));
            for (String section : new String[]{"packages", "packages-dev"}) {
                org.json.JSONArray packages = json.optJSONArray(section);
                if (packages == null) {
                    continue;
                }
                for (int i = 0; i < packages.length(); i++) {
                    JSONObject pkg = packages.optJSONObject(i);
                    if (pkg != null && packageName.equals(pkg.optString("name"))) {
                        String version = pkg.optString("version", "");
                        if (version.isEmpty()) {
                            return null;
                        }
                        return version.startsWith("v") ? version.substring(1) : version;
                    }
                }
            }
        } catch (Exception ex) {
            // unreadable or malformed lock: report unknown
        }
        return null;
    }

    /** The declared version constraint of a dependency, or null. */
    public static String dependencyVersion(File projectDir, String name) {
        JSONObject json = read(projectDir);
        if (json == null) {
            return null;
        }
        JSONObject deps = json.optJSONObject("dependencies");
        if (deps != null && deps.has(name)) {
            return deps.getString(name);
        }
        JSONObject devDeps = json.optJSONObject("devDependencies");
        if (devDeps != null && devDeps.has(name)) {
            return devDeps.getString(name);
        }
        return null;
    }

    /**
     * The first of the candidate packages found in dependencies or
     * devDependencies, or null. Order expresses preference.
     */
    public static String firstDependency(File projectDir, String... candidates) {
        JSONObject json = read(projectDir);
        if (json == null) {
            return null;
        }
        JSONObject deps = json.optJSONObject("dependencies");
        JSONObject devDeps = json.optJSONObject("devDependencies");
        for (String name : candidates) {
            if ((deps != null && deps.has(name)) || (devDeps != null && devDeps.has(name))) {
                return name;
            }
        }
        return null;
    }
}
