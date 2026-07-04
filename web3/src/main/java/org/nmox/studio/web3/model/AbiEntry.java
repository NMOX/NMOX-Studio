package org.nmox.studio.web3.model;

import java.util.List;
import java.util.StringJoiner;

/**
 * One entry of a contract ABI — a function, an event, the constructor,
 * or a custom error. One record models the whole family; {@link Kind}
 * says which fields are meaningful:
 *
 * <ul>
 *   <li>{@code FUNCTION} — name, inputs, outputs, stateMutability</li>
 *   <li>{@code EVENT} — name, inputs (with {@link AbiParam#indexed} flags)</li>
 *   <li>{@code CONSTRUCTOR} — inputs, stateMutability; name is {@code ""}</li>
 *   <li>{@code ERROR} — name, inputs</li>
 * </ul>
 *
 * <p>Both parameter lists are defensively copied to immutable lists.
 *
 * @param kind            what this entry is
 * @param name            the Solidity name; {@code ""} for constructors
 * @param inputs          parameters (event params carry indexed flags)
 * @param outputs         return values; empty except for functions
 * @param stateMutability {@code pure}/{@code view}/{@code nonpayable}/
 *                        {@code payable}, or {@code ""} where the ABI
 *                        carries none (events, errors)
 */
public record AbiEntry(
        Kind kind,
        String name,
        List<AbiParam> inputs,
        List<AbiParam> outputs,
        String stateMutability) {

    /** The member of the family this entry is. */
    public enum Kind {
        FUNCTION, EVENT, CONSTRUCTOR, ERROR
    }

    public AbiEntry {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /** A function entry. */
    public static AbiEntry function(String name, List<AbiParam> inputs,
            List<AbiParam> outputs, String stateMutability) {
        return new AbiEntry(Kind.FUNCTION, name, inputs, outputs, stateMutability);
    }

    /** An event entry; the inputs carry the indexed flags. */
    public static AbiEntry event(String name, List<AbiParam> inputs) {
        return new AbiEntry(Kind.EVENT, name, inputs, List.of(), "");
    }

    /** The constructor entry (a contract has at most one). */
    public static AbiEntry constructor(List<AbiParam> inputs, String stateMutability) {
        return new AbiEntry(Kind.CONSTRUCTOR, "", inputs, List.of(), stateMutability);
    }

    /** A custom error entry. */
    public static AbiEntry error(String name, List<AbiParam> inputs) {
        return new AbiEntry(Kind.ERROR, name, inputs, List.of(), "");
    }

    /**
     * The canonical signature this entry hashes to — selector for
     * functions and errors, topic0 for events:
     * {@code transfer(address,uint256)}. Alias types are canonicalized
     * ({@code uint} → {@code uint256}, {@code int} → {@code int256},
     * also inside array suffixes) per the Solidity ABI rules.
     */
    public String signature() {
        StringJoiner types = new StringJoiner(",", name + "(", ")");
        for (AbiParam input : inputs) {
            types.add(canonicalType(input.type()));
        }
        return types.toString();
    }

    /**
     * {@code uint} and {@code int} written bare are aliases for their
     * 256-bit forms in canonical signatures; array suffixes ride along.
     */
    public static String canonicalType(String type) {
        if (type == null) {
            return "";
        }
        int bracket = type.indexOf('[');
        String base = bracket < 0 ? type : type.substring(0, bracket);
        String suffix = bracket < 0 ? "" : type.substring(bracket);
        if (base.equals("uint")) {
            return "uint256" + suffix;
        }
        if (base.equals("int")) {
            return "int256" + suffix;
        }
        return type;
    }

    /** True for {@code view}/{@code pure} functions — safe to eth_call. */
    public boolean readOnly() {
        return "view".equals(stateMutability) || "pure".equals(stateMutability);
    }
}
