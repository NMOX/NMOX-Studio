package org.nmox.studio.rack.docker;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.rack.devices.ProjectInspector;

/**
 * Turns what the rack already knows about a project into production
 * container files: a multi-stage Dockerfile tuned to the detected
 * toolchain, a .dockerignore that keeps the context lean, and a
 * compose.yaml to run it. This is the "more power than docker gives
 * you" move - docker can build an image, but it cannot look at your
 * package.json and write the Dockerfile.
 *
 * <p>Two halves. {@link #inspect} reads the project (bounded reads, no
 * walk) into {@link Facts}; {@link #generate(ProjectInspector.ProjectKind,
 * String, Facts)} is deterministic and does no I/O. A recipe follows what
 * the facts say exists: project detection promises a manifest, never a
 * lockfile, an entry file or a binary named after the folder, and every
 * one of those was once assumed here (the proof fixtures found nine).
 * Where nothing can be detected the generated file SAYS so, in a comment
 * and in a start command that exits with the sentence.
 */
public final class DockerizeGenerator {

    // The base images are version literals no bot reads, so they are moved
    // by hand, each existence-checked on its registry, and held by
    // DockerizeGeneratorTest: every FROM and every compose image the
    // generator can write must carry a version. Node is the ACTIVE LTS
    // line; go and rust are the current stable minor; nginx is the current
    // STABLE branch (a retired mainline branch such as 1.29 stops receiving
    // image rebuilds the day the next stable is cut) and has ONE home here
    // for the static-site image and the PHP sidecar; debian and distroless
    // are the current stable release; deno and bun were measured 2026-11-13.
    static final String NODE_IMAGE = "node:26-alpine";
    static final String NGINX_IMAGE = "nginx:1.30-alpine";
    static final String GO_IMAGE = "golang:1.27";
    static final String DISTROLESS_IMAGE = "gcr.io/distroless/static-debian13";
    static final String RUST_IMAGE = "rust:1.100";
    static final String DEBIAN_IMAGE = "debian:trixie-slim";
    // python stays at 3.14 on purpose (measured 2026-11-13): 3.15.0 is
    // five weeks old, a -slim image has no compiler, and a requirements.txt
    // whose packages have not published 3.15 wheels yet fails at pip install.
    // The CI exporter's python-version follows this line (CiExportPinCurrencyTest).
    static final String PYTHON_IMAGE = "python:3.14-slim";
    static final String DENO_IMAGE = "denoland/deno:alpine-2.9.8";
    static final String BUN_IMAGE = "oven/bun:1.4-alpine";
    static final String COMPOSER_IMAGE = "composer:2";
    static final String PHP_IMAGE = "php:8.5-fpm-alpine";

    /** The compose file Dockerize writes. */
    public static final String COMPOSE = "compose.yaml";
    /**
     * The name it uses instead when the project already has a compose file
     * of its own: {@code compose.yaml} beside a {@code docker-compose.yml}
     * WINS (compose prefers it), so writing it would silently replace the
     * stack {@code docker compose up} starts.
     */
    public static final String SIDE_COMPOSE = "compose.nmox.yaml";
    /** Compose file names the generator never writes: one of these is somebody else's stack. */
    private static final String[] FOREIGN_COMPOSE = {"docker-compose.yml", "docker-compose.yaml", "compose.yml"};

    /** How a Python project's dependencies are installed. */
    public enum PyInstall {
        REQUIREMENTS, PROJECT, NONE
    }

    /** What starts a Python project, as far as its dependency text says. */
    public enum PyStart {
        UVICORN, GUNICORN, FLASK, DJANGO, UNKNOWN
    }

