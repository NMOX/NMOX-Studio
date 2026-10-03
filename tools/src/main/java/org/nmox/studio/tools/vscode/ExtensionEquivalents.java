package org.nmox.studio.tools.vscode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.nmox.studio.core.spi.ServerCatalog;
import org.nmox.studio.rack.devices.DeviceType;
import org.openide.util.NbBundle;

/**
 * What covers a VS Code extension in NMOX Studio: the table behind the
 * VS Code extensions sheet, and the one sentence each row renders.
 *
 * <p>VS Code extensions do not install here. A switcher who opens their
 * repository and asks "where are my extensions?" is owed, per extension,
 * a true answer of one {@link Kind}: it is built in, it is a window, it
 * is a rack device, a language server this product starts covers it
 * (with whether that server is installed), the product only colours the
 * syntax, it is the AI device, it does not apply here, it cannot work
 * here, there is no equivalent — or the product does not know the
 * extension, which is what every id absent from the table is told.
 * <b>An id that is not in the table is never guessed at</b>: a wrong
 * claim is worse than "not known".
 *
 * <p><b>Every row is a claim, and each was checked in this repository
 * before it was written.</b> The comment beside a row names the file
 * that makes it true, so the next reader can check it again; a row whose
 * file has gone is a row to delete. The names a sentence shows are not
 * typed here: a window or an action is named by its own registered
 * action ({@link Door}, resolved by {@code ExtensionDoors}), a rack
 * device by its {@link DeviceType} title, a language server by the
 * binary the editor's own catalog knows ({@link ServerCatalog}). So the
 * prose is a dozen templates and a dozen notes, and a renamed window
 * renames itself here.
 *
 * <p>Pure: no Swing and no lookups. {@link Facts} carries what the
 * running product said (door names, server states), gathered by the
 * caller off the event thread.
 */
public final class ExtensionEquivalents {

    private ExtensionEquivalents() {
    }

    /** The kinds of answer a row can give. */
    enum Kind {
        /** The product does it without anything being installed. */
        BUILT_IN,
        /** One of the product's windows does it; the row can open it. */
        WINDOW,
        /** A rack device does it. */
        DEVICE,
        /** A language server the editor starts covers it. */
        SERVER,
        /** The product colours the language and does no more for it. */
        SYNTAX,
        /** The AI device, on its own terms. */
        AI,
        /** A theme, an icon theme, a keymap: it has no meaning here. */
        NOT_APPLICABLE,
        /** The product knows it and it cannot work here. */
        NOT_AVAILABLE,
        /** The product has nothing like it. */
        NO_EQUIVALENT,
        /** Not in the table. */
        UNKNOWN
    }

    /**
     * An action the product registers, by which a row names something and
     * may open it. The ids live in {@code ExtensionDoors}, written so that
     * {@code ActionIdsResolveTest} holds each one to the assembled cluster.
     */
    enum Door {
        DOCKER_PANEL(true),
        DB_STUDIO(true),
        API_STUDIO(true),
        CONTRACT_STUDIO(true),
        TASK_RACK(true),
        TESTS(true),
        AGENT_PORT(true),
        LANGUAGE_SERVERS(true),
        PULL_REQUESTS(true),
        NG_SCHEMATIC(true),
        /** Acts on the editor that has focus, so the sheet names it and does not run it. */
        FORMAT_WITH_PRETTIER(false),
        /** Acts on the stylesheet being edited: named, not run. */
        COMPILE_TO_CSS(false);

        /** Whether the sheet's Open button may run this action. */
        final boolean opens;

        Door(boolean opens) {
            this.opens = opens;
        }
    }

    // ---- the sentences (tools/vscode/Bundle.properties) ---------------------

