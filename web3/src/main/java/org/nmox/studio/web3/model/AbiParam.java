package org.nmox.studio.web3.model;

import java.util.List;
import java.util.StringJoiner;

/**
 * One input or output parameter of an ABI entry.
 *
 * @param name       the parameter name from the ABI JSON, or {@code ""} when
 *                   the contract left it unnamed (decoders label those
 *                   {@code arg0}, {@code arg1}, ...)
 * @param type       the Solidity ABI type string, e.g. {@code uint256},
 *                   {@code address}, {@code bytes32[]}; a struct arrives as
 *                   {@code tuple} (or {@code tuple[]}, {@code tuple[2]},
 *                   {@code tuple[][]}) with its members in {@code components}
 * @param indexed    true for an event parameter that lives in the log topics
 *                   rather than the data section; always false for function
 *                   parameters
 * @param components the members of a {@code tuple} type, in declaration
 *                   order, each itself possibly a tuple; empty for every
 *                   other type. An ABI that says {@code tuple} but lists no
 *                   components is refused by the codec rather than guessed
 */
public record AbiParam(String name, String type, boolean indexed, List<AbiParam> components) {

    public AbiParam {
        components = components == null ? List.of() : List.copyOf(components);
    }

    /** A parameter with no components — every non-tuple type. */
    public AbiParam(String name, String type, boolean indexed) {
        this(name, type, indexed, List.of());
    }

    /** A non-indexed parameter — the usual case for functions. */
    public static AbiParam of(String name, String type) {
        return new AbiParam(name, type, false);
    }

    /** A non-indexed tuple parameter (or tuple array) with its members. */
    public static AbiParam tuple(String name, String type, List<AbiParam> components) {
        return new AbiParam(name, type, false, components);
    }

    /**
     * The canonical type this parameter hashes as in a signature:
     * {@code uint} → {@code uint256}, and a tuple rendered as its
     * parenthesized member list with any array suffix kept —
     * {@code tuple[]} over (uint256, address) is
     * {@code (uint256,address)[]}. Nested tuples recurse.
     */
    public String canonicalType() {
        String t = type == null ? "" : type;
        if (t.equals("tuple") || t.startsWith("tuple[")) {
            StringJoiner members = new StringJoiner(",", "(", ")");
            for (AbiParam component : components) {
                members.add(component.canonicalType());
            }
            return members + t.substring("tuple".length());
        }
        return AbiEntry.canonicalType(t);
    }
}