    /**
     * What a recipe needs to know that the project's kind does not say.
     * {@link #inspect} reads it from disk; {@link #assumed} is the answer
     * for "a project of this kind in its most ordinary shape", which is
     * what the three-argument {@code generate} uses.
     *
     * @param nodeStatic     the Node project builds a static bundle
     * @param nodeManager    npm, pnpm or yarn
     * @param nodeLocked     that manager's lockfile is in the build context
     * @param yarnBerry      yarn 2+ ({@code .yarnrc.yml}, or a pin of it)
     * @param yarnDir        a {@code .yarn} directory to copy before installing
     * @param staticDir      where the build leaves the bundle, under /app
     * @param staticNote     what that was read from, or null
     * @param rustBinary     the crate's binary name; "" means the image name
     * @param rustWorkspace  Cargo.toml is a workspace root with no package
     * @param pyInstall      how dependencies are installed
     * @param pyStart        what starts the app
     * @param pyModule       the module holding the app, or null when none was found
     * @param denoWorkspace  deno.json names workspace members
     * @param bunPackage     there is a package.json
     * @param bunLocked      there is a bun.lock or bun.lockb
     * @param bunScript      package.json has a start script
     * @param bunEntry       the entry file when there is no start script, or null
     * @param foreignCompose the project has a compose file Dockerize did not write
     */
    public record Facts(boolean nodeStatic, String nodeManager, boolean nodeLocked, boolean yarnBerry,
            boolean yarnDir, String staticDir, String staticNote, String rustBinary, boolean rustWorkspace,
            PyInstall pyInstall, PyStart pyStart, String pyModule, boolean denoWorkspace, boolean bunPackage,
            boolean bunLocked, boolean bunScript, String bunEntry, boolean foreignCompose) {

        /** The ordinary shape of a kind: nothing was read from disk. */
        public static Facts assumed(boolean nodeStatic) {
            return new Draft(nodeStatic).build();
        }
    }

    /** Facts while they are being read. */
    private static final class Draft {
        final boolean nodeStatic;
        String nodeManager = "npm";
        boolean nodeLocked = true;
        boolean yarnBerry;
        boolean yarnDir;
        String staticDir = "dist";
        String staticNote;
        String rustBinary = "";
        boolean rustWorkspace;
        PyInstall pyInstall = PyInstall.REQUIREMENTS;
        PyStart pyStart = PyStart.UVICORN;
        String pyModule = "main";
        boolean denoWorkspace;
        boolean bunPackage = true;
        boolean bunLocked = true;
        boolean bunScript = true;
        String bunEntry;
        boolean foreignCompose;

        Draft(boolean nodeStatic) {
            this.nodeStatic = nodeStatic;
        }

        Facts build() {
            return new Facts(nodeStatic, nodeManager, nodeLocked, yarnBerry, yarnDir, staticDir, staticNote,
                    rustBinary, rustWorkspace, pyInstall, pyStart, pyModule, denoWorkspace, bunPackage,
                    bunLocked, bunScript, bunEntry, foreignCompose);
        }
    }

    private DockerizeGenerator() {
    }

    // ------------------------------------------------------------ reading the project

    /**
     * Reads what the recipe for {@code kind} needs from the project's own
     * files. Every read is bounded and names one file at the project root:
     * no directory is walked. A file that cannot be read leaves the
     * ordinary assumption in place. Call it off the EDT.
     */
    public static Facts inspect(File dir, ProjectInspector.ProjectKind kind) {
        Draft d = new Draft(kind == ProjectInspector.ProjectKind.NODE && buildsStatic(dir));
        for (String name : FOREIGN_COMPOSE) {
            d.foreignCompose |= new File(dir, name).isFile();
        }
        switch (kind) {
            case NODE -> inspectNode(dir, d);
            case RUST -> inspectRust(dir, d);
            case PYTHON -> inspectPython(dir, d);
            case DENO -> d.denoWorkspace = mentions(text(dir, "deno.json") + text(dir, "deno.jsonc"), "workspace");
            case BUN -> inspectBun(dir, d);
            default -> {
                // go and php ask nothing of the project beyond its manifest
            }
        }
        return d.build();
    }

    private static void inspectNode(File dir, Draft d) {
        d.nodeManager = ProjectInspector.nodePackageManager(dir);
        String lock = switch (d.nodeManager) {
            case "pnpm" -> "pnpm-lock.yaml";
            case "yarn" -> "yarn.lock";
            default -> "package-lock.json";
        };
        d.nodeLocked = new File(dir, lock).isFile()
                || ("npm".equals(d.nodeManager) && new File(dir, "npm-shrinkwrap.json").isFile());
        if ("yarn".equals(d.nodeManager)) {
            JSONObject pkg = json(dir, "package.json");
            String pin = pkg == null ? "" : pkg.optString("packageManager", "");
            // "yarn@4.5.0": anything but a 0.x or 1.x pin is berry
            boolean pinnedBerry = pin.startsWith("yarn@") && !pin.startsWith("yarn@1.") && !pin.startsWith("yarn@0.");
            d.yarnBerry = pinnedBerry || new File(dir, ".yarnrc.yml").isFile();
            d.yarnDir = d.yarnBerry && new File(dir, ".yarn").isDirectory();
        }
        if (!d.nodeStatic) {
            return;
        }
        String tool = ProjectInspector.firstDependency(dir, "@angular/cli", "vite", "react-scripts");
        if ("react-scripts".equals(tool)) {
            d.staticDir = "build";
            d.staticNote = "react-scripts writes its bundle to build/";
        } else if ("@angular/cli".equals(tool)) {
            angularOutput(dir, d);
        }
    }

