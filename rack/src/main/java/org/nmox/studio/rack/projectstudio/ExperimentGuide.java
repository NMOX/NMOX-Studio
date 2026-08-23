package org.nmox.studio.rack.projectstudio;

/**
 * The walkthrough an experiment is born with (v2.36.0, David's ask:
 * experiments should be the first tool for learning a stack — set up
 * success and teach). Every {@link Experiments#create} writes an
 * EXPERIMENT.md built here, and the editor opens it the moment the
 * rack aims: what to press, which file to change, and where THIS
 * stack's IDE intelligence lives.
 *
 * <p>Pure on purpose: every claim in the generated text is a product
 * claim, so the claims are unit-testable — and the one claim that can
 * silently rot ("open {@code this file} and change it") is pinned by
 * {@code ExperimentGuideParityTest}, which generates every template
 * for real and asserts the named file exists in its output.
 */
public final class ExperimentGuide {

    private ExperimentGuide() {
    }

    /** Per-template teaching facts; every field lands in the text. */
    record Stack(String editFile, String editHint, String runStory,
            String seeStory, String[] powers) {
    }

    /**
     * The file the walkthrough tells the learner to edit — the one
     * claim proven against the real generator output. Name-dependent
     * only for mix, whose lib module is named after the project.
     */
    static String editFile(ProjectTemplates t, String name) {
        return stack(t, name).editFile();
    }

    private static final String[] WEB_POWERS = {
        "Type `class=\"` in the HTML — completion offers every class your real stylesheets declare, and ⌘-click jumps between a class and its rule.",
        "Color literals in the CSS paint as inline swatches; ⌘-click one to open the picker and it rewrites the literal in its authored form.",
        "Press ⌥⌘E on an Emmet abbreviation (`ul>li*3`, or `bgc:tomato` inside a style block) to expand it.",
    };

    private static final String[] VITE_POWERS = {
        "Completion and ⌘B jumps ride tsserver out of the box.",
        "Color literals in the stylesheets paint as inline swatches; ⌘-click one for the picker.",
        "Right-click inside a test and Run Focused Test runs just that test.",
    };

