package org.nmox.studio.web3.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The literal grammar a person types into an array or tuple field of the
 * Interact pane — pure and strict, so every refusal names the parameter
 * and the character where the text stopped making sense.
 *
 * <pre>
 *   value  := list | quoted | bare
 *   list   := '[' ( value ( ',' value )* )? ']'
 *   quoted := '"' ( any char but '"' or '\' | escape )* '"'
 *   escape := '\' ( '"' | '\' | '/' | 'b' | 'f' | 'n' | 'r' | 't' | 'u' hex hex hex hex )
 *   bare   := one or more chars that are none of  [ ] , " { }  (trimmed)
 * </pre>
 *
 * <p>A tuple is written as a list of its components in declaration order
 * ({@code ["0x…", "100"]}), an array of tuples as a list of lists, and
 * nesting follows the same rule to any depth up to {@link #MAX_DEPTH}.
 * The grammar decides only STRUCTURE — whether {@code "1.5"} is a legal
 * {@code uint256} is the codec's question, answered by the same strict
 * number rules a top-level field uses (never truncated, never guessed).
 *
 * <p>Refused outright: an empty element ({@code [1,,2]}), a trailing
 * comma, an unterminated string or list, text after the closing bracket,
 * an unknown escape, and a JSON object — components go in order, not by
 * key, so an object would be a second way to say the same thing that
 * could silently disagree with the ABI's order.
 */
public final class AbiLiteral {

    /** How deep lists may nest before the literal is refused. */
    public static final int MAX_DEPTH = 32;

    private AbiLiteral() {
    }

    /** One parsed value: a single scalar or a bracketed list. */
    public sealed interface Node permits Scalar, Group {
    }

    /**
     * A scalar leaf.
     *
     * @param text   the value — unescaped when it was quoted, trimmed when bare
     * @param quoted whether the person wrote it inside double quotes
     */
    public record Scalar(String text, boolean quoted) implements Node {
    }

    /** A bracketed list of values, in the order written. */
    public record Group(List<Node> items) implements Node {

        public Group {
            items = List.copyOf(items);
        }
    }

    /**
     * Parses one field's whole text.
     *
     * @param raw   what the person typed
     * @param label the parameter label every refusal names, e.g. {@code 'order'}
     * @throws IllegalArgumentException with a status-bar-ready sentence
     */
    public static Node parse(String raw, String label) {
        Parser parser = new Parser(raw == null ? "" : raw, label);
        parser.skipSpace();
        if (parser.atEnd()) {
            throw parser.refuse("is empty");
        }
        Node node = parser.value(0);
        parser.skipSpace();
        if (!parser.atEnd()) {
            throw parser.refuse("has unexpected text after the value at character "
                    + parser.column());
        }
        return node;
    }

    private static final class Parser {
        private final String text;
        private final String label;
        private int pos;

        Parser(String text, String label) {
            this.text = text;
            this.label = label;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        int column() {
            return pos + 1;
        }

        void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        IllegalArgumentException refuse(String reason) {
            return new IllegalArgumentException("Parameter " + label + " " + reason + ".");
        }

        Node value(int depth) {
            skipSpace();
            if (atEnd()) {
                throw refuse("ends where a value was expected");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '[' -> list(depth + 1);
                case '"' -> quoted();
                case ',', ']' -> throw refuse("has an empty element at character " + column()
                        + " — write \"\" for an empty string");
                case '{', '}' -> throw refuse("has an object at character " + column()
                        + " — write a tuple's components in order as a list, like [a, b]");
                default -> bare();
            };
        }

        Node list(int depth) {
            if (depth > MAX_DEPTH) {
                throw refuse("nests lists deeper than " + MAX_DEPTH + " levels");
            }
            int open = column();
            pos++; // '['
            List<Node> items = new ArrayList<>();
            skipSpace();
            if (!atEnd() && text.charAt(pos) == ']') {
                pos++;
                return new Group(items);
            }
            while (true) {
                items.add(value(depth));
                skipSpace();
                if (atEnd()) {
                    throw refuse("is missing the ] that closes the [ at character " + open);
                }
                char c = text.charAt(pos);
                if (c == ']') {
                    pos++;
                    return new Group(items);
                }
                if (c != ',') {
                    throw refuse("expected , or ] at character " + column());
                }
                pos++; // ','
                skipSpace();
                if (!atEnd() && text.charAt(pos) == ']') {
                    throw refuse("has a trailing comma before the ] at character " + column());
                }
            }
        }

        Node quoted() {
            int open = column();
            pos++; // opening quote
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return new Scalar(out.toString(), true);
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    break;
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(unicodeEscape());
                    default -> throw refuse("has an unknown escape \\" + e
                            + " at character " + (pos - 1));
                }
            }
            throw refuse("has an unterminated string starting at character " + open);
        }

        char unicodeEscape() {
            if (pos + 4 > text.length()) {
                throw refuse("has a \\u escape without four hex digits at character " + pos);
            }
            String hex = text.substring(pos, pos + 4);
            for (int i = 0; i < 4; i++) {
                if (Character.digit(hex.charAt(i), 16) < 0) {
                    throw refuse("has a \\u escape without four hex digits at character " + pos);
                }
            }
            pos += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        Node bare() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ',' || c == ']') {
                    break;
                }
                if (c == '[' || c == '"' || c == '{' || c == '}') {
                    throw refuse("has an unexpected " + c + " at character " + column()
                            + " — quote a value that contains it");
                }
                pos++;
            }
            return new Scalar(text.substring(start, pos).trim(), false);
        }
    }
}