    /**
     * Where {@code ng build} leaves the browser bundle. The application
     * builder (Angular 17+) writes {@code <outputPath>/browser}; the older
     * browser builder writes {@code <outputPath>} itself; the default
     * outputPath is {@code dist/<project>}.
     */
    private static void angularOutput(File dir, Draft d) {
        JSONObject root = json(dir, "angular.json");
        JSONObject projects = root == null ? null : root.optJSONObject("projects");
        if (projects == null || projects.isEmpty()) {
            d.staticNote = "no readable angular.json: dist/ is a guess - set it to your outputPath";
            return;
        }
        String project = root.optString("defaultProject", "");
        if (!projects.has(project)) {
            // org.json keeps no file order: take the first application by name
            project = null;
            for (String name : new TreeSet<>(projects.keySet())) {
                JSONObject p = projects.optJSONObject(name);
                if (p != null && !"library".equals(p.optString("projectType"))) {
                    project = name;
                    break;
                }
            }
            if (project == null) {
                d.staticNote = "angular.json has no application project: dist/ is a guess";
                return;
            }
        }
        JSONObject p = projects.optJSONObject(project);
        JSONObject targets = p == null ? null : p.optJSONObject("architect");
        if (targets == null && p != null) {
            targets = p.optJSONObject("targets");
        }
        JSONObject build = targets == null ? null : targets.optJSONObject("build");
        String builder = build == null ? "" : build.optString("builder", "");
        JSONObject options = build == null ? null : build.optJSONObject("options");
        Object declared = options == null ? null : options.opt("outputPath");
        String base = "dist/" + project;
        String browser = builder.endsWith(":browser") || builder.endsWith(":browser-esbuild") ? "" : "browser";
        if (declared instanceof String s && !s.isBlank()) {
            base = s;
        } else if (declared instanceof JSONObject o) {
            base = o.optString("base", base);
            browser = o.has("browser") ? o.optString("browser", "") : browser;
        }
        String out = trimSlashes(base) + (browser.isEmpty() ? "" : "/" + trimSlashes(browser));
        if (!safePath(out) || !safeName(project)) {
            d.staticNote = "angular.json names an output path this file will not repeat: dist/ is a guess";
            return;
        }
        d.staticDir = out;
        d.staticNote = "angular.json, project \"" + project + "\": "
                + (declared == null ? "no outputPath, so the default" : "outputPath " + trimSlashes(base))
                + (browser.isEmpty() ? "" : ", and the application builder adds /" + trimSlashes(browser));
    }