    private static Stack stack(ProjectTemplates t, String name) {
        return switch (t) {
            case VANILLA -> new Stack("index.html",
                    "change the heading text",
                    "a static dev server starts, the ⇄ serving chip lights on the status line, and the page opens in the in-app Browser (⌥⌘4).",
                    "reload the Browser tab and your change is live.",
                    WEB_POWERS);
            case VITE_REACT -> new Stack("src/App.jsx",
                    "change the heading text in the JSX",
                    "Vite's dev server starts and the ⇄ serving chip lights; open the URL in the in-app Browser (⌥⌘4).",
                    "Vite hot-reloads the page the moment you save.",
                    VITE_POWERS);
            case VITE_VUE -> new Stack("src/App.vue",
                    "change the template block",
                    "Vite's dev server starts and the ⇄ serving chip lights; open the URL in the in-app Browser (⌥⌘4).",
                    "Vite hot-reloads the page the moment you save.",
                    new String[] {
                        "The Vue language server answers inside the SFC — completion, diagnostics, ⌘B.",
                        "The `<style>` block gets the full stylesheet treatment: swatches, the ⌘-click picker, `var(` token completion, Emmet ⌥⌘E.",
                        "Right-click inside a test and Run Focused Test runs just that test.", });
            case EXPRESS_API -> new Stack("server.js",
                    "add a route — `app.get('/hello', (req, res) => res.json({hi: true}))`",
                    "nodemon starts the API and the ⇄ serving chip lights.",
                    "nodemon restarts on save; the Navigator outline lists every route as `GET /path`.",
                    new String[] {
                        "Right-click a route line and **Test in API Studio** drafts the request with the verb and path filled in.",
                        "Type `process.env.` — completion offers the keys of your real `.env` family, and ⌘-click jumps to the declaring line.",
                        "⌘-click a `fetch('/path')` string anywhere in the project to jump to the Express route that serves it — `:id` params understood.", });
            case VITE_SOLID -> new Stack("src/index.jsx",
                    "change the heading text in the JSX",
                    "Vite's dev server starts and the ⇄ serving chip lights; open the URL in the in-app Browser (⌥⌘4).",
                    "Vite hot-reloads the page the moment you save.",
                    VITE_POWERS);
            case VITE_SVELTE -> new Stack("src/App.svelte",
                    "change the markup",
                    "Vite's dev server starts and the ⇄ serving chip lights; open the URL in the in-app Browser (⌥⌘4).",
                    "Vite hot-reloads the page the moment you save.",
                    new String[] {
                        "Svelte 5 runes (`$state`, `$derived`, `$effect`) complete after the `$`.",
                        "The `<style>` block gets the full stylesheet treatment: swatches, the ⌘-click picker, Emmet ⌥⌘E.",
                        "The svelte language server answers inside the component.", });
            case TS_LIBRARY -> new Stack("src/index.ts",
                    "add an exported function",
                    "the build lane runs `tsc`; the test lane runs Vitest.",
                    "the Vitest suite in `src/index.test.ts` is one F6 away — grow it alongside the code.",
                    new String[] {
                        "Completion, ⌘B, and rename ride tsserver.",
                        "Right-click inside a test and Run Focused Test runs just that test.",
                        "Right-click any `.json` file and **Copy TS Types** turns its shape into interfaces on the clipboard.", });
            case PYTHON_CLI -> new Stack("main.py",
                    "change what it prints",
                    "the run lane executes `main.py` with your Python.",
                    "rerun after each save; the pytest suite rides the test lane.",
                    new String[] {
                        "Right-click inside a pytest test and Run Focused Test runs just that test.",
                        "Tools ▸ Environment Doctor shows which interpreters and tools this machine has, with install hints.", });
            case GO_SERVICE -> new Stack("main.go",
                    "change the handler's response text",
                    "`go run` starts the hello service.",
                    "stop and rerun after a save; `go test` rides the test lane.",
                    new String[] {
                        "gopls answers completion and jumps once Go is installed.",
                        "The PURITY lane speaks `go vet`; GLOSS formats with gofmt.",
                        "Right-click inside a test and Run Focused Test runs just that test.", });
            case RUST_CLI -> new Stack("src/main.rs",
                    "change what it prints",
                    "`cargo run` builds and runs the crate.",
                    "rerun after each save; `cargo test` rides the test lane.",
                    new String[] {
                        "rust-analyzer answers completion and jumps once the rustup component is installed.",
                        "The PURITY lane speaks `cargo clippy`; GLOSS formats with `cargo fmt`.",
                        "Right-click inside a `#[test]` fn and Run Focused Test runs just that test.", });
            case ANGULAR -> new Stack("src/app/app.component.html",
                    "change the template",
                    "`ng serve` starts and the in-app Browser opens on the app automatically.",
                    "the page rebuilds on save; the Navigator outlines `app.routes.ts` as a route table.",
                    new String[] {
                        "The Angular Language Service type-checks templates live — a typo'd property gets a \"Did you mean…?\" squiggle.",
                        "Right-click a component to switch between class, template, styles, and spec.",
                        "@-blocks and *-directives complete inside templates.", });
            case ELIXIR_MIX -> new Stack("lib/" + name.replace('-', '_') + ".ex",
                    "change the module",
                    "the BEAM lanes speak mix: run, and `mix test` for the ExUnit suite.",
                    "rerun after each save.",
                    new String[] {
                        "Tools ▸ Environment Doctor probes elixir/mix with install hints.",
                        "The rack's BEAM wiring is already aimed at this project.", });
            case PHP_WEB -> new Stack("public/index.php",
                    "change the page it renders",
                    "`php -S` serves the site and the ⇄ serving chip lights.",
                    "reload the Browser tab after a save.",
                    new String[] {
                        "The test lane runs PHPUnit; PURITY speaks phpstan; GLOSS formats with Pint.",
                        "The template ships an nginx+fpm+MariaDB compose file for when it grows up.", });
            case CLASSIC_WEB_JQUERY -> new Stack("js/app.js",
                    "change what the ready handler does",
                    "the site is served as-is — no build step — and the ⇄ serving chip lights.",
                    "reload the Browser tab after a save.",
                    new String[] {
                        "jQuery's API completes after `$.` and `$(sel).` — the vendored library is detected from the script tag.",
                        "`class=\"` completion and the class↔rule ⌘-click jumps work across the HTML and CSS.", });
            case CLASSIC_WEB_MOOTOOLS -> new Stack("js/app.js",
                    "change the class or its greeting",
                    "the site is served as-is — no build step — and the ⇄ serving chip lights.",
                    "reload the Browser tab after a save.",
                    new String[] {
                        "MooTools' API completes — Class, Extends, and the Element family are known.",
                        "`class=\"` completion and the class↔rule ⌘-click jumps work across the HTML and CSS.", });
        };
    }

    /** The EXPERIMENT.md content for a template experiment. */
    public static String walkthrough(ProjectTemplates t, String name) {
        Stack s = stack(t, name);
        StringBuilder b = new StringBuilder();
        b.append("# ").append(name).append(" — an experiment\n\n");
        b.append("**").append(t.getDisplayName()).append("** — ")
                .append(t.getDescription()).append(".\n\n");
        b.append("This folder is a throwaway. It lives in `~/.nmox/experiments`, has no\n");
        b.append("git repo, never enters your recents, and is already trusted — every\n");
        b.append("run works without prompts. Nothing here is precious: break it.\n\n");
        b.append("## 1 · Run it\n\n");
        b.append("Press **F6** (or GO on the rack) — ").append(s.runStory()).append("\n\n");
        b.append("## 2 · Change something\n\n");
        b.append("Open `").append(s.editFile()).append("`, ").append(s.editHint())
                .append(", and save — ").append(s.seeStory()).append("\n\n");
        b.append("## 3 · The IDE is wired for this stack\n\n");
        for (String p : s.powers()) {
            b.append("- ").append(p).append("\n");
        }
        b.append("\n## When you're done\n\n");
        b.append("**File ▸ Experiments…** manages the shelf: *Promote…* graduates a\n");
        b.append("keeper into a real project (moved out, git init), *Discard…* stops\n");
        b.append("anything running here and deletes the tree. Experiments are meant\n");
        b.append("to be discarded — that is what makes them safe to start.\n\n");
        b.append("## Keep learning\n\n");
        b.append("**File ▸ New Learning Space…** holds 92 guided tutorials — languages,\n");
        b.append("frameworks, and libraries — each with sample code, a walkthrough,\n");
        b.append("and a rack wired with a live REPL.\n");
        return b.toString();
    }
}
