package org.nmox.studio.rack.blockstudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The reverse half of the round trip: a Block-Studio-generated file
 * back into a {@link BlockDoc}. This is deliberately a parser for OUR
 * dialect only — the exact canonical shape {@link BlockCodegen} emits —
 * never a general JavaScript parser. Anything outside the dialect
 * refuses with a line-numbered {@link ParseException}; there is no
 * half-import. Hand edits that stay INSIDE the dialect (changed text,
 * an extra element in the same shape) import fine, which is the honest
 * amount of two-way editing.
 *
 * <p>Identity survives the trip: element ids ride the {@code data-b}
 * anchors and listener ids ride the {@code const bN} variables, so
 * {@code generate(parse(code))} reproduces {@code code} byte for byte —
 * the property {@code BlockRoundTripTest} pins across the whole piece
 * vocabulary. Blocks that never appear in code (texts, attributes,
 * states…) get fresh ids above every anchored one.
 *
 * <p>Known strictness (documented, tested): a TEXT whose content itself
 * starts with {@code <} generates fine but cannot be re-imported — the
 * parser reads template lines starting with {@code <} as markup and
 * refuses when they aren't. Consistency is verified, not assumed: the
 * trailing {@code this.render()} must match the presence of a
 * Set-state, the class name must match the tag, and every data-b
 * anchor must gain its listener.
 */
public final class BlockParser {

    /** A refusal, always with the 1-based source line it points at. */
    public static final class ParseException extends RuntimeException {

        private final int line;

        ParseException(int line, String message) {
            super("line " + line + ": " + message);
            this.line = line;
        }

        public int line() {
            return line;
        }
    }

    private static final Pattern STATE_FIELD = Pattern.compile(
            "  #([a-z_][A-Za-z0-9_]*) = (.+);");
    private static final Pattern PROP_ACCESSOR = Pattern.compile(
            "  #prop_([a-z0-9_]+)\\(\\) \\{ return this\\.getAttribute\\('([a-z0-9-]+)'\\) \\?\\? (.+); }");
    private static final Pattern OBSERVED = Pattern.compile(
            "  static get observedAttributes\\(\\) \\{ return \\[(.*)]; }");
    private static final Pattern CLASS_LINE = Pattern.compile(
            "class ([A-Za-z0-9]+) extends HTMLElement \\{");
    private static final Pattern DEFINE_LINE = Pattern.compile(
            "customElements\\.define\\('([a-z0-9-]+)', ([A-Za-z0-9]+)\\);");
    /** Attr blob is one linear char-class (the ReDOS-gate idiom); its
     *  pair structure is verified by full consumption in parseElement. */
    private static final Pattern ELEMENT_OPEN = Pattern.compile(
            "( *)<([a-z][a-z0-9-]*)([^<>]*)>");
    private static final Pattern ELEMENT_CLOSE = Pattern.compile(
            "( *)</([a-z][a-z0-9-]*)>");
    /** Middle blob is linear; the name pair is validated in code
     *  (the ReDOS-gate idiom, same as ELEMENT_OPEN). */
    private static final Pattern SLOT_LINE = Pattern.compile(
            "( *)<slot([^<>]*)></slot>");
    private static final Pattern SLOT_NAME = Pattern.compile(
            " name=\"([^\"]*)\"");
    private static final Pattern ATTR_PAIR = Pattern.compile(
            "([^=\\s\"]+)=\"([^\"]*)\"");
    private static final Pattern QUERY_LINE = Pattern.compile(
            "    const ([A-Za-z0-9_]+) = this\\.shadowRoot\\.querySelector\\('\\[data-b=\"([A-Za-z0-9_-]+)\"]'\\);");
    private static final Pattern LISTEN_LINE = Pattern.compile(
            "    ([A-Za-z0-9_]+)\\.addEventListener\\('([a-z]+)', \\(\\) => \\{");
    private static final Pattern SET_STATE_LINE = Pattern.compile(
            "( +)this\\.#([a-z_][A-Za-z0-9_]*) = (.+);");
    /** The host var class already covers the literal "this" — a single
     *  linear class, no overlapping alternation (the ReDOS-gate idiom). */
    private static final Pattern TOGGLE_LINE = Pattern.compile(
            "( +)([A-Za-z0-9_]+)\\.classList\\.toggle\\('([a-zA-Z-][a-zA-Z0-9-]*)'\\);");
    private static final Pattern LOG_LINE = Pattern.compile(
            "( +)console\\.log\\(`(.*)`\\);");
    private static final Pattern IF_LINE = Pattern.compile(
            "( +)if \\(this\\.#([a-z_][A-Za-z0-9_]*) (==|!=|>=|<=|>|<) (.+)\\) \\{");
    private static final Pattern DISPATCH_LINE = Pattern.compile(
            "( +)this\\.dispatchEvent\\(new CustomEvent\\('([a-z][a-z0-9-]*)', "
            + "\\{ bubbles: true, composed: true, detail: `(.*)` }\\)\\);");
    private static final Pattern TIMER_OPEN = Pattern.compile(
            "    this\\._t([0-9]+) = setInterval\\(\\(\\) => \\{");
    private static final Pattern TIMER_CLOSE = Pattern.compile(
            "    }, ([0-9]+)\\);");
    private static final Pattern STATE_REF = Pattern.compile(
            "\\$\\{this\\.#([a-z_][A-Za-z0-9_]*)}");
    private static final Pattern PROP_CALL_REF = Pattern.compile(
            "\\$\\{this\\.#prop_([a-z0-9_]+)\\(\\)}");
    private static final Pattern EXPR_PROP = Pattern.compile(
            "this\\.#prop_([a-z0-9_]+)\\(\\)");
    private static final Pattern EXPR_STATE = Pattern.compile(
            "this\\.#([a-z_][A-Za-z0-9_]*)");
    private static final Pattern ANCHOR_NUM = Pattern.compile("b([0-9]+)");

