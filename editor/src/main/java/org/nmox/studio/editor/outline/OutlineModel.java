package org.nmox.studio.editor.outline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The structure of a file, extracted without an AST. Our languages ride
 * TextMate, which colours but does not parse, so the outline is built
 * from line-oriented heuristics tuned per language family. Pure and
 * unit-testable: a function of (mime, text), no editor required.
 *
 * Nesting is honest about what each family affords - brace depth for
 * C-like and CSS, indentation for Python, Elixir and YAML, heading
 * level for Markdown, section headers for INI/TOML, a flat list where
 * nesting would be a lie (Lisp parens, Erlang clauses). The depths
 * only need to be consistent within a file; the navigator's tree
 * builder turns them into parent/child links with a stack.
 */
public final class OutlineModel {

    /** One named thing in the file. line is 0-based; depth nests the tree. */
    public record Item(OutlineKind kind, String name, String detail, int line, int depth) {
    }

    /** Files larger than this are scanned only to here - the outline of a
     * generated megafile is not worth a UI stall. */
    private static final int MAX_LINES = 50_000;

    private OutlineModel() {
    }

    public static List<Item> extract(String mime, CharSequence text) {
        if (mime == null || text == null) {
            return List.of();
        }
        String[] lines = splitLines(text);
        return switch (family(mime)) {
            case "js" -> js(lines);
            case "css" -> css(lines);
            case "markdown" -> markdown(lines);
            case "json" -> json(lines);
            case "yaml" -> yaml(lines);
            case "toml" -> toml(lines);
            case "ini" -> ini(lines);
            case "html" -> html(lines);
            case "python" -> python(lines);
            case "rust" -> rust(lines);
            case "go" -> go(lines);
            case "elixir" -> elixir(lines);
            case "clojure" -> clojure(lines);
            case "erlang" -> erlang(lines);
            case "haskell" -> haskell(lines);
            case "ocaml" -> ocaml(lines);
            case "r" -> r(lines);
            case "perl" -> perl(lines);
            case "julia" -> julia(lines);
            case "fsharp" -> fsharp(lines);
            case "crystal" -> crystal(lines);
            case "zig" -> zig(lines);
            case "solidity" -> solidity(lines);
            case "brace" -> braceLang(lines);
            case "shell" -> shell(lines);
            case "graphql" -> graphql(lines);
            case "sql" -> sql(lines);
            case "make" -> makefile(lines);
            case "proto" -> proto(lines);
            default -> generic(lines);
        };
    }

    static String family(String mime) {
        return switch (mime) {
            case "text/javascript", "text/typescript", "text/jsx", "text/tsx",
                 "text/x-vue", "text/x-svelte", "text/x-astro" -> "js";
            case "text/css", "text/x-scss", "text/x-less" -> "css";
            case "text/x-markdown", "text/markdown" -> "markdown";
            case "text/x-json", "application/json" -> "json";
            case "text/x-yaml", "application/x-yaml" -> "yaml";
            case "text/x-toml" -> "toml";
            case "text/x-ini" -> "ini";
            case "text/html" -> "html";
            case "text/x-python" -> "python";
            case "text/x-rust" -> "rust";
            case "text/x-go" -> "go";
            case "text/x-elixir" -> "elixir";
            case "text/x-clojure" -> "clojure";
            case "text/x-erlang" -> "erlang";
            case "text/x-haskell" -> "haskell";
            case "text/x-ocaml" -> "ocaml";
            case "text/x-r" -> "r";
            case "text/x-perl" -> "perl";
            case "text/x-julia" -> "julia";
            case "text/x-fsharp" -> "fsharp";
            case "text/x-crystal" -> "crystal";
            case "text/x-zig" -> "zig";
            case "text/x-solidity" -> "solidity";
            case "text/x-java", "text/x-kotlin", "text/x-scala", "text/x-csharp",
                 "text/x-swift", "text/x-c", "text/x-cpp", "text/x-dart",
                 "text/x-groovy", "text/x-php5" -> "brace";
            case "text/sh" -> "shell";
            case "text/x-graphql" -> "graphql";
            case "text/x-sql" -> "sql";
            case "text/x-makefile" -> "make";
            case "text/x-protobuf" -> "proto";
            default -> "generic";
        };
    }

    /** No real declaration's name lives past this column. */
    private static final int MAX_LINE_LEN = 2_000;