    static final String K_BUILT_IN = "VsCodeExtensions_builtIn";
    static final String K_WINDOW = "VsCodeExtensions_window";
    static final String K_DEVICE = "VsCodeExtensions_device";
    static final String K_SERVER = "VsCodeExtensions_server";
    static final String K_SERVER_INSTALLED = "VsCodeExtensions_serverInstalled";
    static final String K_SERVER_MISSING = "VsCodeExtensions_serverMissing";
    static final String K_SERVER_MISSING_HINT = "VsCodeExtensions_serverMissingHint";
    static final String K_SYNTAX = "VsCodeExtensions_syntax";
    static final String K_NO_EQUIVALENT = "VsCodeExtensions_noEquivalent";
    static final String K_UNKNOWN = "VsCodeExtensions_unknown";
    static final String K_ABSENT = "VsCodeExtensions_absent";

    static final String N_EDITORCONFIG = "VsCodeExtensions_noteEditorConfig";
    static final String N_SPELLCHECK = "VsCodeExtensions_noteSpellcheck";
    static final String N_DOTENV = "VsCodeExtensions_noteDotenv";
    static final String N_LIVE_SERVER = "VsCodeExtensions_noteLiveServer";
    static final String N_GITLENS = "VsCodeExtensions_noteGitLens";
    static final String N_REST_CLIENT = "VsCodeExtensions_noteRestClient";
    static final String N_PRETTIER = "VsCodeExtensions_notePrettier";
    static final String N_CHROME_DEBUG = "VsCodeExtensions_noteChromeDebug";
    static final String N_COLOURS = "VsCodeExtensions_noteColours";
    static final String N_CSS_CLASSES = "VsCodeExtensions_noteCssClasses";
    static final String N_AI = "VsCodeExtensions_noteAi";
    static final String N_THEME = "VsCodeExtensions_noteTheme";
    static final String N_KEYMAP = "VsCodeExtensions_noteKeymap";
    static final String N_VIM = "VsCodeExtensions_noteVim";
    static final String N_TAILWIND = "VsCodeExtensions_noteTailwind";
    static final String N_CLAUDE_CODE = "VsCodeExtensions_noteClaudeCode";

    /**
     * What covers one extension.
     *
     * @param kind    the kind of answer
     * @param key     the bundle key of its sentence
     * @param door    the action it names or opens, or null
     * @param server  the language server's binary, for {@link Kind#SERVER}
     * @param devices the rack devices, for {@link Kind#DEVICE}
     */
    record Equivalent(Kind kind, String key, Door door, String server, List<DeviceType> devices) {
    }

    /** The answer for every id the table does not hold. */
    static final Equivalent UNKNOWN = new Equivalent(Kind.UNKNOWN, K_UNKNOWN, null, null, List.of());

    private static final Equivalent NONE = new Equivalent(Kind.NO_EQUIVALENT, K_NO_EQUIVALENT, null, null, List.of());
    private static final Equivalent SYNTAX = new Equivalent(Kind.SYNTAX, K_SYNTAX, null, null, List.of());

    private static Equivalent builtIn(String note) {
        return new Equivalent(Kind.BUILT_IN, note, null, null, List.of());
    }

    /** Built in, with the note taking the door's own name as {0}. */
    private static Equivalent builtIn(String note, Door named) {
        return new Equivalent(Kind.BUILT_IN, note, named, null, List.of());
    }

    private static Equivalent window(Door door) {
        return new Equivalent(Kind.WINDOW, K_WINDOW, door, null, List.of());
    }

    private static Equivalent device(DeviceType... devices) {
        return new Equivalent(Kind.DEVICE, K_DEVICE, Door.TASK_RACK, null, List.of(devices));
    }

    private static Equivalent server(String binary) {
        return new Equivalent(Kind.SERVER, K_SERVER, Door.LANGUAGE_SERVERS, binary, List.of());
    }

    private static Equivalent note(Kind kind, String note) {
        return new Equivalent(kind, note, null, null, List.of());
    }

    /** One table row: an extension id, lower case, and what covers it. */
    record Row(String id, Equivalent equivalent) {
    }

    private static void rows(List<Row> out, Equivalent equivalent, String... ids) {
        for (String id : ids) {
            out.add(new Row(id, equivalent));
        }
    }

    /** The table, in the order it is written. Ids are lower case and appear once. */
    static final List<Row> ROWS = table();

    private static final Map<String, Equivalent> BY_ID = ROWS.stream()
            .collect(Collectors.toMap(Row::id, Row::equivalent, (first, second) -> first, LinkedHashMap::new));