    private final String[] lines;
    private int at; // 0-based cursor
    private final Map<String, String> propByMethod = new LinkedHashMap<>();
    private final List<String> states = new ArrayList<>();
    private int fresh = 1; // fresh-id numbering, raised above anchors

    private BlockParser(String code) {
        this.lines = code.split("\n", -1);
    }

    /** Parses; throws {@link ParseException} on anything off-dialect. */
    public static BlockDoc parse(String code) {
        return new BlockParser(code).run();
    }

    // ---- driver ----

    private BlockDoc run() {
        expect(BlockCodegen.MARKER, "the Block Studio marker");
        expect("", "a blank line");
        Matcher cls = match(CLASS_LINE, "the component class");
        String className = cls.group(1);

        JSONArray rootChildren = new JSONArray();
        // state fields
        while (peekMatches(STATE_FIELD)) {
            Matcher m = match(STATE_FIELD, "a state field");
            states.add(m.group(1));
            rootChildren.put(node(freshId(), "STATE",
                    Map.of("name", m.group(1), "initial", unliteral(m.group(2)))));
        }
        // prop accessors
        List<String> propNames = new ArrayList<>();
        while (peekMatches(PROP_ACCESSOR)) {
            Matcher m = match(PROP_ACCESSOR, "a prop accessor");
            if (!m.group(1).equals(m.group(2).replace('-', '_'))) {
                throw new ParseException(at, "prop accessor #prop_" + m.group(1)
                        + " does not match attribute '" + m.group(2) + "'");
            }
            propByMethod.put(m.group(1), m.group(2));
            propNames.add(m.group(2));
            rootChildren.put(node(freshId(), "PROP",
                    Map.of("name", m.group(2), "default", unliteral(m.group(3)))));
        }
        if (!propNames.isEmpty()) {
            Matcher m = match(OBSERVED, "the observedAttributes list");
            String expected = "'" + String.join("', '", propNames) + "'";
            if (!m.group(1).equals(expected)) {
                throw new ParseException(at, "observedAttributes [" + m.group(1)
                        + "] does not match the prop accessors [" + expected + "]");
            }
        }
        expect("", "a blank line");
        expect("  constructor() {", "the constructor");
        expect("    super();", "super()");
        expect("    this.attachShadow({ mode: 'open' });", "attachShadow");
        expect("    this.render();", "the initial render");
        expect("  }", "the constructor close");
        if (!propNames.isEmpty()) {
            expect("", "a blank line");
            expect("  attributeChangedCallback() {", "attributeChangedCallback");
            expect("    this.render();", "the attribute re-render");
            expect("  }", "attributeChangedCallback close");
        }
        expect("", "a blank line");
        expect("  render() {", "render()");
        expect("    this.shadowRoot.innerHTML = `", "the template open");
        // template nodes until the closing backtick line
        List<Object> template = new ArrayList<>();
        parseTemplate(template, 6);
        expect("    `;", "the template close");
        for (Object o : template) {
            rootChildren.put((JSONObject) o);
        }
        // listeners
        Map<String, JSONObject> byAnchor = anchorsIn(rootChildren);
        while (peekMatches(QUERY_LINE)) {
            parseListener(byAnchor);
        }
        expect("  }", "the render close");
        // timers
        List<JSONObject> timers = new ArrayList<>();
        if (peekIs("")) {
            int mark = at;
            expect("", "a blank line");
            if (peekIs("  connectedCallback() {")) {
                expect("  connectedCallback() {", "connectedCallback");
                int index = 0;
                while (peekMatches(TIMER_OPEN)) {
                    Matcher open = match(TIMER_OPEN, "a timer");
                    if (Integer.parseInt(open.group(1)) != index) {
                        throw new ParseException(at, "timer fields must be numbered in order");
                    }
                    JSONArray actions = new JSONArray();
                    boolean sets = parseActions(actions, 6);
                    consumeRender(sets, 6);
                    Matcher close = match(TIMER_CLOSE, "the timer close");
                    timers.add(node(freshId(), "TIMER", Map.of("ms", close.group(1)), actions));
                    index++;
                }
                expect("  }", "connectedCallback close");
                expect("", "a blank line");
                expect("  disconnectedCallback() {", "disconnectedCallback");
                for (int j = 0; j < timers.size(); j++) {
                    expect("    clearInterval(this._t" + j + ");", "clearInterval for timer " + j);
                }
                expect("  }", "disconnectedCallback close");
            } else {
                at = mark; // the blank belonged to the class close
            }
        }
        for (JSONObject t : timers) {
            rootChildren.put(t);
        }
        expect("}", "the class close");
        expect("", "a blank line");
        Matcher def = match(DEFINE_LINE, "customElements.define");
        if (!def.group(2).equals(className)) {
            throw new ParseException(at, "define registers " + def.group(2)
                    + " but the class is " + className);
        }
        String tag = def.group(1);
        if (!BlockCodegen.className(tag).equals(className)) {
            throw new ParseException(at, "class " + className
                    + " does not match tag '" + tag + "'");
        }
        while (at < lines.length) {
            expect("", "nothing after the define line");
        }

        JSONObject root = node("b_root", "COMPONENT", Map.of("tag", tag), rootChildren);
        JSONObject docJson = new JSONObject();
        docJson.put("version", 1);
        docJson.put("nextId", fresh);
        docJson.put("root", root);
        return BlockDoc.fromJson(docJson);
    }