    static String[] splitLines(CharSequence text) {
        String s = text.toString();
        // keep it bounded; split is fine for normal source sizes
        String[] lines = s.split("\n", -1);
        // Bound each line's length before the symbol regexes see it: a
        // pathologically long line could make a grammar regex backtrack
        // badly (ReDoS), and truncating well past any real declaration is
        // behaviour-preserving, so matching stays linear-time in practice.
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > MAX_LINE_LEN) {
                lines[i] = lines[i].substring(0, MAX_LINE_LEN);
            }
        }
        return lines;
    }

    // ---- JS / TS family --------------------------------------------------

    private static final Pattern JS_CLASS = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z0-9_$]+)");
    private static final Pattern JS_IFACE = Pattern.compile(
            "^\\s*(?:export\\s+)?interface\\s+([A-Za-z0-9_$]+)");
    private static final Pattern JS_TYPE = Pattern.compile(
            "^\\s*(?:export\\s+)?type\\s+([A-Za-z0-9_$]+)\\s*[=<]");
    private static final Pattern JS_ENUM = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:const\\s+)?enum\\s+([A-Za-z0-9_$]+)");
    private static final Pattern JS_FUNC = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s*\\*?\\s*([A-Za-z0-9_$]+)");
    private static final Pattern JS_ARROW = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:const|let|var)\\s+([A-Za-z0-9_$]+)\\s*(?::[^=]+)?=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z0-9_$]+)\\s*(?::[^=]+)?=>");
    private static final Pattern JS_METHOD = Pattern.compile(
            "^\\s+(?:public\\s+|private\\s+|protected\\s+|readonly\\s+)*(?:static\\s+)?(?:async\\s+)?(?:get\\s+|set\\s+|\\*\\s*)?([A-Za-z0-9_$]+)\\s*\\([^;]*\\)\\s*(?::[^={]+)?\\{");
    private static final Pattern JS_TEST = Pattern.compile(
            "^\\s*(?:describe|it|test|context|suite)\\s*(?:\\.\\w+)?\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]");
    private static final java.util.Set<String> JS_KEYWORDS = java.util.Set.of(
            "if", "for", "while", "switch", "catch", "return", "function", "constructor",
            "do", "else", "try", "finally", "await", "yield", "typeof", "new");

    private static List<Item> js(String[] lines) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        // carries across lines: are we inside a /* */ block comment or a
        // `backtick` template literal? Without this, a declaration that only
        // looks like one inside a comment or a multi-line template string
        // (e.g. an embedded SQL/HTML heredoc) is mis-reported as a symbol.
        boolean[] state = {false, false};
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String code = stripNonCode(lines[i], state);
            int depthHere = brace;
            Matcher m;
            if ((m = JS_TEST.matcher(code)).find()) {
                out.add(new Item(OutlineKind.TEST, m.group(1), null, i, depthHere));
            } else if ((m = JS_CLASS.matcher(code)).find()) {
                out.add(new Item(OutlineKind.CLASS, m.group(1), null, i, depthHere));
            } else if ((m = JS_IFACE.matcher(code)).find()) {
                out.add(new Item(OutlineKind.INTERFACE, m.group(1), null, i, depthHere));
            } else if ((m = JS_ENUM.matcher(code)).find()) {
                out.add(new Item(OutlineKind.ENUM, m.group(1), null, i, depthHere));
            } else if ((m = JS_TYPE.matcher(code)).find()) {
                out.add(new Item(OutlineKind.TYPE, m.group(1), null, i, depthHere));
            } else if ((m = JS_FUNC.matcher(code)).find()) {
                out.add(new Item(OutlineKind.FUNCTION, m.group(1), null, i, depthHere));
            } else if ((m = JS_ARROW.matcher(code)).find()) {
                out.add(new Item(OutlineKind.FUNCTION, m.group(1), null, i, depthHere));
            } else if (depthHere > 0 && (m = JS_METHOD.matcher(code)).find()
                    && !JS_KEYWORDS.contains(m.group(1))) {
                out.add(new Item(OutlineKind.METHOD, m.group(1), null, i, depthHere));
            }
            brace += netBraces(code);
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    /**
     * Returns the line with block comments, line comments and backtick
     * template literals blanked out, carrying block-comment / template state
     * across lines via {@code state} ([0]=inBlockComment, [1]=inTemplate).
     * Regular "…"/'…' strings are left alone — a declaration keyword inside a
     * single-line string is rare and self-correcting on the next line.
     */
    static String stripNonCode(String line, boolean[] state) {
        StringBuilder code = new StringBuilder(line.length());
        int j = 0;
        while (j < line.length()) {
            if (state[0]) { // inside /* */
                int end = line.indexOf("*/", j);
                if (end < 0) {
                    return code.toString();
                }
                state[0] = false;
                j = end + 2;
            } else if (state[1]) { // inside `template`
                int end = line.indexOf('`', j);
                if (end < 0) {
                    return code.toString();
                }
                state[1] = false;
                j = end + 1;
            } else {
                char c = line.charAt(j);
                char next = j + 1 < line.length() ? line.charAt(j + 1) : '\0';
                if (c == '/' && next == '/') {
                    break; // rest of the line is a comment
                } else if (c == '/' && next == '*') {
                    state[0] = true;
                    j += 2;
                } else if (c == '`') {
                    state[1] = true;
                    j++;
                } else {
                    code.append(c);
                    j++;
                }
            }
        }
        return code.toString();
    }

    // ---- CSS / SCSS / LESS ----------------------------------------------

    private static final Pattern CSS_AT = Pattern.compile(
            "^\\s*(@(?:media|supports|mixin|include|function|keyframes|font-face|page)\\b[^{]*)\\{");
    private static final Pattern CSS_SEL = Pattern.compile("^\\s*([^{}@;]+?)\\s*\\{\\s*$");

    private static List<Item> css(String[] lines) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        boolean inComment = false;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            String code = line;
            if (inComment) {
                int close = code.indexOf("*/");
                if (close < 0) {
                    continue;
                }
                code = code.substring(close + 2);
                inComment = false;
            }
            int open = code.indexOf("/*");
            if (open >= 0 && code.indexOf("*/", open) < 0) {
                inComment = true;
                code = code.substring(0, open);
            }
            int depthHere = brace;
            Matcher m;
            if ((m = CSS_AT.matcher(code)).find()) {
                out.add(new Item(OutlineKind.RULE, m.group(1).trim(), null, i, depthHere));
            } else if ((m = CSS_SEL.matcher(code)).find()) {
                String sel = m.group(1).trim();
                if (!sel.isEmpty() && !sel.contains(":") || sel.contains("&") || sel.matches(".*[.#\\[].*")) {
                    out.add(new Item(OutlineKind.SELECTOR, sel, null, i, depthHere));
                }
            }
            brace += countChar(code, '{') - countChar(code, '}');
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    // ---- Markdown --------------------------------------------------------

    private static final Pattern MD_ATX = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");

    private static List<Item> markdown(String[] lines) {
        List<Item> out = new ArrayList<>();
        boolean fenced = false;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            String t = line.stripLeading();
            if (t.startsWith("```") || t.startsWith("~~~")) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                continue;
            }
            Matcher m = MD_ATX.matcher(line);
            if (m.find()) {
                int level = m.group(1).length();
                String name = m.group(2).trim();
                if (!name.isEmpty()) {
                    out.add(new Item(OutlineKind.HEADING, name, "h" + level, i, level - 1));
                }
            }
        }
        return out;
    }

    // ---- JSON ------------------------------------------------------------

    private static final Pattern JSON_KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");

    private static List<Item> json(String[] lines) {
        List<Item> out = new ArrayList<>();
        int nest = 0;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            int depthHere = Math.max(0, nest - 1);
            Matcher m = JSON_KEY.matcher(line);
            if (m.find() && depthHere <= 1) {
                out.add(new Item(OutlineKind.KEY, m.group(1), null, i, depthHere));
            }
            nest += countChar(line, '{') + countChar(line, '[')
                    - countChar(line, '}') - countChar(line, ']');
            if (nest < 0) {
                nest = 0;
            }
        }
        return out;
    }

    // ---- YAML ------------------------------------------------------------

    private static final Pattern YAML_KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+)\\s*:(?:\\s|$)");

    private static List<Item> yaml(String[] lines) {
        List<Item> out = new ArrayList<>();
        Deque<Integer> cols = new ArrayDeque<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            if (line.stripLeading().startsWith("#") || line.isBlank()) {
                continue;
            }
            Matcher m = YAML_KEY.matcher(line);
            if (m.find()) {
                int col = m.group(1).length();
                while (!cols.isEmpty() && cols.peek() >= col) {
                    cols.pop();
                }
                int depth = cols.size();
                cols.push(col);
                out.add(new Item(OutlineKind.KEY, m.group(2), null, i, depth));
            }
        }
        return out;
    }

    // ---- TOML ------------------------------------------------------------

    private static final Pattern TOML_SECTION = Pattern.compile("^\\s*(\\[\\[?[^\\]]+\\]\\]?)");

    private static List<Item> toml(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = TOML_SECTION.matcher(lines[i]);
            if (m.find()) {
                out.add(new Item(OutlineKind.SECTION, m.group(1).trim(), null, i, 0));
            }
        }
        return out;
    }

    // ---- INI -------------------------------------------------------------

    private static final Pattern INI_SECTION = Pattern.compile("^\\s*\\[([^\\]]+)\\]");

    private static List<Item> ini(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = INI_SECTION.matcher(lines[i]);
            if (m.find()) {
                out.add(new Item(OutlineKind.SECTION, m.group(1).trim(), null, i, 0));
            }
        }
        return out;
    }

    // ---- HTML ------------------------------------------------------------

    private static final Pattern HTML_LANDMARK = Pattern.compile(
            "<(header|nav|main|section|article|aside|footer|form|table|script|style|template)\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ID = Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern HTML_HEADING = Pattern.compile(
            "<(h[1-6])\\b[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE);

    private static List<Item> html(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher h = HTML_HEADING.matcher(line);
            while (h.find()) {
                String txt = h.group(2).replaceAll("<[^>]+>", "").trim();
                out.add(new Item(OutlineKind.HEADING, txt.isEmpty() ? h.group(1) : txt,
                        h.group(1).toLowerCase(), i, 0));
            }
            Matcher m = HTML_LANDMARK.matcher(line);
            while (m.find()) {
                String tag = m.group(1).toLowerCase();
                Matcher id = HTML_ID.matcher(m.group(2));
                String name = id.find() ? tag + " #" + id.group(1) : tag;
                out.add(new Item(OutlineKind.SECTION, name, null, i, 0));
            }
        }
        return out;
    }

    // ---- Python ----------------------------------------------------------

    private static final Pattern PY = Pattern.compile(
            "^(\\s*)(?:async\\s+)?(def|class)\\s+([A-Za-z0-9_]+)");

    private static List<Item> python(String[] lines) {
        List<Item> out = new ArrayList<>();
        Deque<Integer> cols = new ArrayDeque<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = PY.matcher(lines[i]);
            if (m.find()) {
                int col = m.group(1).length();
                while (!cols.isEmpty() && cols.peek() >= col) {
                    cols.pop();
                }
                int depth = cols.size();
                cols.push(col);
                OutlineKind kind = m.group(2).equals("class") ? OutlineKind.CLASS
                        : depth > 0 ? OutlineKind.METHOD : OutlineKind.FUNCTION;
                out.add(new Item(kind, m.group(3), null, i, depth));
            }
        }
        return out;
    }

    // ---- Rust ------------------------------------------------------------

    private static final Pattern RUST = Pattern.compile(
            "^\\s*(?:pub(?:\\([^)]*\\))?\\s+)?(?:async\\s+)?(?:unsafe\\s+)?(fn|struct|enum|trait|impl|mod|type|const|static)\\s+([A-Za-z0-9_]+)");

    private static List<Item> rust(String[] lines) {
        return braceKeyword(lines, RUST, m -> switch (m.group(1)) {
            case "fn" -> OutlineKind.FUNCTION;
            case "struct" -> OutlineKind.TYPE;
            case "enum" -> OutlineKind.ENUM;
            case "trait" -> OutlineKind.INTERFACE;
            case "impl", "mod" -> OutlineKind.MODULE;
            case "type" -> OutlineKind.TYPE;
            default -> OutlineKind.FIELD;
        }, 2);
    }

    // ---- Go --------------------------------------------------------------

    private static final Pattern GO = Pattern.compile(
            "^\\s*(?:func\\s*(?:\\([^)]*\\)\\s*)?([A-Za-z0-9_]+)|type\\s+([A-Za-z0-9_]+)\\s+(?:struct|interface)\\b)");

    private static List<Item> go(String[] lines) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher m = GO.matcher(line);
            if (m.find()) {
                if (m.group(1) != null) {
                    out.add(new Item(OutlineKind.FUNCTION, m.group(1), null, i, brace));
                } else if (m.group(2) != null) {
                    out.add(new Item(OutlineKind.TYPE, m.group(2), null, i, brace));
                }
            }
            brace += netBraces(line);
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    // ---- Elixir ------------------------------------------------------------

    private static final Pattern ELIXIR = Pattern.compile(
            "^(\\s*)(defmodule|defmacrop?|defp?)\\s+([A-Za-z0-9_.?!]+)");

    private static List<Item> elixir(String[] lines) {
        List<Item> out = new ArrayList<>();
        Deque<Integer> cols = new ArrayDeque<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = ELIXIR.matcher(lines[i]);
            if (m.find()) {
                int col = m.group(1).length();
                while (!cols.isEmpty() && cols.peek() >= col) {
                    cols.pop();
                }
                int depth = cols.size();
                cols.push(col);
                OutlineKind kind = m.group(2).equals("defmodule") ? OutlineKind.MODULE
                        : depth > 0 ? OutlineKind.METHOD : OutlineKind.FUNCTION;
                out.add(new Item(kind, m.group(3), null, i, depth));
            }
        }
        return out;
    }

    // ---- Clojure -----------------------------------------------------------

    private static final Pattern CLOJURE = Pattern.compile(
            "^\\((ns|defprotocol|defrecord|deftype|defmacro|defn-?|def)\\s+([A-Za-z0-9_*+!?<>=.'/-]+)");

    /** Top-level forms only; Lisp nesting by parens is not this outline's business. */
    private static List<Item> clojure(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = CLOJURE.matcher(lines[i]);
            if (m.find()) {
                OutlineKind kind = switch (m.group(1)) {
                    case "ns" -> OutlineKind.MODULE;
                    case "defprotocol" -> OutlineKind.INTERFACE;
                    case "defrecord", "deftype" -> OutlineKind.TYPE;
                    case "def" -> OutlineKind.FIELD;
                    default -> OutlineKind.FUNCTION;
                };
                out.add(new Item(kind, m.group(2), m.group(1), i, 0));
            }
        }
        return out;
    }

    // ---- Erlang ------------------------------------------------------------

    private static final Pattern ERLANG_ATTR = Pattern.compile(
            "^-(module|behaviou?r|export)\\s*\\(\\s*([^)]*?)\\s*\\)");
    private static final Pattern ERLANG_FUN = Pattern.compile(
            "^([a-z][A-Za-z0-9_@]*)\\(([^)]*)\\)\\s*(?:when\\b[^-]*)?->");

    /** Module attributes plus function clause heads at column 0. */
    private static List<Item> erlang(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher a = ERLANG_ATTR.matcher(line);
            if (a.find()) {
                String attr = a.group(1);
                String value = a.group(2).replaceAll("[\\[\\]]", "").trim();
                Item item = switch (attr) {
                    case "module" -> new Item(OutlineKind.MODULE, value, null, i, 0);
                    case "export" -> new Item(OutlineKind.SECTION, "export", trim(value, 40), i, 0);
                    default -> new Item(OutlineKind.INTERFACE, value, "behaviour", i, 0);
                };
                out.add(item);
                continue;
            }
            Matcher f = ERLANG_FUN.matcher(line);
            if (f.find()) {
                out.add(new Item(OutlineKind.FUNCTION, f.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- Haskell -----------------------------------------------------------

    // The file's own module header: `module Rack.Wire (exports) where`.
    private static final Pattern HASKELL_MODULE = Pattern.compile(
            "^module\\s+([A-Z][A-Za-z0-9_.']*)");
    // A top-level type signature: `name :: Type`. Only at column 0.
    private static final Pattern HASKELL_SIG = Pattern.compile(
            "^([a-z_][A-Za-z0-9_']*)\\s*::");
    // A top-level value/function binding: `name args = ...` at column 0. The
    // guard against `::`/`=` in the name keeps signatures out of this branch.
    private static final Pattern HASKELL_BIND = Pattern.compile(
            "^([a-z_][A-Za-z0-9_']*)\\b[^=:]*=(?!=)");
    // data / newtype / type / class / instance declarations at column 0.
    private static final Pattern HASKELL_DECL = Pattern.compile(
            "^(data|newtype|type|class|instance)\\s+(?:[^=>]*?=>\\s*)?([A-Z][A-Za-z0-9_']*)");

    /** Column-0 module header, signatures, bindings and data/class/instance
     * declarations. A binding is only surfaced once - the first clause wins -
     * so multi-clause pattern-match functions don't spam the outline. */
    private static List<Item> haskell(String[] lines) {
        List<Item> out = new ArrayList<>();
        java.util.Set<String> seenBind = new java.util.HashSet<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t'
                    || line.startsWith("--")) {
                continue;
            }
            Matcher mod = HASKELL_MODULE.matcher(line);
            if (mod.find()) {
                out.add(new Item(OutlineKind.MODULE, mod.group(1), null, i, 0));
                continue;
            }
            Matcher d = HASKELL_DECL.matcher(line);
            if (d.find()) {
                OutlineKind kind = d.group(1).equals("class") ? OutlineKind.INTERFACE
                        : OutlineKind.TYPE;
                out.add(new Item(kind, d.group(2), d.group(1), i, 0));
                continue;
            }
            Matcher s = HASKELL_SIG.matcher(line);
            if (s.find()) {
                out.add(new Item(OutlineKind.FIELD, s.group(1), "::", i, 0));
                continue;
            }
            Matcher b = HASKELL_BIND.matcher(line);
            if (b.find() && seenBind.add(b.group(1))) {
                out.add(new Item(OutlineKind.FUNCTION, b.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- OCaml -------------------------------------------------------------

    // `module type` must precede `module` in the alternation or the sig name
    // would be swallowed; the optional group skips type params ('a, ('a, 'b))
    // so `type 'a slot` names slot, not its variable.
    private static final Pattern OCAML = Pattern.compile(
            "^(let\\s+rec|let|and|type|module\\s+type|module|exception|val|class)\\s+"
            + "(?:(?:'[A-Za-z0-9_]+|\\([^)]*\\))\\s+)*([A-Za-z_][A-Za-z0-9_']*)");

    /** let / let rec bindings, type/module/exception/val/class declarations
     * at column 0. `and` continuations reuse the preceding kind's family. */
    private static List<Item> ocaml(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                continue;
            }
            Matcher m = OCAML.matcher(line);
            if (m.find()) {
                String name = m.group(2);
                if ("_".equals(name)) {
                    continue; // `let _ = ...` is a discard, not a symbol
                }
                String kw = m.group(1).replaceAll("\\s+", " ");
                OutlineKind kind = switch (kw) {
                    case "type" -> OutlineKind.TYPE;
                    case "module", "module type" -> OutlineKind.MODULE;
                    case "class" -> OutlineKind.CLASS;
                    case "exception" -> OutlineKind.ENUM;
                    case "val" -> OutlineKind.FIELD;
                    default -> OutlineKind.FUNCTION; // let / let rec / and
                };
                out.add(new Item(kind, name, null, i, 0));
            }
        }
        return out;
    }

    // ---- R -----------------------------------------------------------------

    // `name <- function(...)` or `name = function(...)`. Flat list of defs.
    private static final Pattern R_FUNC = Pattern.compile(
            "^\\s*([A-Za-z.][A-Za-z0-9._]*)\\s*(?:<-|=)\\s*function\\b");
    // S4 registrations: setClass("Name", ...) and setGeneric("name", ...).
    private static final Pattern R_S4 = Pattern.compile(
            "^\\s*(setClass|setGeneric)\\s*\\(\\s*[\"']([^\"']+)[\"']");

    private static List<Item> r(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher s4 = R_S4.matcher(lines[i]);
            if (s4.find()) {
                OutlineKind kind = s4.group(1).equals("setClass")
                        ? OutlineKind.CLASS : OutlineKind.FUNCTION;
                out.add(new Item(kind, s4.group(2), s4.group(1), i, 0));
                continue;
            }
            Matcher m = R_FUNC.matcher(lines[i]);
            if (m.find()) {
                out.add(new Item(OutlineKind.FUNCTION, m.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- Perl --------------------------------------------------------------

    private static final Pattern PERL_SUB = Pattern.compile(
            "^\\s*sub\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern PERL_PKG = Pattern.compile(
            "^\\s*package\\s+([A-Za-z_][A-Za-z0-9_:]*)");

    /** sub declarations and package statements - flat. */
    private static List<Item> perl(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher p = PERL_PKG.matcher(line);
            if (p.find()) {
                out.add(new Item(OutlineKind.MODULE, p.group(1), null, i, 0));
                continue;
            }
            Matcher s = PERL_SUB.matcher(line);
            if (s.find()) {
                out.add(new Item(OutlineKind.FUNCTION, s.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- Julia -------------------------------------------------------------

    private static final Pattern JULIA = Pattern.compile(
            "^(\\s*)(function|struct|mutable\\s+struct|module|macro|abstract\\s+type|primitive\\s+type)\\s+([A-Za-z_][A-Za-z0-9_!]*)");
    // Short-form definition at column 0: `gain(x) = 2 * x`. The (?!=) keeps
    // comparisons (`==`) from reading as definitions.
    private static final Pattern JULIA_SHORT = Pattern.compile(
            "^([a-z_][A-Za-z0-9_!]*)\\(.*\\)\\s*=(?!=)");

    /** function/struct/module/macro at their indentation; nesting by
     * indentation like Python, so a struct's inner functions sit under it.
     * Column-0 short-form definitions (`f(x) = ...`) surface flat. */
    private static List<Item> julia(String[] lines) {
        List<Item> out = new ArrayList<>();
        Deque<Integer> cols = new ArrayDeque<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = JULIA.matcher(lines[i]);
            if (m.find()) {
                int col = m.group(1).length();
                while (!cols.isEmpty() && cols.peek() >= col) {
                    cols.pop();
                }
                int depth = cols.size();
                cols.push(col);
                String kw = m.group(2);
                OutlineKind kind = switch (kw) {
                    case "module" -> OutlineKind.MODULE;
                    case "function", "macro" -> depth > 0 ? OutlineKind.METHOD : OutlineKind.FUNCTION;
                    default -> OutlineKind.TYPE; // struct / mutable struct / abstract|primitive type
                };
                out.add(new Item(kind, m.group(3), null, i, depth));
            } else {
                Matcher s = JULIA_SHORT.matcher(lines[i]);
                if (s.find()) {
                    out.add(new Item(OutlineKind.FUNCTION, s.group(1), null, i, 0));
                }
            }
        }
        return out;
    }

    // ---- F# ----------------------------------------------------------------

    // module/type carry a dotted name (Rack.Wire); a member is `member this.Foo`
    // where the `this.` self-qualifier is dropped and Foo is the name.
    private static final Pattern FSHARP = Pattern.compile(
            "^\\s*(let\\s+rec|let|type|module|member)\\s+([A-Za-z_][A-Za-z0-9_'.]*)");
    private static final Pattern FSHARP_MEMBER = Pattern.compile(
            "^\\s*member\\s+(?:[A-Za-z_][A-Za-z0-9_']*\\.)?([A-Za-z_][A-Za-z0-9_']*)");

    /** let / let rec / type / module / member at column 0 (or indented member).
     * A `member this.Foo` binding surfaces as Foo; module/type keep dotted names. */
    private static List<Item> fsharp(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher m = FSHARP.matcher(line);
            if (m.find()) {
                String kw = m.group(1);
                if (kw.equals("member")) {
                    Matcher mem = FSHARP_MEMBER.matcher(line);
                    if (mem.find()) {
                        out.add(new Item(OutlineKind.METHOD, mem.group(1), null, i, 0));
                    }
                    continue;
                }
                OutlineKind kind = switch (kw) {
                    case "type" -> OutlineKind.TYPE;
                    case "module" -> OutlineKind.MODULE;
                    default -> OutlineKind.FUNCTION; // let / let rec
                };
                out.add(new Item(kind, m.group(2), null, i, 0));
            }
        }
        return out;
    }

    // ---- Crystal (Ruby-like) -----------------------------------------------

    // The name class carries `.` for `def self.version` and `:` for
    // namespaced types like Rack::Device.
    private static final Pattern CRYSTAL = Pattern.compile(
            "^(\\s*)(def|class|module|struct|enum|macro)\\s+([A-Za-z_][A-Za-z0-9_.:]*[?!]?)");

    /** def/class/module/struct/enum/macro, nested by indentation (Ruby-like).
     * A class's methods sit under it. */
    private static List<Item> crystal(String[] lines) {
        List<Item> out = new ArrayList<>();
        Deque<Integer> cols = new ArrayDeque<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = CRYSTAL.matcher(lines[i]);
            if (m.find()) {
                int col = m.group(1).length();
                while (!cols.isEmpty() && cols.peek() >= col) {
                    cols.pop();
                }
                int depth = cols.size();
                cols.push(col);
                String kw = m.group(2);
                OutlineKind kind = switch (kw) {
                    case "class" -> OutlineKind.CLASS;
                    case "module" -> OutlineKind.MODULE;
                    case "struct" -> OutlineKind.TYPE;
                    case "enum" -> OutlineKind.ENUM;
                    default -> depth > 0 ? OutlineKind.METHOD : OutlineKind.FUNCTION; // def / macro
                };
                out.add(new Item(kind, m.group(3), null, i, depth));
            }
        }
        return out;
    }

    // ---- Zig ---------------------------------------------------------------

    private static final Pattern ZIG_FN = Pattern.compile(
            "^\\s*(?:pub\\s+)?(?:export\\s+)?(?:extern\\s+(?:\"[^\"]*\"\\s+)?)?(?:inline\\s+)?fn\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ZIG_CONST = Pattern.compile(
            "^\\s*(?:pub\\s+)?const\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:extern\\s+|packed\\s+)?(struct|enum|union|opaque)\\b");
    private static final Pattern ZIG_TEST = Pattern.compile(
            "^\\s*test\\s+\"([^\"]+)\"");

    /** pub fn / fn, const Name = struct|enum|union, and test "name" blocks.
     * Column-0 oriented; a nested type keeps depth 0 (flat) to stay honest. */
    private static List<Item> zig(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher t = ZIG_TEST.matcher(line);
            if (t.find()) {
                out.add(new Item(OutlineKind.TEST, t.group(1), null, i, 0));
                continue;
            }
            Matcher c = ZIG_CONST.matcher(line);
            if (c.find()) {
                OutlineKind kind = c.group(2).equals("enum") ? OutlineKind.ENUM : OutlineKind.TYPE;
                out.add(new Item(kind, c.group(1), c.group(2), i, 0));
                continue;
            }
            Matcher f = ZIG_FN.matcher(line);
            if (f.find()) {
                out.add(new Item(OutlineKind.FUNCTION, f.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- Solidity ----------------------------------------------------------

    private static final Pattern SOL_CONTAINER = Pattern.compile(
            "^\\s*(?:abstract\\s+)?(contract|interface|library)\\s+([A-Za-z0-9_$]+)");
    private static final Pattern SOL_FUNCTION = Pattern.compile(
            "^\\s*function\\s+([A-Za-z0-9_$]+)");
    private static final Pattern SOL_CONSTRUCTOR = Pattern.compile(
            "^\\s*constructor\\s*\\(");
    private static final Pattern SOL_MODIFIER = Pattern.compile(
            "^\\s*modifier\\s+([A-Za-z0-9_$]+)");
    private static final Pattern SOL_EVENT = Pattern.compile(
            "^\\s*event\\s+([A-Za-z0-9_$]+)");
    // an error declaration always carries its parameter list: `error Empty();`
    private static final Pattern SOL_ERROR = Pattern.compile(
            "^\\s*error\\s+([A-Za-z0-9_$]+)\\s*\\(");
    private static final Pattern SOL_TYPE = Pattern.compile(
            "^\\s*(struct|enum)\\s+([A-Za-z0-9_$]+)");

    /** contract/interface/library contain their functions, constructor,
     * modifiers, events, errors and structs/enums - nested by brace depth
     * like the other C-shaped languages, with comment state carried across
     * lines so a commented-out declaration never surfaces. */
    private static List<Item> solidity(String[] lines) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        boolean[] state = {false, false};
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String code = stripNonCode(lines[i], state);
            int depthHere = brace;
            Matcher m;
            if ((m = SOL_CONTAINER.matcher(code)).find()) {
                OutlineKind kind = switch (m.group(1)) {
                    case "interface" -> OutlineKind.INTERFACE;
                    case "library" -> OutlineKind.MODULE;
                    default -> OutlineKind.CLASS;
                };
                out.add(new Item(kind, m.group(2), m.group(1), i, depthHere));
            } else if ((m = SOL_FUNCTION.matcher(code)).find()) {
                out.add(new Item(depthHere > 0 ? OutlineKind.METHOD : OutlineKind.FUNCTION,
                        m.group(1), null, i, depthHere));
            } else if (SOL_CONSTRUCTOR.matcher(code).find()) {
                out.add(new Item(OutlineKind.METHOD, "constructor", null, i, depthHere));
            } else if ((m = SOL_MODIFIER.matcher(code)).find()) {
                out.add(new Item(OutlineKind.METHOD, m.group(1), "modifier", i, depthHere));
            } else if ((m = SOL_EVENT.matcher(code)).find()) {
                out.add(new Item(OutlineKind.FIELD, m.group(1), "event", i, depthHere));
            } else if ((m = SOL_ERROR.matcher(code)).find()) {
                out.add(new Item(OutlineKind.FIELD, m.group(1), "error", i, depthHere));
            } else if ((m = SOL_TYPE.matcher(code)).find()) {
                out.add(new Item("enum".equals(m.group(1)) ? OutlineKind.ENUM : OutlineKind.TYPE,
                        m.group(2), null, i, depthHere));
            }
            brace += netBraces(code);
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    // ---- Brace languages (Java/Kotlin/Swift/C#/C/C++/Dart/Groovy/PHP) ----

    private static final Pattern BRACE_DECL = Pattern.compile(
            "\\b(class|interface|enum|struct|object|trait|protocol|namespace)\\s+([A-Za-z0-9_]+)");
    private static final Pattern BRACE_METHOD = Pattern.compile(
            "^\\s*(?:@\\w+\\s*)*(?:public|private|protected|internal|static|final|override|fun|func|def|virtual|async|suspend|inline|operator|\\s)*\\s*[A-Za-z0-9_<>\\[\\].$]+\\s+([A-Za-z0-9_]+)\\s*\\([^;{]*\\)\\s*(?:throws [^{]+)?\\{");

    private static List<Item> braceLang(String[] lines) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher d = BRACE_DECL.matcher(line);
            if (d.find()) {
                OutlineKind kind = switch (d.group(1)) {
                    case "interface", "protocol", "trait" -> OutlineKind.INTERFACE;
                    case "enum" -> OutlineKind.ENUM;
                    case "namespace" -> OutlineKind.MODULE;
                    default -> OutlineKind.CLASS;
                };
                out.add(new Item(kind, d.group(2), null, i, brace));
            } else {
                Matcher m = BRACE_METHOD.matcher(line);
                if (m.find() && !JS_KEYWORDS.contains(m.group(1))) {
                    out.add(new Item(brace > 0 ? OutlineKind.METHOD : OutlineKind.FUNCTION,
                            m.group(1), null, i, brace));
                }
            }
            brace += netBraces(line);
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    // ---- Shell -----------------------------------------------------------

    private static final Pattern SH_FUNC = Pattern.compile(
            "^\\s*(?:function\\s+)?([A-Za-z0-9_-]+)\\s*\\(\\s*\\)\\s*\\{?");

    private static List<Item> shell(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = SH_FUNC.matcher(lines[i]);
            if (m.find()) {
                out.add(new Item(OutlineKind.FUNCTION, m.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- GraphQL ---------------------------------------------------------

    private static final Pattern GQL = Pattern.compile(
            "^\\s*(type|input|interface|enum|scalar|union|schema|extend\\s+type)\\s+([A-Za-z0-9_]+)?");

    private static List<Item> graphql(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = GQL.matcher(lines[i]);
            if (m.find() && m.group(2) != null) {
                OutlineKind kind = m.group(1).startsWith("enum") ? OutlineKind.ENUM
                        : m.group(1).contains("interface") ? OutlineKind.INTERFACE
                        : OutlineKind.TYPE;
                out.add(new Item(kind, m.group(2), m.group(1).trim(), i, 0));
            }
        }
        return out;
    }

    // ---- SQL -------------------------------------------------------------

    private static final Pattern SQL = Pattern.compile(
            "(?i)\\bcreate\\s+(?:or\\s+replace\\s+)?(table|view|index|function|procedure|trigger|materialized\\s+view)\\s+(?:if\\s+not\\s+exists\\s+)?[\"`\\[]?([A-Za-z0-9_.]+)");

    private static List<Item> sql(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = SQL.matcher(lines[i]);
            if (m.find()) {
                out.add(new Item(OutlineKind.TYPE, m.group(2), m.group(1).toLowerCase(), i, 0));
            }
        }
        return out;
    }

    // ---- Makefile --------------------------------------------------------

    private static final Pattern MAKE_TARGET = Pattern.compile("^([A-Za-z0-9_./%-]+)\\s*:(?!=)");

    private static List<Item> makefile(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            if (line.startsWith("\t") || line.startsWith(" ")) {
                continue;
            }
            Matcher m = MAKE_TARGET.matcher(line);
            if (m.find() && !m.group(1).contains("=")) {
                out.add(new Item(OutlineKind.TARGET, m.group(1), null, i, 0));
            }
        }
        return out;
    }

    // ---- Protobuf --------------------------------------------------------

    private static final Pattern PROTO = Pattern.compile(
            "^\\s*(message|enum|service|rpc)\\s+([A-Za-z0-9_]+)");

    private static List<Item> proto(String[] lines) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher m = PROTO.matcher(line);
            if (m.find()) {
                OutlineKind kind = switch (m.group(1)) {
                    case "enum" -> OutlineKind.ENUM;
                    case "service" -> OutlineKind.INTERFACE;
                    case "rpc" -> OutlineKind.METHOD;
                    default -> OutlineKind.TYPE;
                };
                out.add(new Item(kind, m.group(2), null, i, brace));
            }
            brace += netBraces(line);
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    // ---- Generic fallback: surface action markers ------------------------

    private static final Pattern TODO = Pattern.compile(
            "\\b(TODO|FIXME|HACK|XXX|BUG)\\b[:\\s]*(.*)");

    private static List<Item> generic(String[] lines) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            Matcher m = TODO.matcher(lines[i]);
            if (m.find()) {
                String rest = m.group(2).trim();
                String name = m.group(1) + (rest.isEmpty() ? "" : ": " + rest);
                out.add(new Item(OutlineKind.TODO, trim(name, 60), null, i, 0));
            }
        }
        return out;
    }

    // ---- shared helpers --------------------------------------------------

    private interface KindFn {
        OutlineKind kind(Matcher m);
    }

    private static List<Item> braceKeyword(String[] lines, Pattern p, KindFn kindFn, int nameGroup) {
        List<Item> out = new ArrayList<>();
        int brace = 0;
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String line = lines[i];
            Matcher m = p.matcher(line);
            if (m.find()) {
                out.add(new Item(kindFn.kind(m), m.group(nameGroup), null, i, brace));
            }
            brace += netBraces(line);
            if (brace < 0) {
                brace = 0;
            }
        }
        return out;
    }

    /** Net {@code {} minus {@code }} on a line, ignoring line comments and
     * string/char literals so braces in text never skew the nesting. */
    static int netBraces(String line) {
        int net = 0;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                quote = c;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                break;
            } else if (c == '#') {
                break;
            } else if (c == '{') {
                net++;
            } else if (c == '}') {
                net--;
            }
        }
        return net;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