    /** The binary cargo builds: the first {@code [[bin]]} name, else the package's. */
    private static void inspectRust(File dir, Draft d) {
        String section = "";
        String bin = null;
        String pkg = null;
        boolean workspace = false;
        for (String raw : text(dir, "Cargo.toml").split("\n")) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                section = line;
                workspace |= line.startsWith("[workspace");
                continue;
            }
            if (!line.startsWith("name") || line.indexOf('=') < 0) {
                continue;
            }
            String key = line.substring(0, line.indexOf('=')).strip();
            String value = line.substring(line.indexOf('=') + 1).strip();
            int hash = value.indexOf('#');
            value = (hash >= 0 ? value.substring(0, hash) : value).strip().replace("\"", "").replace("'", "");
            if (!"name".equals(key) || !safeName(value)) {
                continue;
            }
            if (section.startsWith("[[bin]]") && bin == null) {
                bin = value;
            } else if (section.startsWith("[package]") && pkg == null) {
                pkg = value;
            }
        }
        if (bin != null || pkg != null) {
            d.rustBinary = bin != null ? bin : pkg;
        } else {
            d.rustWorkspace = workspace;
        }
    }

    private static void inspectPython(File dir, Draft d) {
        boolean requirements = new File(dir, "requirements.txt").isFile();
        boolean project = new File(dir, "pyproject.toml").isFile() || new File(dir, "setup.py").isFile();
        d.pyInstall = requirements ? PyInstall.REQUIREMENTS : project ? PyInstall.PROJECT : PyInstall.NONE;
        String deps = (text(dir, "requirements.txt") + '\n' + text(dir, "pyproject.toml") + '\n'
                + text(dir, "setup.py")).toLowerCase(Locale.ROOT);
        if (mentions(deps, "uvicorn") || mentions(deps, "fastapi")) {
            d.pyStart = PyStart.UVICORN;
        } else if (mentions(deps, "gunicorn")) {
            d.pyStart = PyStart.GUNICORN;
        } else if (mentions(deps, "flask")) {
            d.pyStart = PyStart.FLASK;
        } else if (mentions(deps, "django") && new File(dir, "manage.py").isFile()) {
            d.pyStart = PyStart.DJANGO;
        } else {
            d.pyStart = PyStart.UNKNOWN;
        }
        d.pyModule = null;
        for (String module : new String[] {"main", "app", "wsgi", "asgi"}) {
            if (new File(dir, module + ".py").isFile()) {
                d.pyModule = module;
                break;
            }
        }
    }

    private static void inspectBun(File dir, Draft d) {
        JSONObject pkg = json(dir, "package.json");
        d.bunPackage = new File(dir, "package.json").isFile();
        d.bunLocked = new File(dir, "bun.lock").isFile() || new File(dir, "bun.lockb").isFile();
        JSONObject scripts = pkg == null ? null : pkg.optJSONObject("scripts");
        d.bunScript = scripts != null && scripts.has("start");
        d.bunEntry = null;
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (pkg != null) {
            candidates.add(pkg.optString("module", ""));
            candidates.add(pkg.optString("main", ""));
        }
        candidates.addAll(java.util.List.of("index.ts", "index.js", "src/index.ts", "src/index.js",
                "server.ts", "server.js", "main.ts", "main.js"));
        for (String candidate : candidates) {
            String entry = candidate.startsWith("./") ? candidate.substring(2) : candidate;
            if (!entry.isEmpty() && safePath(entry) && new File(dir, entry).isFile()) {
                d.bunEntry = entry;
                break;
            }
        }
    }

    /** The file's text, or "" when it is absent, unreadable or over the read cap. */
    private static String text(File dir, String name) {
        File file = new File(dir, name);
        if (!file.isFile()) {
            return "";
        }
        try {
            return BoundedReads.read(file.toPath());
        } catch (Exception ex) {
            return "";
        }
    }

    private static JSONObject json(File dir, String name) {
        try {
            String text = text(dir, name);
            return text.isEmpty() ? null : new JSONObject(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** True when {@code word} stands in {@code text} as a name of its own ("flask", never "flask-cors"'s tail). */
    static boolean mentions(String text, String word) {
        for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
            boolean before = at == 0 || !nameChar(text.charAt(at - 1));
            int end = at + word.length();
            boolean after = end == text.length() || !nameChar(text.charAt(end));
            if (before && after) {
                return true;
            }
        }
        return false;
    }

    private static boolean nameChar(char c) {
        return c == '_' || c == '-' || c == '.' || Character.isLetterOrDigit(c);
    }

    /** A name this file may repeat inside a Dockerfile: no space, quote, newline or shell character. */
    private static boolean safeName(String s) {
        return s != null && s.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    }

    private static boolean safePath(String s) {
        if (s == null || s.isEmpty() || s.length() > 200 || s.startsWith("/")) {
            return false;
        }
        for (String segment : s.split("/", -1)) {
            if (!safeName(segment) && !segment.matches("@[A-Za-z0-9_.-]{1,127}") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String trimSlashes(String s) {
        String out = s.strip();
        while (out.startsWith("./")) {
            out = out.substring(2);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    // ------------------------------------------------------------ writing the files

    /**
     * What we will write for a project in its kind's ordinary shape:
     * filename -> content. Deterministic, no I/O. The Docker panel reads
     * the project first and calls the {@link Facts} form.
     */
    public static Map<String, String> generate(ProjectInspector.ProjectKind kind,
            String projectName, boolean nodeBuildsStatic) {
        return generate(kind, projectName, Facts.assumed(nodeBuildsStatic));
    }

    /** What we will write: filename -> content. Deterministic, no I/O. */
    public static Map<String, String> generate(ProjectInspector.ProjectKind kind,
            String projectName, Facts facts) {
        Map<String, String> files = new LinkedHashMap<>();
        String name = projectName == null || projectName.isBlank() ? "app" : projectName;
        boolean nodeBuildsStatic = facts.nodeStatic();
        boolean side = facts.foreignCompose();
        String compose = side ? SIDE_COMPOSE : COMPOSE;
        switch (kind) {
            case NODE -> {
                files.put("Dockerfile", nodeBuildsStatic ? nodeStatic(facts) : nodeServer(facts));
                if (nodeBuildsStatic) {
                    files.put("docker/nginx.conf", nodeNginxConf());
                }
                files.put(".dockerignore", ignore(side, "node_modules", "dist", ".git", "*.log"));
            }
            case BUN -> {
                files.put("Dockerfile", bun(facts));
                files.put(".dockerignore", ignore(side, "node_modules", ".git", "*.log"));
            }
            case GO -> {
                files.put("Dockerfile", go(name));
                files.put(".dockerignore", ignore(side, "bin", ".git"));
            }
            case RUST -> {
                files.put("Dockerfile", rust(name, facts));
                files.put(".dockerignore", ignore(side, "target", ".git"));
            }
            case PYTHON -> {
                files.put("Dockerfile", python(facts));
                files.put(".dockerignore", ignore(side, "__pycache__", ".venv", ".git"));
            }
            case DENO -> {
                files.put("Dockerfile", deno(facts));
                files.put(".dockerignore", ignore(side, ".git", "coverage"));
            }
            case PHP -> {
                files.put("Dockerfile", php());
                files.put(phpNginxConfName(side), phpNginxConf(name));
                files.put(".dockerignore", ignore(side, "vendor", ".env", ".git"));
            }
            default -> {
                files.put("Dockerfile", generic());
                files.put(".dockerignore", ignore(side, ".git"));
            }
        }
        // php-fpm doesn't speak HTTP; its compose pairs the app with an
        // nginx sidecar instead of publishing the app port directly
        files.put(compose, (side ? SIDE_COMPOSE_HEADER : "") + (kind == ProjectInspector.ProjectKind.PHP
                ? phpCompose(name, phpNginxConfName(side))
                : compose(name, defaultPort(kind, nodeBuildsStatic))));
        return files;
    }

    /** The port the generated container listens on. */
    public static int defaultPort(ProjectInspector.ProjectKind kind, boolean nodeBuildsStatic) {
        return switch (kind) {
            case NODE -> nodeBuildsStatic ? 80 : 3000;
            case BUN -> 3000;
            case GO, RUST -> 8080;
            case PYTHON, DENO -> 8000;
            case PHP -> 80; // the nginx sidecar fronts the fpm container
            default -> 8080;
        };
    }

    /** True when the Node project builds a static bundle (vite & friends). */
    public static boolean buildsStatic(File projectDir) {
        return ProjectInspector.firstDependency(projectDir,
                "vite", "react-scripts", "@angular/cli", "svelte", "parcel") != null;
    }

    private static final String SIDE_COMPOSE_HEADER = """
            # This project already has a compose file of its own, and Dockerize
            # leaves it alone: a compose.yaml written beside it would be the one
            # `docker compose up` starts. Run this stack by name instead:
            #   docker compose -f compose.nmox.yaml up
            """;

    private static String ignore(boolean side, String... extra) {
        StringBuilder sb = new StringBuilder("""
                Dockerfile
                compose.yaml
                .dockerignore
                """);
        if (side) {
            sb.append(SIDE_COMPOSE).append('\n');
        }
        for (String e : extra) {
            sb.append(e).append('\n');
        }
        return sb.toString();
    }

    /**
     * The lines that install a Node project's dependencies with the
     * manager the project uses ({@code ProjectInspector.nodePackageManager}:
     * the corepack pin, then the lockfile). npm in a pnpm or yarn project
     * has no package-lock.json, and {@code npm ci} refuses without one.
     *
     * @param production leave devDependencies out (the server image)
     */
    private static String nodeInstall(Facts f, boolean production) {
        // corepack is no longer part of Node (25+), so it is installed, not assumed
        String corepack = """
                # this project uses %s: corepack provides the version it pins
                ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
                RUN npm install -g corepack@latest && corepack enable
                """;
        String unlocked = "# no %s in this project: nothing pins the versions installed here. Commit one.\n";
        switch (f.nodeManager()) {
            case "pnpm" -> {
                // the wildcards tolerate a file that is not there
                return corepack.formatted("pnpm")
                        + (f.nodeLocked() ? "" : unlocked.formatted("pnpm-lock.yaml"))
                        + "COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml* ./\n"
                        + "RUN pnpm install" + (f.nodeLocked() ? " --frozen-lockfile" : "")
                        + (production ? " --prod" : "") + "\n";
            }
            case "yarn" -> {
                String copy = "COPY package.json yarn.lock* .yarnrc.yml* ./\n"
                        + (f.yarnDir() ? "COPY .yarn ./.yarn\n" : "");
                String run;
                if (f.yarnBerry()) {
                    // yarn 2+ has no --production install; the image carries devDependencies
                    run = "RUN yarn install" + (f.nodeLocked() ? " --immutable" : "") + "\n";
                } else {
                    run = "RUN yarn install" + (f.nodeLocked() ? " --frozen-lockfile" : "")
                            + (production ? " --production" : "") + "\n";
                }
                return corepack.formatted(f.yarnBerry() ? "yarn (2 or later)" : "yarn")
                        + (f.nodeLocked() ? "" : unlocked.formatted("yarn.lock")) + copy + run;
            }
            default -> {
                if (f.nodeLocked()) {
                    return "COPY package*.json ./\nRUN npm ci" + (production ? " --omit=dev" : "") + "\n";
                }
                // npm ci refuses a project with no lockfile
                return unlocked.formatted("package-lock.json")
                        + "COPY package*.json ./\nRUN npm install" + (production ? " --omit=dev" : "") + "\n";
            }
        }
    }

    private static String nodeStatic(Facts f) {
        String run = "npm".equals(f.nodeManager()) ? "npm" : f.nodeManager();
        return "# Build the static bundle, serve it with nginx - tiny final image\n"
                + "FROM " + NODE_IMAGE + " AS build\n"
                + "WORKDIR /app\n"
                + nodeInstall(f, false)
                + "COPY . .\n"
                + "RUN " + run + " run build\n"
                + "\n"
                + "FROM " + NGINX_IMAGE + "\n"
                + "COPY docker/nginx.conf /etc/nginx/conf.d/default.conf\n"
                + (f.staticNote() == null ? "" : "# " + f.staticNote() + "\n")
                + "COPY --from=build /app/" + f.staticDir() + " /usr/share/nginx/html\n"
                + "EXPOSE 80\n";
    }

    /**
     * The static-site server block. The tools buildsStatic() keys on
     * (vite, angular, svelte, ...) all build single-page apps, and a
     * client-routed SPA under stock nginx 404s on every deep-link
     * refresh - unknown paths must serve the shell.
     */
    private static String nodeNginxConf() {
        return """
                server {
                    listen 80;
                    server_name _;

                    root /usr/share/nginx/html;
                    index index.html;

                    location / {
                        # single-page app: client routes serve the shell.
                        # For a plain multi-page site, change the fallback to =404.
                        try_files $uri $uri/ /index.html;
                    }
                }
                """;
    }

    private static String nodeServer(Facts f) {
        boolean npm = "npm".equals(f.nodeManager());
        return "# Install with the lockfile, run as the non-root node user\n"
                + "FROM " + NODE_IMAGE + "\n"
                + "WORKDIR /app\n"
                + "ENV NODE_ENV=production\n"
                + nodeInstall(f, true)
                + "COPY . .\n"
                + "USER node\n"
                + "EXPOSE 3000\n"
                // the node user has no corepack cache of its own, and a container
                // should not download its package manager when it starts
                + (npm ? "" : "# npm runs the same \"start\" script; " + f.nodeManager()
                        + " was only needed to install\n")
                + "CMD [\"npm\", \"start\"]\n";
    }

    private static String bun(Facts f) {
        StringBuilder sb = new StringBuilder("""
                # Bun's own image: dependencies from the lockfile, run as the non-root bun user
                FROM %s
                WORKDIR /app
                ENV NODE_ENV=production
                """.formatted(BUN_IMAGE));
        if (!f.bunPackage()) {
            sb.append("# no package.json in this project: nothing to install\n");
        } else if (f.bunLocked()) {
            sb.append("# the wildcard takes either lockfile name (bun.lock, bun.lockb)\n")
                    .append("COPY package.json bun.lock* ./\n")
                    .append("RUN bun install --frozen-lockfile --production\n");
        } else {
            sb.append("# no bun.lock in this project: nothing pins the versions installed here. Commit one.\n")
                    .append("COPY package.json ./\n")
                    .append("RUN bun install --production\n");
        }
        sb.append("COPY . .\nUSER bun\nEXPOSE 3000\n");
        if (f.bunScript()) {
            sb.append("CMD [\"bun\", \"run\", \"start\"]\n");
        } else if (f.bunEntry() != null) {
            sb.append("# no \"start\" script in package.json: this runs the entry file that was found\n")
                    .append("CMD [\"bun\", \"run\", \"").append(f.bunEntry()).append("\"]\n");
        } else {
            sb.append("# NO START COMMAND WAS DETECTED: no \"start\" script and no index/server/main file.\n")
                    .append("# Name your entry here, for example CMD [\"bun\", \"run\", \"src/app.ts\"]\n")
                    .append("CMD [\"bun\", \"-e\", \"console.error('NMOX Studio Dockerize: no start command was detected"
                            + " for this project. Edit the CMD line of its Dockerfile.'); process.exit(1)\"]\n");
        }
        return sb.toString();
    }

    private static String go(String name) {
        // NOT `go build ./...`: -o with a file refuses the moment the module
        // grows a second package ("cannot write multiple packages to
        // non-directory") - proven by building the generated file for real.
        // And NOT `-o /<name>`: the name is the project folder's, and a folder
        // called go, src, bin, usr, etc or tmp names a directory that exists in
        // the image, so the binary was written INTO it and the entry point was
        // a directory. /out and /app exist in neither image.
        return """
                # Static binary in a distroless image - ~10MB, no shell to exploit
                FROM %s AS build
                WORKDIR /src
                COPY go.* ./
                RUN go mod download
                COPY . .
                # builds the root main package; point at ./cmd/%s if yours lives there
                RUN CGO_ENABLED=0 go build -o /out/%s .

                FROM %s
                COPY --from=build /out/%s /app/%s
                EXPOSE 8080
                ENTRYPOINT ["/app/%s"]
                """.formatted(GO_IMAGE, name, name, DISTROLESS_IMAGE, name, name, name);
    }

    private static String rust(String name, Facts f) {
        // target/ lives in a cache mount, so the binary must be copied out
        // inside the same RUN - COPY --from can't see cache mounts. It is
        // copied to /out, which the image does not have: /<name> was a
        // directory for a crate called src.
        String bin = f.rustBinary() == null || f.rustBinary().isEmpty() ? name : f.rustBinary();
        String note = f.rustWorkspace()
                ? """
                # Cargo.toml here is a WORKSPACE ROOT with no [package], so no binary name
                # could be read from it. "%s" below is this folder's name and is a guess:
                # replace it (three places) with the binary of the member you ship.
                """.formatted(bin)
                : "";
        return """
                # Release build, then a minimal runtime layer
                FROM %s AS build
                WORKDIR /src
                COPY . .
                %s# BuildKit cache mounts keep the registry and target dir across
                # builds - a code edit recompiles your crate, not every dependency
                RUN --mount=type=cache,target=/usr/local/cargo/registry \\
                    --mount=type=cache,target=/src/target \\
                    cargo build --release && mkdir -p /out && cp target/release/%s /out/%s

                FROM %s
                COPY --from=build /out/%s /usr/local/bin/%s
                EXPOSE 8080
                CMD ["%s"]
                """.formatted(RUST_IMAGE, note, bin, bin, DEBIAN_IMAGE, bin, bin, bin);
    }

    private static String python(Facts f) {
        StringBuilder sb = new StringBuilder("FROM " + PYTHON_IMAGE + "\nWORKDIR /app\n");
        switch (f.pyInstall()) {
            case REQUIREMENTS -> sb.append("""
                    COPY requirements.txt ./
                    RUN pip install --no-cache-dir -r requirements.txt
                    COPY . .
                    """);
            case PROJECT -> sb.append("""
                    # no requirements.txt: the project installs itself from pyproject.toml or setup.py
                    COPY . .
                    RUN pip install --no-cache-dir .
                    """);
            default -> sb.append("""
                    # no requirements.txt, pyproject.toml or setup.py: nothing is installed
                    COPY . .
                    """);
        }
        sb.append("EXPOSE 8000\n");
        String module = f.pyModule() == null ? "main" : f.pyModule();
        if (f.pyModule() == null && f.pyStart() != PyStart.DJANGO && f.pyStart() != PyStart.UNKNOWN) {
            sb.append("# no main.py or app.py at the project root: \"main\" below is a guess, name your module\n");
        }
        switch (f.pyStart()) {
            case UVICORN -> sb.append("CMD [\"python\", \"-m\", \"uvicorn\", \"").append(module)
                    .append(":app\", \"--host\", \"0.0.0.0\", \"--port\", \"8000\"]\n");
            case GUNICORN -> sb.append("CMD [\"python\", \"-m\", \"gunicorn\", \"--bind\", \"0.0.0.0:8000\", \"")
                    .append(module).append(":app\"]\n");
            case FLASK -> sb.append("CMD [\"python\", \"-m\", \"flask\", \"--app\", \"").append(module)
                    .append("\", \"run\", \"--host=0.0.0.0\", \"--port=8000\"]\n");
            case DJANGO -> sb.append("# Django's development server; put gunicorn or uvicorn in front for production\n")
                    .append("CMD [\"python\", \"manage.py\", \"runserver\", \"0.0.0.0:8000\"]\n");
            default -> sb.append("""
                    # NO START COMMAND WAS DETECTED: the dependencies name no uvicorn, fastapi,
                    # gunicorn or flask, and no django with a manage.py. Name your entry here,
                    # for example CMD ["python", "main.py"]
                    CMD ["python", "-c", "import sys; sys.exit('NMOX Studio Dockerize: no start command was detected for this project. Edit the CMD line of its Dockerfile.')"]
                    """);
        }
        return sb.toString();
    }

    private static String deno(Facts f) {
        if (f.denoWorkspace()) {
            // the members' own deno.json files are part of the manifest set and
            // cannot be named from here, so nothing is installed before the source
            return """
                    FROM %s
                    WORKDIR /app
                    # deno.json names workspace members, whose manifests live beside their
                    # source: everything is copied first, so a code edit re-resolves dependencies
                    COPY . .
                    RUN deno install
                    RUN deno cache main.ts || true
                    EXPOSE 8000
                    CMD ["deno", "task", "start"]
                    """.formatted(DENO_IMAGE);
        }
        return """
                FROM %s
                WORKDIR /app
                # dependencies first, so code edits don't re-download them: this layer is
                # rebuilt only when a manifest changes (the wildcards take deno.json or
                # deno.jsonc, and tolerate a project with no deno.lock)
                COPY deno.json* deno.lock* ./
                RUN deno install
                COPY . .
                RUN deno cache main.ts || true
                EXPOSE 8000
                CMD ["deno", "task", "start"]
                """.formatted(DENO_IMAGE);
    }

    private static String php() {
        return """
                # Composer resolves dependencies in its own stage; the runtime
                # image is slim FPM and never carries composer itself
                FROM %s AS deps
                WORKDIR /app
                # manifests first, so code edits don't re-resolve the packages
                # (the wildcard tolerates a project with no composer.lock)
                COPY composer.json composer.lock* ./
                RUN composer install --no-dev --no-interaction --no-scripts --no-autoloader
                COPY . .
                # the optimized autoloader is a map of the source, so it is written after it
                RUN composer dump-autoload --no-dev --optimize

                FROM %s
                WORKDIR /var/www/html
                COPY --from=deps /app/vendor ./vendor
                COPY . .
                EXPOSE 9000
                CMD ["php-fpm"]
                """.formatted(COMPOSER_IMAGE, PHP_IMAGE);
    }

    /**
     * Where the sidecar's server block goes. A project with a stack of its
     * own may well have a {@code docker/nginx.conf} that stack mounts (the
     * product's own LEMP template does), and this one names a service that
     * stack does not have.
     */
    private static String phpNginxConfName(boolean side) {
        return side ? "docker/nginx.nmox.conf" : "docker/nginx.conf";
    }

    /** The nginx sidecar's server block: static files, then the front controller. */
    private static String phpNginxConf(String name) {
        return """
                server {
                    listen 80;
                    server_name _;

                    root /var/www/html/public;
                    index index.php;

                    location / {
                        try_files $uri $uri/ /index.php?$query_string;
                    }

                    location ~ \\.php$ {
                        include fastcgi_params;
                        fastcgi_pass %s:9000;
                        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
                    }
                }
                """.formatted(name);
    }

    /** fpm app + nginx sidecar, nginx published on 80. */
    private static String phpCompose(String name, String conf) {
        return """
                services:
                  %s:
                    build: .
                    restart: unless-stopped

                  nginx:
                    image: %s
                    ports:
                      - "80:80"
                    volumes:
                      - ./%s:/etc/nginx/conf.d/default.conf:ro
                      - .:/var/www/html:ro
                    depends_on:
                      - %s
                    restart: unless-stopped
                """.formatted(name, NGINX_IMAGE, conf, name);
    }

    private static String generic() {
        return """
                # No toolchain detected - a starting point, not a guess
                FROM %s
                WORKDIR /app
                COPY . .
                CMD ["/bin/sh", "-c", "echo 'edit this Dockerfile for your runtime' && sleep infinity"]
                """.formatted(DEBIAN_IMAGE);
    }

    private static String compose(String name, int port) {
        return """
                services:
                  %s:
                    build: .
                    ports:
                      - "%d:%d"
                    restart: unless-stopped
                """.formatted(name, port, port);
    }
}
