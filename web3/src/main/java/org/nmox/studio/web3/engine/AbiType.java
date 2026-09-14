package org.nmox.studio.web3.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

/**
 * An ABI type as a tree — the shape the codec walks. A type string alone
 * cannot describe a struct ({@code tuple} carries its members in the ABI
 * JSON's {@code components}), so every encode/decode path parses the
 * parameter into one of these first:
 *
 * <ul>
 *   <li>{@link Kind#SCALAR} — {@code uintN}, {@code address}, {@code bytes},
 *       … validated lazily at the leaf so each keeps its own refusal;</li>
 *   <li>{@link Kind#ARRAY} — {@code T[]} (dynamic) or {@code T[k]}, where
 *       {@code T} may itself be an array or a tuple;</li>
 *   <li>{@link Kind#TUPLE} — ordered, named components.</li>
 * </ul>
 *
 * <p>Parsing refuses what cannot be laid out honestly: a {@code tuple}
 * with no components, nesting past {@link AbiLiteral#MAX_DEPTH}, and a
 * fixed array too large for a head section to exist in memory.
 */
final class AbiType {

    enum Kind { SCALAR, ARRAY, TUPLE }

    /** Largest fixed-array length the codec will lay out. */
    static final int MAX_FIXED_LENGTH = 100_000;

    private static final int WORD = 32;

    final Kind kind;
    /** The type string at this level, e.g. {@code tuple[2]} — decode labels use it. */
    final String type;
    /** ARRAY only: the element type. */
    final AbiType element;
    /** ARRAY only: the fixed length, or -1 for a dynamic {@code T[]}. */
    final int length;
    /** TUPLE only: component types and names, in declaration order. */
    final List<AbiType> components;
    final List<String> names;
    private final boolean dynamic;
    private final int staticWords;

    private AbiType(Kind kind, String type, AbiType element, int length,
            List<AbiType> components, List<String> names, String label) {
        this.kind = kind;
        this.type = type;
        this.element = element;
        this.length = length;
        this.components = components;
        this.names = names;
        this.dynamic = switch (kind) {
            case SCALAR -> type.equals("bytes") || type.equals("string");
            case ARRAY -> length < 0 || element.dynamic;
            case TUPLE -> components.stream().anyMatch(c -> c.dynamic);
        };
        long words = switch (kind) {
            case SCALAR -> 1;
            case ARRAY -> length < 0 ? 1 : (long) length * element.headWords();
            case TUPLE -> components.stream().mapToLong(AbiType::headWords).sum();
        };
        if (words > Integer.MAX_VALUE / WORD) {
            throw new IllegalArgumentException("Parameter " + label + " is a " + type
                    + " too large to lay out (" + words + " words).");
        }
        this.staticWords = (int) words;
    }

    /** Parses a parameter, components and all; refusals name {@code label}. */
    static AbiType of(AbiParam param, String label) {
        return parse(param.type() == null ? "" : param.type(), param.components(), label, 0);
    }

    private static AbiType parse(String type, List<AbiParam> components, String label, int depth) {
        if (depth > AbiLiteral.MAX_DEPTH) {
            throw new IllegalArgumentException("Parameter " + label
                    + " nests deeper than " + AbiLiteral.MAX_DEPTH + " levels.");
        }
        if (type.endsWith("]")) {
            int bracket = type.lastIndexOf('[');
            if (bracket <= 0) {
                throw new IllegalArgumentException("Parameter " + label
                        + " has unsupported array type '" + type + "'.");
            }
            String sizeSpec = type.substring(bracket + 1, type.length() - 1);
            int length = sizeSpec.isEmpty() ? -1 : parseArraySize(sizeSpec, type, label);
            AbiType element = parse(type.substring(0, bracket), components, label, depth + 1);
            return new AbiType(Kind.ARRAY, type, element, length, List.of(), List.of(), label);
        }
        if (type.equals("tuple")) {
            if (components.isEmpty()) {
                throw new IllegalArgumentException("Parameter " + label
                        + " is a tuple, but this ABI lists none of its components"
                        + " — re-export the ABI with them (solc and forge include them).");
            }
            List<AbiType> types = new ArrayList<>(components.size());
            List<String> names = new ArrayList<>(components.size());
            for (int i = 0; i < components.size(); i++) {
                AbiParam component = components.get(i);
                String name = component.name() == null ? "" : component.name();
                types.add(parse(component.type() == null ? "" : component.type(),
                        component.components(), componentLabel(label, name, i), depth + 1));
                names.add(name);
            }
            return new AbiType(Kind.TUPLE, type, null, -1,
                    List.copyOf(types), List.copyOf(names), label);
        }
        if (type.startsWith("tuple") || type.startsWith("(")) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported type '" + type + "'.");
        }
        return new AbiType(Kind.SCALAR, type, null, -1, List.of(), List.of(), label);
    }

    /**
     * How a tuple component is named in a refusal: its name and its
     * 1-based position, so {@code 'order'.amount#2} says which field and
     * where it sits even when two components share a type.
     */
    static String componentLabel(String parent, String name, int index) {
        return parent + "." + (name == null || name.isBlank() ? "" : name) + "#" + (index + 1);
    }

    static int parseArraySize(String sizeSpec, String type, String label) {
        int size;
        try {
            size = Integer.parseInt(sizeSpec);
        } catch (NumberFormatException bad) {
            size = -1;
        }
        if (size < 0) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported array type '" + type + "'.");
        }
        if (size > MAX_FIXED_LENGTH) {
            throw new IllegalArgumentException("Parameter " + label + " is a " + type
                    + " — more than " + MAX_FIXED_LENGTH + " elements is refused.");
        }
        return size;
    }

    /** True when the encoding lives in the tail (an offset sits in the head). */
    boolean isDynamic() {
        return dynamic;
    }

    /** Words this type occupies in a head section: 1 for dynamic, its inline size otherwise. */
    int headWords() {
        return dynamic ? 1 : staticWords;
    }

    /** Inline words of a static type. */
    int staticWords() {
        return staticWords;
    }

    /** True when a tuple appears anywhere in this type. */
    boolean containsTuple() {
        return switch (kind) {
            case SCALAR -> false;
            case ARRAY -> element.containsTuple();
            case TUPLE -> true;
        };
    }

    /**
     * The shape a person types, for a field hint: a tuple is a list of
     * {@code name: type} in order, an array of tuples a list of those —
     * {@code [[to: address, amount: uint256], …]}. A fixed array of
     * tuples shows its count. Scalars read as their canonical type.
     */
    String shape() {
        return switch (kind) {
            case SCALAR -> AbiEntry.canonicalType(type);
            case ARRAY -> element.containsTuple()
                    ? "[" + element.shape() + (length < 0 ? ", …]" : " × " + length + "]")
                    : element.shape() + (length < 0 ? "[]" : "[" + length + "]");
            case TUPLE -> {
                StringJoiner joiner = new StringJoiner(", ", "[", "]");
                for (int i = 0; i < components.size(); i++) {
                    String name = names.get(i);
                    joiner.add(name.isBlank() ? components.get(i).shape()
                            : name + ": " + components.get(i).shape());
                }
                yield joiner.toString();
            }
        };
    }
}