    private static List<Row> table() {
        List<Row> t = new ArrayList<>();

        // ---- built in -------------------------------------------------------
        // editor/standards/EditorConfigOnSave.java (trim, final newline, on save)
        // and EditorConfigIndentation.java (indent_style, indent_size, tab_width)
        rows(t, builtIn(N_EDITORCONFIG), "editorconfig.editorconfig");
        // editor/spell/CodeSpellTokenListProvider.java (comments in code),
        // MarkdownSpellTokenListProvider.java, GitMessageSpellTokenListProvider.java,
        // over the platform's spellchecker and its English dictionary (ide cluster:
        // spellchecker, spellchecker-dictionary_en)
        rows(t, builtIn(N_SPELLCHECK), "streetsidesoftware.code-spell-checker");
        // the platform's languages-env module colours .env files (ide cluster);
        // editor/fullstack/EnvKeyCompletionProvider.java completes their keys after
        // process.env. and import.meta.env., EnvKeyHyperlink.java jumps to the line
        rows(t, builtIn(N_DOTENV), "mikestead.dotenv", "dotenv.dotenv-vscode");
        // tools/npm/WebProjectCommands.java (the STATIC lane: Run serves a site
        // that has no manifest); ui/browser/fx/FxBrowserPanel.java and LocalUrls.java
        // (a save reloads a LOCAL page in the in-app Browser)
        rows(t, builtIn(N_LIVE_SERVER), "ritwickdey.liveserver", "ms-vscode.live-server");
        // editor/blame/ToggleLineBlameAction.java (the caret line's blame on the
        // status line), rack/service/GitStatusLine.java (the git chip), and the
        // platform git module's Team menu. A subset of GitLens, and the note says so.
        rows(t, builtIn(N_GITLENS), "eamodio.gitlens");
        // editor/languages/HttpFileLanguage.java + editor/grammars/http.tmLanguage.json
        // (.http/.rest files are a language); apiclient/ui/OpenInApiStudioAction.java
        // (Open in API Studio, which is where a request is sent)
        rows(t, builtIn(N_REST_CLIENT, Door.API_STUDIO), "humao.rest-client");
        // editor/format/FormatWithPrettierAction.java (on demand, the editor's
        // right-click menu) and FormatOnSave.java (on save, only when the project
        // has its own Prettier configuration and the Options toggle is on)
        rows(t, builtIn(N_PRETTIER, Door.FORMAT_WITH_PRETTIER), "esbenp.prettier-vscode");
        // editor/debug/BrowserDebugAction.java: the vendored js-debug launches
        // Chrome (BrowserLocator.java: Chrome, then Edge, then Chromium) from an
        // HTML, JavaScript or TypeScript file's right-click menu
        rows(t, builtIn(N_CHROME_DEBUG), "msjsdiag.debugger-for-chrome");
        // editor/design/CssColorHighlighter.java (swatches) and CssColorHyperlink.java
        // (the picker), in CSS, SCSS, Less, Sass and HTML style regions
        rows(t, builtIn(N_COLOURS), "naumovs.color-highlight");
        // editor/design/CssClassCompletionProvider.java (class="…" completes from the
        // project's stylesheets) and CssClassHyperlink.java (jump to the rule)
        rows(t, builtIn(N_CSS_CLASSES), "ecmel.vscode-html-css", "zignd.html-css-class-completion",
                "pranaygp.vscode-css-peek");
        // rack/service/PullRequestsAction.java: Team menu and the git chip, through
        // the user's own gh
        rows(t, builtIn(K_BUILT_IN, Door.PULL_REQUESTS), "github.vscode-pull-request-github");
        // ui/actions/NgSchematicAction.java: ng generate as a dialog
        rows(t, builtIn(K_BUILT_IN, Door.NG_SCHEMATIC), "cyrilletuzi.angular-schematics");
        // editor/sass/SassCompileAction.java: compiles to the sibling .css and
        // recompiles on save
        rows(t, builtIn(K_BUILT_IN, Door.COMPILE_TO_CSS), "glenn2223.live-sass", "ritwickdey.live-sass");

        // ---- a window -------------------------------------------------------
        // rack/docker/DockerPanelTopComponent.java
        rows(t, window(Door.DOCKER_PANEL), "ms-azuretools.vscode-docker", "ms-azuretools.vscode-containers");
        // dbstudio/ui/DbStudioTopComponent.java; engines in dbstudio/model/DbEngine.java:
        // SQLite, PostgreSQL, MySQL, MariaDB, MongoDB, CouchDB
        rows(t, window(Door.DB_STUDIO), "mtxr.sqltools", "cweijan.vscode-database-client2",
                "cweijan.vscode-mysql-client2", "mongodb.mongodb-vscode", "alexcvzz.vscode-sqlite",
                "qwtel.sqlite-viewer", "ckolkman.vscode-postgres");
        // apiclient/ui/ApiClientTopComponent.java
        rows(t, window(Door.API_STUDIO), "rangav.vscode-thunder-client", "postman.postman-for-vscode");
        // web3/ui/Web3StudioTopComponent.java (Foundry and Hardhat artifacts)
        rows(t, window(Door.CONTRACT_STUDIO), "juanblanco.solidity", "nomicfoundation.hardhat-solidity");
        // editor/testing/explorer/TestsExplorerTopComponent.java over TestIndex.java;
        // RunFocusedTestAction.java runs jest and vitest
        rows(t, window(Door.TESTS), "orta.vscode-jest", "vitest.explorer", "firsttris.vscode-jest-runner",
                "hbenl.vscode-test-explorer");

        // ---- a rack device --------------------------------------------------
        // rack/devices/SpecterDevice.java (DeviceType.E2E): Playwright run, report, codegen
        rows(t, device(DeviceType.E2E), "ms-playwright.playwright");
        // rack/devices/LintDevice.java and FormatDevice.java: a biome.json turns
        // both lanes to biome
        rows(t, device(DeviceType.LINT, DeviceType.FORMAT), "biomejs.biome");

        // ---- a language server the editor starts ----------------------------
        // every binary below is launched in editor/lsp/LanguageServers.java and is
        // held by ServerTrust.java; ExtensionEquivalentsTest reads both files
        rows(t, server("vscode-eslint-language-server"), "dbaeumer.vscode-eslint");
        rows(t, server("stylelint-lsp"), "stylelint.vscode-stylelint");
        // Pylance is built on Pyright, the server this product starts
        rows(t, server("pyright-langserver"), "ms-python.python", "ms-python.vscode-pylance");
        rows(t, server("gopls"), "golang.go");
        rows(t, server("rust-analyzer"), "rust-lang.rust-analyzer");
        rows(t, server("ngserver"), "angular.ng-template");
        rows(t, server("vue-language-server"), "vue.volar", "octref.vetur");
        rows(t, server("svelteserver"), "svelte.svelte-vscode");
        rows(t, server("deno"), "denoland.vscode-deno");
        rows(t, server("yaml-language-server"), "redhat.vscode-yaml");
        // Even Better TOML is the taplo project's own extension
        rows(t, server("taplo"), "tamasfe.even-better-toml");
        rows(t, server("clangd"), "ms-vscode.cpptools", "llvm-vs-code-extensions.vscode-clangd");
        rows(t, server("elixir-ls"), "jakebecker.elixir-ls", "elixir-lsp.vscode-elixir-ls", "elixir-lsp.elixir-ls");
        rows(t, server("dart"), "dart-code.dart-code");
        rows(t, server("jdtls"), "redhat.java", "vscjava.vscode-java-pack");
        rows(t, server("csharp-ls"), "ms-dotnettools.csharp", "ms-dotnettools.csdevkit");
        rows(t, server("fsautocomplete"), "ionide.ionide-fsharp");
        rows(t, server("astro-ls"), "astro-build.astro-vscode");
        rows(t, server("prisma-language-server"), "prisma.prisma");
        rows(t, server("graphql-lsp"), "graphql.vscode-graphql");
        rows(t, server("ruby-lsp"), "shopify.ruby-lsp");
        rows(t, server("solargraph"), "castwide.solargraph");
        rows(t, server("intelephense"), "bmewburn.vscode-intelephense-client");
        rows(t, server("lua-language-server"), "sumneko.lua");
        rows(t, server("metals"), "scalameta.metals");
        rows(t, server("haskell-language-server-wrapper"), "haskell.haskell");
        rows(t, server("zls"), "ziglang.vscode-zig");
        rows(t, server("kotlin-language-server"), "fwcd.kotlin");
        rows(t, server("sourcekit-lsp"), "swiftlang.swift-vscode", "sswg.swift-lang");
        rows(t, server("elm-language-server"), "elmtooling.elm-ls-vscode");
        rows(t, server("ocamllsp"), "ocamllabs.ocaml-platform");
        rows(t, server("bash-language-server"), "mads-hartmann.bash-ide-vscode");

        // ---- syntax colouring, and no more ----------------------------------
        // the platform's languages-hcl module (ide cluster) registers
        // text/x-terraform+x-hcl; the editor starts no Terraform server
        rows(t, SYNTAX, "hashicorp.terraform");
        // editor/grammars/GraphqlGrammar.java (the extension is itself syntax only)
        rows(t, SYNTAX, "graphql.vscode-graphql-syntax");
        // editor/grammars/ProtoGrammar.java
        rows(t, SYNTAX, "zxh404.vscode-proto3");
        // editor/grammars/SassGrammar.java
        rows(t, SYNTAX, "syler.sass-indented");

        // ---- the AI device --------------------------------------------------
        // rack/service/AskKvasirAction.java, EditWithKvasirAction.java,
        // editor/ghost/CompleteWithKvasirAction.java; rack/engine/KvasirProvider.java
        // (Claude, ChatGPT, Gemini). No always-on stream:
        // docs/engineering/competitive-lens.md, R4.
        rows(t, note(Kind.AI, N_AI), "github.copilot", "github.copilot-chat", "continue.continue",
                "codeium.codeium", "tabnine.tabnine-vscode", "sourcegraph.cody-ai", "supermaven.supermaven",
                "visualstudioexptteam.vscodeintellicode");

        // ---- not applicable here --------------------------------------------
        // a VS Code colour or icon theme is a VS Code file format; nothing here loads one
        rows(t, note(Kind.NOT_APPLICABLE, N_THEME), "pkief.material-icon-theme", "vscode-icons-team.vscode-icons",
                "dracula-theme.theme-dracula", "zhuangtongfa.material-theme", "github.github-vscode-theme",
                "enkia.tokyo-night", "catppuccin.catppuccin-vsc", "catppuccin.catppuccin-vsc-icons",
                "sdras.night-owl", "equinusocio.vsc-material-theme");
        // the platform's keymap profiles (NetBeans, Eclipse, Emacs, Idea, NetBeans55:
        // ui KeymapProfileParityTest); there is no Sublime or Atom profile
        rows(t, note(Kind.NOT_APPLICABLE, N_KEYMAP), "ms-vscode.sublime-keybindings", "ms-vscode.atom-keybindings",
                "k--kato.intellij-idea-keybindings", "alphabotsec.vscode-eclipse-keybindings",
                "tuttieee.emacs-mcx", "lfs.vscode-emacs-friendly");
        // no modal editing anywhere in the editor module
        rows(t, note(Kind.NOT_APPLICABLE, N_VIM), "vscodevim.vim", "asvetliakov.vscode-neovim");

        // ---- known, and cannot work here ------------------------------------
        // docs/engineering/tech-debt.md, ledger 45: the Tailwind server needs
        // client/registerCapability, which the platform's LSP client does not answer
        rows(t, note(Kind.NOT_AVAILABLE, N_TAILWIND), "bradlc.vscode-tailwindcss");

        // ---- no equivalent --------------------------------------------------
        // editor/debug/BrowserLocator.java finds Chrome, Edge and Chromium only
        rows(t, NONE, "firefox-devtools.vscode-firefox-debug");
        // nothing opens a folder in a container, over SSH or in WSL, shares a
        // session, or runs a notebook (no devcontainer, liveshare or ipynb anywhere
        // in the sources)
        rows(t, NONE, "ms-vscode-remote.remote-containers", "ms-vscode-remote.remote-ssh",
                "ms-vscode-remote.remote-wsl", "ms-vscode-remote.vscode-remote-extensionpack",
                "ms-vsliveshare.vsliveshare", "ms-toolsai.jupyter");
        // nothing here is a coding agent. rack/mcp/AgentPortAction.java opens a
        // read-only MCP endpoint (McpReadOnlyLedgerTest: no tool writes or spawns)
        // that Claude Code, running in its own terminal, can connect to and read
        // the IDE through; it cannot edit, run or decide anything there
        rows(t, new Equivalent(Kind.NO_EQUIVALENT, N_CLAUDE_CODE, Door.AGENT_PORT, null, List.of()),
                "anthropic.claude-code");
        return List.copyOf(t);
    }