    // ---- template ----

    private void parseTemplate(List<Object> out, int indent) {
        String pad = " ".repeat(indent);
        while (at < lines.length) {
            String line = lines[at];
            if (line.equals("    `;")) {
                return;
            }
            Matcher slot = SLOT_LINE.matcher(line);
            if (slot.matches() && slot.group(1).length() == indent) {
                at++;
                String blob = slot.group(2);
                String name;
                if (blob.isEmpty()) {
                    name = "";
                } else {
                    Matcher n = SLOT_NAME.matcher(blob);
                    if (!n.matches()) {
                        throw new ParseException(at, "malformed slot attributes: <slot" + blob + ">");
                    }
                    name = untemplate(n.group(1), true);
                }
                out.add(node(freshId(), "SLOT", Map.of("name", name)));
                continue;
            }
            Matcher open = ELEMENT_OPEN.matcher(line);
            if (open.matches() && open.group(1).length() == indent) {
                at++;
                out.add(parseElement(open, indent));
                continue;
            }
            if (ELEMENT_CLOSE.matcher(line).matches()) {
                return; // parent's close — let the caller consume it
            }
            if (line.startsWith(pad) && !line.substring(indent).startsWith("<")
                    && !line.substring(indent).startsWith(" ")) {
                at++;
                out.add(node(freshId(), "TEXT",
                        Map.of("text", untemplate(line.substring(indent), false))));
                continue;
            }
            throw new ParseException(at + 1, "unrecognized template line: " + line.trim());
        }
        throw new ParseException(at, "template never closed");
    }

