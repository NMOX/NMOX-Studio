package org.nmox.studio.web3.model;

/**
 * One input or output parameter of an ABI entry.
 *
 * @param name    the parameter name from the ABI JSON, or {@code ""} when
 *                the contract left it unnamed (decoders label those
 *                {@code arg0}, {@code arg1}, ...)
 * @param type    the Solidity ABI type string, e.g. {@code uint256},
 *                {@code address}, {@code bytes32[]}; tuples arrive as
 *                {@code tuple} and are honestly refused by the codec
 * @param indexed true for an event parameter that lives in the log topics
 *                rather than the data section; always false for function
 *                parameters
 */
public record AbiParam(String name, String type, boolean indexed) {

    /** A non-indexed parameter — the usual case for functions. */
    public static AbiParam of(String name, String type) {
        return new AbiParam(name, type, false);
    }
}