    /** What covers {@code entry}; a malformed entry and an id not in the table are both not known. */
    static Equivalent of(VsCodeExtensions.Entry entry) {
        if (entry == null || !entry.wellFormed()) {
            return UNKNOWN;
        }
        return BY_ID.getOrDefault(entry.id(), UNKNOWN);
    }

    /**
     * What the running product said, gathered before a row is rendered.
     *
     * @param doorNames     each door's own display name; a door whose action
     *                      did not resolve is absent
     * @param serverCatalog whether the editor's catalog was there to ask
     * @param servers       the catalog's answer per binary; a binary it does
     *                      not start is absent
     */
    record Facts(Map<Door, String> doorNames, boolean serverCatalog, Map<String, ServerCatalog.Server> servers) {
    }

    /**
     * The sentence for one row. A row that names something the running
     * product does not have (a window whose module is absent, a server the
     * editor says it does not start) says exactly that, never the claim.
     */
    static String sentence(Equivalent e, Facts facts) {
        String doorName = e.door() == null ? null : facts.doorNames().get(e.door());
        return switch (e.kind()) {
            case UNKNOWN, SYNTAX, AI, NOT_APPLICABLE, NOT_AVAILABLE -> message(e.key());
            // a row may name the nearest thing there is; without it the row is plainly "no equivalent"
            case NO_EQUIVALENT -> e.door() == null ? message(e.key())
                    : doorName == null ? message(K_NO_EQUIVALENT) : message(e.key(), doorName);
            case BUILT_IN -> e.door() == null ? message(e.key())
                    : doorName == null ? message(K_ABSENT) : message(e.key(), doorName);
            case WINDOW -> doorName == null ? message(K_ABSENT) : message(e.key(), doorName);
            case DEVICE -> doorName == null ? message(K_ABSENT)
                    : message(e.key(), e.devices().stream().map(DeviceType::getTitle).collect(Collectors.joining(", ")),
                            doorName, e.devices().size());
            case SERVER -> serverSentence(e.server(), facts);
        };
    }

    private static String serverSentence(String binary, Facts facts) {
        if (!facts.serverCatalog()) {
            return message(K_SERVER, binary);
        }
        ServerCatalog.Server known = facts.servers().get(binary);
        if (known == null) {
            return message(K_ABSENT);
        }
        if (known.installed()) {
            return message(K_SERVER_INSTALLED, binary);
        }
        return known.install() == null || known.install().isBlank() ? message(K_SERVER_MISSING, binary)
                : message(K_SERVER_MISSING_HINT, binary, known.install());
    }

    /** The door the sheet's Open button runs for this row, or null when it has none to run. */
    static Door opens(Equivalent e, Facts facts) {
        Door door = e.door();
        if (door == null || !door.opens || !facts.doorNames().containsKey(door)) {
            return null;
        }
        if (e.kind() == Kind.SERVER && facts.serverCatalog() && !facts.servers().containsKey(e.server())) {
            return null;
        }
        return door;
    }

    private static String message(String key, Object... args) {
        return args.length == 0 ? NbBundle.getMessage(ExtensionEquivalents.class, key)
                : NbBundle.getMessage(ExtensionEquivalents.class, key, args);
    }
}