    private JSONObject parseElement(Matcher open, int indent) {
        String tag = open.group(2);
        JSONArray children = new JSONArray();
        String id = null;
        String blob = open.group(3);
        // strict pair walk: " name=\"value\"" repeated, nothing left over
        int consumed = 0;
        Matcher a = ATTR_PAIR.matcher(blob);
        while (a.find()) {
            if (a.start() != consumed + 1 || blob.charAt(consumed) != ' ') {
                throw new ParseException(at, "malformed attributes in <" + tag + blob + ">");
            }
            consumed = a.end();
            switch (a.group(1)) {
                case "data-b" -> id = a.group(2);
                case "style" -> children.put(node(freshId(), "STYLE",
                        Map.of("css", untemplate(a.group(2), true))));
                default -> children.put(node(freshId(), "SET_ATTR",
                        Map.of("name", a.group(1), "value", untemplate(a.group(2), true))));
            }
        }
        if (consumed != blob.length()) {
            throw new ParseException(at, "malformed attributes in <" + tag + blob + ">");
        }
        List<Object> nested = new ArrayList<>();
        parseTemplate(nested, indent + 2);
        Matcher close = match(ELEMENT_CLOSE, "</" + tag + ">");
        if (close.group(1).length() != indent || !close.group(2).equals(tag)) {
            throw new ParseException(at, "mismatched close tag </" + close.group(2)
                    + "> for <" + tag + ">");
        }
        for (Object o : nested) {
            children.put((JSONObject) o);
        }
        return node(id != null ? anchored(id) : freshId(), tag, children, true);
    }

    // ---- listeners & actions ----

    private void parseListener(Map<String, JSONObject> byAnchor) {
        Matcher q = match(QUERY_LINE, "a listener query");
        String var = q.group(1);
        String anchor = q.group(2);
        JSONObject host = byAnchor.get(anchor);
        if (host == null) {
            throw new ParseException(at, "listener targets unknown data-b anchor \"" + anchor + "\"");
        }
        Matcher l = match(LISTEN_LINE, "addEventListener");
        if (!l.group(1).equals(var)) {
            throw new ParseException(at, "listener variable " + l.group(1)
                    + " does not match query variable " + var);
        }
        JSONArray actions = new JSONArray();
        boolean sets = parseActions(actions, 6);
        consumeRender(sets, 6);
        expect("    });", "the listener close");
        host.getJSONArray("children").put(
                node(anchored(var), "ON_EVENT", Map.of("event", l.group(2)), actions));
    }

    /** Parses actions at {@code indent}; true when any SET_STATE ran. */
    private boolean parseActions(JSONArray out, int indent) {
        boolean sets = false;
        while (at < lines.length) {
            String line = lines[at];
            Matcher m;
            if ((m = SET_STATE_LINE.matcher(line)).matches() && m.group(1).length() == indent) {
                at++;
                requireState(m.group(2));
                out.put(node(freshId(), "SET_STATE",
                        Map.of("name", m.group(2), "expr", unexpr(m.group(3)))));
                sets = true;
            } else if ((m = TOGGLE_LINE.matcher(line)).matches() && m.group(1).length() == indent) {
                at++;
                out.put(node(freshId(), "TOGGLE_CLASS", Map.of("class", m.group(3))));
            } else if ((m = LOG_LINE.matcher(line)).matches() && m.group(1).length() == indent) {
                at++;
                out.put(node(freshId(), "LOG", Map.of("message", untemplate(m.group(2), false))));
            } else if ((m = DISPATCH_LINE.matcher(line)).matches() && m.group(1).length() == indent) {
                at++;
                out.put(node(freshId(), "DISPATCH",
                        Map.of("event", m.group(2), "detail", untemplate(m.group(3), false))));
            } else if ((m = IF_LINE.matcher(line)).matches() && m.group(1).length() == indent) {
                at++;
                requireState(m.group(2));
                JSONArray nested = new JSONArray();
                sets |= parseActions(nested, indent + 2);
                expect(" ".repeat(indent) + "}", "the if close");
                out.put(node(freshId(), "IF_STATE", Map.of(
                        "name", m.group(2), "op", m.group(3),
                        "value", unliteral(m.group(4))), nested));
            } else {
                return sets;
            }
        }
        return sets;
    }

    /** The derived re-render line: present iff a SET_STATE ran. */
    private void consumeRender(boolean sets, int indent) {
        String render = " ".repeat(indent) + "this.render();";
        boolean present = peekIs(render);
        if (present != sets) {
            throw new ParseException(at + 1, sets
                    ? "a Set-state ran but the re-render line is missing"
                    : "a re-render line without any Set-state");
        }
        if (present) {
            at++;
        }
    }

    private void requireState(String name) {
        if (!states.contains(name)) {
            throw new ParseException(at, "undeclared state \"" + name + "\"");
        }
    }

    // ---- text inversion (exact inverses of BlockCodegen's escaping) ----

