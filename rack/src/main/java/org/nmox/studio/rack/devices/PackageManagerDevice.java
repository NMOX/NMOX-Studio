package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * CRATE Package Manager: dependency installation and maintenance.
 * INSTALL is the primary action (fired by the RUN jack). The DEPS LCD
 * shows the declared dependency counts straight from package.json and
 * refreshes after every operation.
 */
public class PackageManagerDevice extends CommandDevice {

    private static final String[] MANAGERS = {"auto", "npm", "yarn", "pnpm"};

    private final Knob managerKnob;
    private final LcdDisplay depsLcd;

    public PackageManagerDevice() {
        super("package-manager", "CRATE", "PACKAGE MANAGER", new Color(214, 121, 41), 2);

        RackButton install = place(new RackButton("INSTALL", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        install.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        managerKnob = place(new Knob("ENGINE", MANAGERS, 0), 180, 40);
        RackButton update = place(new RackButton("UPDATE", RackStyle.MUTATE), 254, 52);
        RackButton outdated = place(new RackButton("CHECK", RackStyle.QUERY), 318, 52);
        depsLcd = place(new LcdDisplay(96, 1), 382, 52);
        depsLcd.getAccessibleContext().setAccessibleName("dependencies");
        depsLcd.setText("—");
        depsLcd.setToolTipText("dependencies + devDependencies declared in package.json");

        install.addActionListener(e -> installAll());
        update.addActionListener(e -> launch(cmd("update")));
        outdated.addActionListener(e -> launch(cmd("outdated")));
        stop.addActionListener(e -> stopProcess());

        param("manager", managerKnob);
    }

    private String manager() {
        String selected = MANAGERS[managerKnob.getSelectedIndex()];
        return "auto".equals(selected)
                ? ProjectInspector.nodePackageManager(projectDir())
                : selected;
    }

    private boolean isAuto() {
        return managerKnob.getSelectedIndex() == 0;
    }

    /**
     * In AUTO the verb maps onto whatever toolchain the project uses;
     * an explicit npm/yarn/pnpm selection always talks Node.
     */
    /** Package-private: the verb→argv seam device tests drive directly. */
    List<String> cmd(String verb) {
        ProjectInspector.ProjectKind kind = isAuto()
                ? effectiveKind()
                : ProjectInspector.ProjectKind.NODE;
        return cmdFor(kind, verb);
    }

    private List<String> cmdFor(ProjectInspector.ProjectKind kind, String verb) {
        return switch (kind) {
            case BUN -> switch (verb) {
                case "update" -> List.of("bun", "update");
                case "outdated" -> List.of("bun", "outdated");
                default -> List.of("bun", "install");
            };
            case DENO -> switch (verb) {
                case "outdated" -> List.of("deno", "outdated");
                default -> List.of("deno", "install");
            };
            case RUST -> switch (verb) {
                case "update" -> List.of("cargo", "update");
                case "outdated" -> List.of("cargo", "update", "--dry-run");
                default -> List.of("cargo", "fetch");
            };
            case FOUNDRY -> switch (verb) {
                case "update" -> List.of("forge", "update");
                default -> List.of("forge", "install");
            };
            case ELIXIR -> switch (verb) {
                case "update" -> List.of("mix", "deps.update", "--all");
                case "outdated" -> List.of("mix", "hex.outdated");
                default -> List.of("mix", "deps.get");
            };
            case GLEAM -> switch (verb) {
                case "update" -> List.of("gleam", "deps", "update");
                case "outdated" -> null; // gleam has no outdated query — CHECK greys
                default -> List.of("gleam", "deps", "download");
            };
            case JULIA -> switch (verb) {
                case "update" -> List.of("julia", "--project=.", "-e", "using Pkg; Pkg.update()");
                case "outdated" -> List.of("julia", "--project=.", "-e", "using Pkg; Pkg.status(outdated=true)");
                default -> List.of("julia", "--project=.", "-e", "using Pkg; Pkg.instantiate()");
            };
            case NIM -> switch (verb) {
                case "update" -> List.of("nimble", "refresh");
                case "outdated" -> null; // nimble has no outdated query — CHECK greys
                default -> List.of("nimble", "install", "-d", "-y");
            };
            case DLANG -> switch (verb) {
                case "update" -> List.of("dub", "upgrade");
                case "outdated" -> null; // dub has no outdated query — CHECK greys
                default -> List.of("dub", "upgrade", "--missing-only");
            };
            case RACKET -> switch (verb) {
                case "update" -> List.of("raco", "pkg", "update", "--auto");
                case "outdated" -> null; // raco has no outdated query — CHECK greys
                default -> List.of("raco", "pkg", "install", "--auto", "--skip-installed");
            };
            case PURESCRIPT -> switch (verb) {
                case "update" -> List.of("spago", "upgrade");
                case "outdated" -> null; // spago has no outdated query — CHECK greys
                default -> List.of("spago", "install");
            };
            case CAIRO -> switch (verb) {
                case "update" -> List.of("scarb", "update");
                case "outdated" -> null; // scarb has no outdated query — CHECK greys
                default -> List.of("scarb", "build"); // build fetches deps
            };
            case VLANG -> switch (verb) {
                case "update" -> List.of("v", "update");
                case "outdated" -> null; // vpm has no outdated query — CHECK greys
                default -> List.of("v", "install");
            };
            case FORTRAN -> switch (verb) {
                case "update" -> List.of("fpm", "update");
                case "outdated" -> null; // fpm has no outdated query — CHECK greys
                default -> List.of("fpm", "build"); // fpm fetches deps at build time
            };
            case ADA -> switch (verb) {
                case "update" -> List.of("alr", "update");
                case "outdated" -> null; // alr has no outdated query — CHECK greys
                default -> List.of("alr", "build"); // alr fetches deps at build time
            };
            // ELM deps live in elm.json / RESCRIPT deps in package.json beside their manifests
            // — the NODE lane (npm/yarn/pnpm detection) already covers them
            case ELM, RESCRIPT -> null;
            case ERLANG -> switch (verb) {
                case "update" -> List.of("rebar3", "upgrade", "--all");
                default -> List.of("rebar3", "get-deps");
            };
            case CLOJURE -> List.of("clojure", "-P");
            case SWIFT -> switch (verb) {
                case "update" -> List.of("swift", "package", "update");
                case "outdated" -> List.of("swift", "package", "show-dependencies");
                default -> List.of("swift", "package", "resolve");
            };
            case DOTNET -> switch (verb) {
                case "outdated" -> List.of("dotnet", "list", "package", "--outdated");
                default -> List.of("dotnet", "restore");
            };
            case DART -> switch (verb) {
                case "update" -> List.of("dart", "pub", "upgrade");
                case "outdated" -> List.of("dart", "pub", "outdated");
                default -> List.of("dart", "pub", "get");
            };
            case SCALA -> List.of("sbt", "update");
            case HASKELL -> List.of("stack", "build", "--only-dependencies");
            case ZIG -> List.of("zig", "build", "--fetch");
            case OCAML -> List.of("dune", "build");
            case CRYSTAL -> switch (verb) {
                case "update" -> List.of("shards", "update");
                case "outdated" -> List.of("shards", "outdated");
                default -> List.of("shards", "install");
            };
            case GO -> switch (verb) {
                case "update" -> List.of("go", "get", "-u", "./...");
                case "outdated" -> List.of("go", "list", "-u", "-m", "all");
                default -> List.of("go", "mod", "download");
            };
            case MAVEN -> switch (verb) {
                case "update", "outdated" -> List.of("mvn", "-q", "versions:display-dependency-updates");
                default -> List.of("mvn", "-q", "dependency:resolve");
            };
            case GRADLE -> List.of("gradle", "--quiet", "dependencies");
            case PYTHON -> switch (verb) {
                case "update" -> List.of("pip", "install", "--upgrade", "-r", "requirements.txt");
                case "outdated" -> List.of("pip", "list", "--outdated");
                default -> new java.io.File(ProjectInspector.kindDir(projectDir(),
                        ProjectInspector.ProjectKind.PYTHON), "requirements.txt").isFile()
                        ? List.of("pip", "install", "-r", "requirements.txt")
                        : List.of("pip", "install", "-e", ".");
            };
            case RUBY -> switch (verb) {
                case "update" -> List.of("bundle", "update");
                case "outdated" -> List.of("bundle", "outdated");
                default -> List.of("bundle", "install");
            };
            case PHP -> switch (verb) {
                case "update" -> List.of("composer", "update");
                case "outdated" -> List.of("composer", "outdated");
                default -> List.of("composer", "install");
            };
            // the classic web package manager; npx surfaces its absence honestly
            case BOWER -> switch (verb) {
                case "update" -> List.of("npx", "bower", "update");
                case "outdated" -> List.of("npx", "bower", "list"); // annotates available updates
                default -> List.of("npx", "bower", "install");
            };
            // taskfile/bundler configs and bare static sites declare no
            // installable dependencies of their own — their packages ride
            // the NODE lane whenever a package.json exists
            case WEBPACK, GRUNT, GULP, STATIC -> null;
            default -> List.of(manager(),
                    "update".equals(verb) && "yarn".equals(manager()) ? "upgrade" : verb);
        };
    }

    /** Single launches run where the effective toolchain's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        ProjectInspector.ProjectKind kind = isAuto()
                ? effectiveKind() : ProjectInspector.ProjectKind.NODE;
        return ProjectInspector.kindDir(projectDir(), kind);
    }

    /**
     * INSTALL bootstraps the whole repo: in AUTO with no ROSETTA
     * override, a mixed project sequences every toolchain's install,
     * each in its own manifest directory - one button press readies
     * frontend and backend alike.
     */
    private void installAll() {
        if (!isAuto() || (getRack() != null && getRack().getToolchainOverride() != null)) {
            launch(cmd("install"));
            return;
        }
        if (ProjectInspector.detectKinds(projectDir()).size() <= 1) {
            launch(cmd("install"));
            return;
        }
        launchSequence(installSteps());
    }

    /**
     * The AUTO install sequence: one step per detected toolchain that
     * actually installs anything, each in its own manifest directory —
     * npm before bower on a classic web repo, kinds that carry no
     * installable dependencies (GRUNT, STATIC…) skipped rather than
     * derailing the train. Package-private test seam.
     */
    List<Step> installSteps() {
        List<Step> steps = new java.util.ArrayList<>();
        for (var entry : ProjectInspector.detectKinds(projectDir()).entrySet()) {
            List<String> command = cmdFor(entry.getKey(), "install");
            if (command != null) {
                steps.add(new Step(command, entry.getValue()));
            }
        }
        return steps;
    }

    @Override
    protected void primaryAction() {
        installAll();
    }

    @Override
    protected List<String> buildCommand() {
        return cmd("install");
    }

    @Override
    protected void onAttached() {
        refreshDepsLcd();
    }

    @Override
    public void projectChanged(File dir) {
        refreshDepsLcd();
    }

    /**
     * Manifest pulse: dependency-declaring files refresh the DEPS LCD.
     * Idempotent by construction — the refresh only rewrites LCD text,
     * it fires no signals or knob events.
     */
    @Override
    public void manifestChanged(java.util.List<java.nio.file.Path> changed) {
        if (anyNamed(changed, "package.json", "package-lock.json",
                "pnpm-lock.yaml", "yarn.lock",
                "bower.json", "composer.json", "composer.lock")) {
            refreshDepsLcd();
        }
    }

    /** The faceplate context menu's "Open package.json". */
    @Override
    public java.util.Optional<File> primaryManifest() {
        File pkg = new File(ProjectInspector.kindDir(projectDir(),
                ProjectInspector.ProjectKind.NODE), "package.json");
        return pkg.isFile() ? java.util.Optional.of(pkg) : java.util.Optional.empty();
    }

    /** Installs and updates change package.json; re-read the truth. */
    @Override
    protected void onFinished(int exitCode) {
        refreshDepsLcd();
    }

    /** Test seam: the DEPS LCD text the refresh writes. */
    String depsTextForTest() {
        return depsLcd.getText();
    }

    private void refreshDepsLcd() {
        // dependencyCounts / detectKinds / kindDir all walk the project
        // directory; on a $HOME aim that would touch the TCC-protected folders
        // on the EDT during startup. Read the truth on the background thread and
        // marshal the LCD update back to the EDT.
        File dir = projectDir();
        offEdt(() -> {
            int[] counts = ProjectInspector.dependencyCounts(dir);
            var kinds = ProjectInspector.detectKinds(dir);
            // a present-but-unparseable package.json is a fact worth stating,
            // not a silent shrug - it breaks every AUTO knob downstream
            boolean broken = counts == null && new File(
                    ProjectInspector.kindDir(dir, ProjectInspector.ProjectKind.NODE),
                    "package.json").isFile();
            onEdt(() -> {
            depsLcd.setText(broken ? "PACKAGE.JSON UNREADABLE"
                    : counts != null && kinds.size() <= 1
                    ? counts[0] + "+" + counts[1] + " DEPS"
                    : kinds.size() > 1 ? kinds.size() + " TOOLCHAINS"
                    : !kinds.isEmpty() ? kinds.keySet().iterator().next().manifest()
                    : "NO PROJECT");
            depsLcd.setToolTipText(broken
                    ? "package.json exists but does not parse — fix the JSON"
                    : counts == null
                    ? "No package.json in the project"
                    : counts[0] + " dependencies, " + counts[1] + " devDependencies");
            });
        });
    }
}