    /** Template text back to its param form: refs → {x}/{@y}, unescape. */
    private String untemplate(String s, boolean attrContext) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            Matcher prop = PROP_CALL_REF.matcher(s).region(i, s.length());
            Matcher state = STATE_REF.matcher(s).region(i, s.length());
            if (prop.lookingAt()) {
                String attr = propByMethod.get(prop.group(1));
                if (attr == null) {
                    throw new ParseException(at, "reference to unknown prop accessor #prop_" + prop.group(1));
                }
                out.append("{@").append(attr).append("}");
                i = prop.end();
            } else if (state.lookingAt() && states.contains(state.group(1))) {
                out.append("{").append(state.group(1)).append("}");
                i = state.end();
            } else if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '\\' || n == '`') {
                    out.append(n);
                    i += 2;
                } else if (n == '$' && i + 2 < s.length() && s.charAt(i + 2) == '{') {
                    out.append("${");
                    i += 3;
                } else {
                    throw new ParseException(at, "unexpected escape \\" + n + " in template text");
                }
            } else if (s.charAt(i) == '$' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                throw new ParseException(at, "unrecognized ${...} interpolation in template text");
            } else if (attrContext && s.startsWith("&quot;", i)) {
                out.append('"');
                i += 6;
            } else {
                out.append(s.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** Expression back to param form: this.#x → {x}, prop calls → {@y}. */
    private String unexpr(String s) {
        String r = EXPR_PROP.matcher(s).replaceAll(m -> {
            String attr = propByMethod.get(m.group(1));
            if (attr == null) {
                throw new ParseException(at, "expression uses unknown prop accessor");
            }
            return Matcher.quoteReplacement("{@" + attr + "}");
        });
        return EXPR_STATE.matcher(r).replaceAll(m -> {
            if (!states.contains(m.group(1))) {
                throw new ParseException(at, "expression uses undeclared state \"" + m.group(1) + "\"");
            }
            return Matcher.quoteReplacement("{" + m.group(1) + "}");
        });
    }

    /** Inverse of BlockCodegen.literal(). */
    private String unliteral(String s) {
        if (s.matches("-?[0-9]+") || s.matches("-?[0-9]+\\.[0-9]+")
                || s.equals("true") || s.equals("false")) {
            return s;
        }
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("\\'", "'").replace("\\\\", "\\");
        }
        throw new ParseException(at, "unrecognized literal " + s);
    }

    // ---- cursor plumbing ----

    private void expect(String literal, String what) {
        if (at >= lines.length || !lines[at].equals(literal)) {
            throw new ParseException(at + 1, "expected " + what);
        }
        at++;
    }

    private Matcher match(Pattern p, String what) {
        if (at >= lines.length) {
            throw new ParseException(at + 1, "expected " + what + " but the file ended");
        }
        Matcher m = p.matcher(lines[at]);
        if (!m.matches()) {
            throw new ParseException(at + 1, "expected " + what);
        }
        at++;
        return m;
    }

    private boolean peekMatches(Pattern p) {
        return at < lines.length && p.matcher(lines[at]).matches();
    }

    private boolean peekIs(String literal) {
        return at < lines.length && lines[at].equals(literal);
    }

    /** Registers an id seen in code and keeps fresh ids above it. */
    private String anchored(String id) {
        Matcher m = ANCHOR_NUM.matcher(id);
        if (m.matches()) {
            fresh = Math.max(fresh, Integer.parseInt(m.group(1)) + 1);
        }
        return id;
    }

    private String freshId() {
        return "p" + (fresh++);
    }

    // ---- JSON node builders ----

    private static JSONObject node(String id, String kind, Map<String, String> params) {
        return node(id, kind, params, new JSONArray());
    }

    private static JSONObject node(String id, String kind, Map<String, String> params,
            JSONArray children) {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("kind", kind);
        JSONObject p = new JSONObject();
        params.forEach(p::put);
        o.put("params", p);
        o.put("children", children);
        return o;
    }

    /** An ELEMENT node (tag param), children already collected. */
    private JSONObject node(String id, String tag, JSONArray children, boolean element) {
        anchored(id);
        return node(id, "ELEMENT", Map.of("tag", tag), children);
    }

    /** data-b anchors present anywhere under the parsed template. */
    private static Map<String, JSONObject> anchorsIn(JSONArray rootChildren) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        collectAnchors(rootChildren, out);
        return out;
    }

    private static void collectAnchors(JSONArray nodes, Map<String, JSONObject> out) {
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            if ("ELEMENT".equals(n.getString("kind"))) {
                out.put(n.getString("id"), n);
            }
            collectAnchors(n.getJSONArray("children"), out);
        }
    }
}
