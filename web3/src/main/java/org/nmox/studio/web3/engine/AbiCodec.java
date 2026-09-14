package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

/**
 * The Solidity ABI codec — head/tail encoding exactly per the official
 * ABI specification. It speaks {@code uintN}/{@code intN},
 * {@code address}, {@code bool}, {@code bytesN}, {@code bytes},
 * {@code string}, arrays of any of them to any depth ({@code T[]},
 * {@code T[k]}, {@code T[][2]}), and tuples/structs — including tuple
 * arrays, nested tuples, and tuples with dynamic members — with the
 * struct's members read from the ABI's {@code components}.
 *
 * <p>Every malformed argument throws {@link IllegalArgumentException}
 * with a human message that names the offending parameter (and, inside a
 * tuple, the component and its position); the UI can show it verbatim.
 */
public final class AbiCodec {

    private static final int WORD = 32;
    private static final BigInteger TWO_POW_256 = BigInteger.ONE.shiftLeft(256);

    /**
     * The most values one decode will render. Return data comes from an
     * RPC endpoint the IDE does not control; a hostile payload of nested
     * arrays with huge counts could otherwise ask for billions of
     * strings. Past this the decode is refused, never truncated.
     */
    static final int MAX_DECODED_VALUES = 100_000;

    /** Selector of {@code Error(string)} — the require/revert reason wrapper. */
    private static final String ERROR_STRING_SELECTOR = "08c379a0";
    /** Selector of {@code Panic(uint256)} — Solidity's checked-failure wrapper. */
    private static final String PANIC_SELECTOR = "4e487b71";

    private AbiCodec() {
    }

    // ---- encoding ----------------------------------------------------

    /**
     * Encodes a call to the function: {@code 0x} + 4-byte selector +
     * ABI-encoded arguments. Argument strings are what a person types:
     * decimal or 0x-hex for numbers, {@code true}/{@code false},
     * 0x-tolerant hex for address/bytes, literal text for strings
     * (surrounding double quotes stripped if present), and the
     * {@link AbiLiteral} list grammar for arrays and tuples —
     * {@code [a, b, c]}, a tuple as its components in order
     * ({@code ["0x…", "100"]}), a tuple array as a list of lists.
     */
    public static String encodeCall(AbiEntry function, List<String> args) {
        byte[] selector = Keccak256.selector(function.signature());
        return "0x" + Hex.toHex(selector) + Hex.toHex(encodeArgs(function.inputs(), args));
    }

    /**
     * ABI-encodes arguments against a parameter list without a selector
     * — what a constructor call appends to the creation bytecode.
     */
    public static byte[] encodeArgs(List<AbiParam> params, List<String> args) {
        if (args == null) {
            args = List.of();
        }
        if (params.size() != args.size()) {
            throw new IllegalArgumentException("Expected " + params.size()
                    + " argument" + (params.size() == 1 ? "" : "s")
                    + ", got " + args.size() + ".");
        }
        List<Enc> parts = new ArrayList<>(params.size());
        for (int i = 0; i < params.size(); i++) {
            AbiParam param = params.get(i);
            parts.add(encodeArgument(param, args.get(i), paramLabel(param, i)));
        }
        return encodeSequence(parts);
    }

    /**
     * The shape a field for this parameter expects, for its label and
     * tooltip: a tuple reads {@code [recipient: address, amount: uint256]},
     * a tuple array {@code [[…], …]}; every other type is its ABI type
     * string unchanged. A tuple the codec would refuse (no components)
     * falls back to the raw type — the refusal speaks at CALL time.
     */
    public static String inputShape(AbiParam param) {
        try {
            AbiType type = AbiType.of(param, paramLabel(param, 0));
            return type.containsTuple() ? type.shape() : param.type();
        } catch (IllegalArgumentException unshapeable) {
            return param.type();
        }
    }

    // ---- decoding ----------------------------------------------------

    /**
     * Decodes a call's return data against the function's outputs, one
     * display string per output: addresses 0x-prefixed lowercase,
     * numbers decimal, bools {@code true}/{@code false}, bytes 0x-hex,
     * strings as their UTF-8 text, arrays as {@code [a, b, c]}, tuples
     * as {@code (name: value, name: value)} (an unnamed component shows
     * its value alone).
     */
    public static List<String> decodeReturn(AbiEntry function, String hexData) {
        List<AbiParam> outputs = function.outputs();
        if (outputs.isEmpty()) {
            return List.of();
        }
        byte[] data = Hex.fromHex(hexData == null ? "" : hexData);
        if (data.length == 0) {
            throw new IllegalArgumentException("The call returned no data — "
                    + "is the contract actually deployed on this network?");
        }
        return decodeSequence(typesOf(outputs), data, 0, new Budget());
    }

    /**
     * Decodes one event log against the event's ABI entry: indexed
     * parameters from the topics, the rest ABI-decoded from the data
     * section. Per the spec an indexed {@code bytes}, {@code string},
     * array, or struct is stored only as the keccak hash of its
     * encoding — the value is gone, so it is shown honestly as
     * {@code hash:0x…}. Returns parameter name → display value in
     * declaration order; unnamed parameters become {@code arg0},
     * {@code arg1}, ...
     */
    public static Map<String, String> decodeEventLog(AbiEntry event,
            List<String> topics, String data) {
        Map<String, String> out = new LinkedHashMap<>();
        Budget budget = new Budget();

        List<AbiType> dataTypes = new ArrayList<>();
        List<AbiType> allTypes = new ArrayList<>(event.inputs().size());
        for (int i = 0; i < event.inputs().size(); i++) {
            AbiParam input = event.inputs().get(i);
            AbiType type = AbiType.of(input, paramLabel(input, i));
            allTypes.add(type);
            if (!input.indexed()) {
                dataTypes.add(type);
            }
        }
        byte[] dataBytes = Hex.fromHex(data == null ? "" : data);
        List<String> dataValues = dataTypes.isEmpty()
                ? List.of()
                : decodeSequence(dataTypes, dataBytes, 0, budget);

        int topicIndex = 1; // topics[0] is the event signature hash
        int dataIndex = 0;
        for (int i = 0; i < event.inputs().size(); i++) {
            AbiParam param = event.inputs().get(i);
            String name = param.name() == null || param.name().isBlank()
                    ? "arg" + i : param.name();
            if (param.indexed()) {
                if (topicIndex >= (topics == null ? 0 : topics.size())) {
                    throw new IllegalArgumentException("The log has too few topics for "
                            + event.name() + " — indexed parameter '" + name + "' is missing.");
                }
                String topic = topics.get(topicIndex++);
                AbiType type = allTypes.get(i);
                if (type.kind != AbiType.Kind.SCALAR || type.isDynamic()) {
                    // arrays, structs, bytes and string are stored hashed; the value is gone
                    out.put(name, "hash:0x"
                            + Hex.strip0x(topic).toLowerCase(Locale.ROOT));
                } else {
                    byte[] word = Hex.fromHex(topic);
                    out.put(name, decodeStatic(type, pad32(word), 0, budget));
                }
            } else {
                out.put(name, dataValues.get(dataIndex++));
            }
        }
        return out;
    }

    /**
     * Decodes a transaction's input data against an ABI (v2.44.0, the
     * inspector): matches the 4-byte selector to a function and decodes
     * the arguments. Returns {@code name(value, value, …)}, or null
     * when no function matches — the caller shows the raw selector
     * honestly rather than guessing.
     */
    public static String decodeCallInput(List<AbiEntry> abi, String inputHex) {
        String digits = inputHex == null ? "" : inputHex.startsWith("0x")
                ? inputHex.substring(2) : inputHex;
        if (digits.length() < 8) {
            return null;
        }
        String selector = digits.substring(0, 8).toLowerCase(java.util.Locale.ROOT);
        for (AbiEntry entry : abi) {
            if (entry.kind() != AbiEntry.Kind.FUNCTION) {
                continue;
            }
            String own = Hex.toHex(Keccak256.selector(entry.signature()));
            if (!own.equalsIgnoreCase(selector)) {
                continue;
            }
            List<AbiType> types = typesOf(entry.inputs());
            List<String> values = types.isEmpty() ? List.of()
                    : decodeSequence(types, Hex.fromHex(digits.substring(8)), 0, new Budget());
            return entry.name() + "(" + String.join(", ", values) + ")";
        }
        return null;
    }

    /**
     * Turns revert data into a human sentence: {@code Error(string)}
     * yields the reason text, {@code Panic(uint256)} its named code,
     * anything else {@code custom error 0x…}.
     */
    public static String decodeRevert(String hexData) {
        return decodeRevert(hexData, List.of());
    }

    /**
     * As {@link #decodeRevert(String)}, additionally matching custom
     * errors against the given ABI error entries by selector and
     * decoding their arguments when one matches.
     */
    public static String decodeRevert(String hexData, List<AbiEntry> errors) {
        String digits = Hex.strip0x(hexData == null ? "" : hexData.trim())
                .toLowerCase(Locale.ROOT);
        if (digits.isEmpty()) {
            return "Reverted without a reason.";
        }
        if (digits.length() < 8) {
            return "Reverted with unrecognizable data 0x" + digits + ".";
        }
        String selector = digits.substring(0, 8);
        byte[] payload;
        try {
            payload = Hex.fromHex(digits.substring(8));
        } catch (IllegalArgumentException oddHex) {
            return "Reverted with unrecognizable data 0x" + digits + ".";
        }
        if (ERROR_STRING_SELECTOR.equals(selector)) {
            try {
                return decodeSequence(typesOf(List.of(AbiParam.of("", "string"))),
                        payload, 0, new Budget()).get(0);
            } catch (RuntimeException malformed) {
                return "Reverted with a malformed Error(string) payload.";
            }
        }
        if (PANIC_SELECTOR.equals(selector)) {
            if (payload.length < WORD) {
                // Panic(uint256) always carries one full word; less is garbage
                return "Reverted with a malformed Panic(uint256) payload.";
            }
            try {
                BigInteger code = new BigInteger(1, payload);
                return "Panic 0x" + code.toString(16) + ": " + panicName(code.intValueExact());
            } catch (RuntimeException malformed) {
                return "Reverted with a malformed Panic(uint256) payload.";
            }
        }
        if (errors != null) {
            for (AbiEntry error : errors) {
                if (error.kind() != AbiEntry.Kind.ERROR) {
                    continue;
                }
                if (selector.equals(Hex.toHex(Keccak256.selector(error.signature())))) {
                    return describeCustomError(error, payload, selector);
                }
            }
        }
        return "custom error 0x" + selector;
    }

    // ---- the encoder core ---------------------------------------------

    /**
     * One encoded value: static values carry their inline words, dynamic
     * values carry their tail content (an offset word points at it).
     * A plain class, not a record — nothing outside this file sees it.
     */
    private static final class Enc {
        final boolean dynamic;
        final byte[] data;

        Enc(boolean dynamic, byte[] data) {
            this.dynamic = dynamic;
            this.data = data;
        }
    }

    /** A top-level field: scalars keep their v1 text rules, composites parse as a literal. */
    private static Enc encodeArgument(AbiParam param, String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter " + label + " has no value.");
        }
        AbiType type = AbiType.of(param, label);
        String trimmed = value.trim();
        if (type.kind == AbiType.Kind.SCALAR) {
            return encodeScalar(type.type, trimmed, false, label);
        }
        if (!trimmed.startsWith("[")) {
            throw new IllegalArgumentException(expectsList(type, label));
        }
        return encodeNode(type, AbiLiteral.parse(trimmed, label), label);
    }

    private static Enc encodeNode(AbiType type, AbiLiteral.Node node, String label) {
        switch (type.kind) {
            case SCALAR -> {
                if (!(node instanceof AbiLiteral.Scalar scalar)) {
                    throw new IllegalArgumentException("Parameter " + label + " is a "
                            + type.type + " — expected one value, got a list.");
                }
                return encodeScalar(type.type, scalar.text(), true, label);
            }
            case ARRAY -> {
                if (!(node instanceof AbiLiteral.Group group)) {
                    throw new IllegalArgumentException(expectsList(type, label));
                }
                List<AbiLiteral.Node> items = group.items();
                if (type.length >= 0 && items.size() != type.length) {
                    throw new IllegalArgumentException("Parameter " + label + " is a " + type.type
                            + " — expected exactly " + type.length + " elements, got "
                            + items.size() + ".");
                }
                List<Enc> encoded = new ArrayList<>(items.size());
                for (int i = 0; i < items.size(); i++) {
                    encoded.add(encodeNode(type.element, items.get(i), label + "[" + i + "]"));
                }
                byte[] body = encodeSequence(encoded);
                if (type.length >= 0) {
                    // a fixed array is itself dynamic exactly when its element type is
                    return new Enc(type.isDynamic(), body);
                }
                // dynamic T[]: count word + element sequence
                byte[] out = new byte[WORD + body.length];
                System.arraycopy(uintWord(BigInteger.valueOf(items.size())), 0, out, 0, WORD);
                System.arraycopy(body, 0, out, WORD, body.length);
                return new Enc(true, out);
            }
            default -> {
                if (!(node instanceof AbiLiteral.Group group)) {
                    throw new IllegalArgumentException(expectsList(type, label));
                }
                List<AbiLiteral.Node> items = group.items();
                if (items.size() != type.components.size()) {
                    throw new IllegalArgumentException("Parameter " + label + " is a tuple of "
                            + type.components.size() + " component"
                            + (type.components.size() == 1 ? "" : "s") + " " + type.shape()
                            + " — got " + items.size() + " value"
                            + (items.size() == 1 ? "" : "s") + ".");
                }
                List<Enc> encoded = new ArrayList<>(items.size());
                for (int i = 0; i < items.size(); i++) {
                    encoded.add(encodeNode(type.components.get(i), items.get(i),
                            AbiType.componentLabel(label, type.names.get(i), i)));
                }
                return new Enc(type.isDynamic(), encodeSequence(encoded));
            }
        }
    }

    private static String expectsList(AbiType type, String label) {
        if (type.kind == AbiType.Kind.TUPLE) {
            return "Parameter " + label + " is a tuple — write its components in order as "
                    + type.shape() + ".";
        }
        return "Parameter " + label + " expects an array like "
                + (type.containsTuple() ? type.shape() : "[1, 2, 3]") + ".";
    }

    /**
     * One scalar value. A top-level field keeps the v1 rule that a string
     * may be wrapped in quotes; a literal leaf arrives already unquoted
     * (and unescaped) from {@link AbiLiteral}, so it is taken verbatim.
     */
    private static Enc encodeScalar(String type, String value, boolean literalLeaf, String label) {
        String trimmed = value.trim();
        return switch (baseKind(type, label)) {
            case ADDRESS -> new Enc(false, encodeAddress(trimmed, label));
            case BOOL -> new Enc(false, encodeBool(trimmed, label));
            case UINT -> new Enc(false, encodeInteger(type, trimmed, label, false));
            case INT -> new Enc(false, encodeInteger(type, trimmed, label, true));
            case FIXED_BYTES -> new Enc(false, encodeFixedBytes(type, trimmed, label));
            case BYTES -> new Enc(true, lengthPrefixed(hexArgument(trimmed, label)));
            case STRING -> new Enc(true, lengthPrefixed(
                    (literalLeaf ? value : unquote(trimmed)).getBytes(StandardCharsets.UTF_8)));
        };
    }

    /** Head/tail assembly; offsets are relative to the sequence start. */
    private static byte[] encodeSequence(List<Enc> parts) {
        int headSize = 0;
        for (Enc part : parts) {
            headSize += part.dynamic ? WORD : part.data.length;
        }
        int tailSize = 0;
        for (Enc part : parts) {
            if (part.dynamic) {
                tailSize += part.data.length;
            }
        }
        byte[] out = new byte[headSize + tailSize];
        int headPos = 0;
        int tailPos = headSize;
        for (Enc part : parts) {
            if (part.dynamic) {
                System.arraycopy(uintWord(BigInteger.valueOf(tailPos)), 0, out, headPos, WORD);
                System.arraycopy(part.data, 0, out, tailPos, part.data.length);
                headPos += WORD;
                tailPos += part.data.length;
            } else {
                System.arraycopy(part.data, 0, out, headPos, part.data.length);
                headPos += part.data.length;
            }
        }
        return out;
    }

    private static byte[] encodeAddress(String value, String label) {
        String digits = Hex.strip0x(value);
        if (digits.length() != 40 || !isHex(digits)) {
            throw new IllegalArgumentException("Parameter " + label
                    + " needs an address — 40 hex digits, 0x-prefix optional.");
        }
        return leftPad(Hex.fromHex(digits));
    }

    private static byte[] encodeBool(String value, String label) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("true")) {
            return uintWord(BigInteger.ONE);
        }
        if (lower.equals("false")) {
            return uintWord(BigInteger.ZERO);
        }
        throw new IllegalArgumentException("Parameter " + label
                + " is a bool — write true or false.");
    }

    private static byte[] encodeInteger(String type, String value, String label,
            boolean signed) {
        int bits = integerBits(type, label);
        BigInteger v;
        try {
            String cleaned = value.replace("_", "");
            if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
                v = new BigInteger(cleaned.substring(2), 16);
            } else if (cleaned.startsWith("-0x") || cleaned.startsWith("-0X")) {
                v = new BigInteger(cleaned.substring(3), 16).negate();
            } else {
                v = new BigInteger(cleaned);
            }
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("Parameter " + label + " is a " + type
                    + " — '" + value + "' is not a number (decimal or 0x-hex).");
        }
        if (signed) {
            BigInteger half = BigInteger.ONE.shiftLeft(bits - 1);
            if (v.compareTo(half.negate()) < 0 || v.compareTo(half) >= 0) {
                throw new IllegalArgumentException("Parameter " + label + " is out of range for "
                        + type + " (" + half.negate() + " to " + half.subtract(BigInteger.ONE) + ").");
            }
            BigInteger twos = v.signum() < 0 ? v.add(TWO_POW_256) : v;
            return uintWord(twos);
        }
        if (v.signum() < 0 || v.bitLength() > bits) {
            throw new IllegalArgumentException("Parameter " + label + " is out of range for "
                    + type + " (0 to 2^" + bits + "-1).");
        }
        return uintWord(v);
    }

    private static byte[] encodeFixedBytes(String type, String value, String label) {
        int n = fixedBytesWidth(type, label);
        byte[] bytes = hexArgumentExact(value, n, type, label);
        byte[] word = new byte[WORD];
        System.arraycopy(bytes, 0, word, 0, n);
        return word;
    }

    // ---- the decoder core ---------------------------------------------

    /** The shared count of values one decode may still render. */
    private static final class Budget {
        private int left = MAX_DECODED_VALUES;

        void spend() {
            if (--left < 0) {
                throw new IllegalArgumentException("The data would decode to more than "
                        + MAX_DECODED_VALUES + " values — refusing to render it.");
            }
        }
    }

    private static List<AbiType> typesOf(List<AbiParam> params) {
        List<AbiType> types = new ArrayList<>(params.size());
        for (int i = 0; i < params.size(); i++) {
            types.add(AbiType.of(params.get(i), paramLabel(params.get(i), i)));
        }
        return types;
    }

    /** Decodes a head/tail sequence of types starting at {@code start}. */
    private static List<String> decodeSequence(List<AbiType> types, byte[] data, int start,
            Budget budget) {
        List<String> out = new ArrayList<>(types.size());
        long cursor = start;
        for (AbiType type : types) {
            int at = checkedPosition(cursor, type.type);
            if (type.isDynamic()) {
                int offset = intWordAt(data, at, type.type);
                out.add(decodeDynamic(type, data, checkedPosition((long) start + offset, type.type),
                        budget));
                cursor += WORD;
            } else {
                out.add(decodeStatic(type, data, at, budget));
                cursor += (long) WORD * type.staticWords();
            }
        }
        return out;
    }

    private static int checkedPosition(long position, String label) {
        if (position > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The data of '" + label
                    + "' carries an impossible offset/length (" + position + ").");
        }
        return (int) position;
    }

    private static String decodeDynamic(AbiType type, byte[] data, int pos, Budget budget) {
        budget.spend();
        switch (type.kind) {
            case SCALAR -> {
                int length = intWordAt(data, pos, type.type);
                byte[] content = slice(data, pos + WORD, length, type.type);
                return type.type.equals("string")
                        ? new String(content, StandardCharsets.UTF_8)
                        : "0x" + Hex.toHex(content);
            }
            case ARRAY -> {
                int count;
                int elementsStart;
                if (type.length < 0) {
                    count = intWordAt(data, pos, type.type);
                    elementsStart = checkedPosition((long) pos + WORD, type.type);
                    // every element is charged at least one head word; a count the
                    // data cannot hold is a lie about the payload, not a list to
                    // allocate (a zero-width element type like T[0][] is refused too)
                    long room = (data.length - (long) elementsStart) / WORD;
                    if (count > Math.max(0, room)) {
                        throw new IllegalArgumentException("The data of '" + type.type
                                + "' carries an impossible offset/length (" + count + ").");
                    }
                } else {
                    count = type.length;
                    elementsStart = pos;
                }
                return joinArray(decodeSequence(Collections.nCopies(count, type.element),
                        data, elementsStart, budget));
            }
            default -> {
                return joinTuple(type.names,
                        decodeSequence(type.components, data, pos, budget));
            }
        }
    }

    private static String decodeStatic(AbiType type, byte[] data, int pos, Budget budget) {
        budget.spend();
        switch (type.kind) {
            case ARRAY -> {
                List<String> elements = new ArrayList<>(type.length);
                long at = pos;
                for (int i = 0; i < type.length; i++) {
                    elements.add(decodeStatic(type.element, data, checkedPosition(at, type.type),
                            budget));
                    at += (long) WORD * type.element.staticWords();
                }
                return joinArray(elements);
            }
            case TUPLE -> {
                List<String> values = new ArrayList<>(type.components.size());
                long at = pos;
                for (AbiType component : type.components) {
                    values.add(decodeStatic(component, data, checkedPosition(at, component.type),
                            budget));
                    at += (long) WORD * component.staticWords();
                }
                return joinTuple(type.names, values);
            }
            default -> {
                String t = type.type;
                return switch (baseKind(t, t)) {
                    case ADDRESS -> "0x" + Hex.toHex(slice(data, pos + 12, 20, t));
                    case BOOL -> new BigInteger(1, slice(data, pos, WORD, t)).signum() != 0
                            ? "true" : "false";
                    case UINT -> new BigInteger(1, slice(data, pos, WORD, t)).toString();
                    case INT -> decodeSignedWord(slice(data, pos, WORD, t));
                    case FIXED_BYTES -> "0x" + Hex.toHex(
                            slice(data, pos, fixedBytesWidth(t, t), t));
                    case BYTES, STRING -> throw new IllegalArgumentException(
                            "'" + t + "' is dynamic, not static."); // unreachable via isDynamic
                };
            }
        }
    }

    private static String decodeSignedWord(byte[] word) {
        BigInteger raw = new BigInteger(1, word);
        // the word is sign-extended to 256 bits regardless of declared width
        if (raw.testBit(255)) {
            raw = raw.subtract(TWO_POW_256);
        }
        return raw.toString();
    }

    // ---- shared type grammar -------------------------------------------

    private enum BaseKind { ADDRESS, BOOL, UINT, INT, FIXED_BYTES, BYTES, STRING }

    private static BaseKind baseKind(String type, String label) {
        if (type.equals("address")) {
            return BaseKind.ADDRESS;
        }
        if (type.equals("bool")) {
            return BaseKind.BOOL;
        }
        if (type.equals("string")) {
            return BaseKind.STRING;
        }
        if (type.equals("bytes")) {
            return BaseKind.BYTES;
        }
        if (type.startsWith("bytes")) {
            return BaseKind.FIXED_BYTES;
        }
        if (type.startsWith("uint")) {
            return BaseKind.UINT;
        }
        if (type.startsWith("int")) {
            return BaseKind.INT;
        }
        throw new IllegalArgumentException("Parameter " + label
                + " has unsupported type '" + type + "'.");
    }

    private static int integerBits(String type, String label) {
        String digits = type.startsWith("uint") ? type.substring(4) : type.substring(3);
        if (digits.isEmpty()) {
            return 256;
        }
        int bits;
        try {
            bits = Integer.parseInt(digits);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported type '" + type + "'.");
        }
        if (bits < 8 || bits > 256 || bits % 8 != 0) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported type '" + type + "' — width must be 8..256 in steps of 8.");
        }
        return bits;
    }

    private static int fixedBytesWidth(String type, String label) {
        int n;
        try {
            n = Integer.parseInt(type.substring("bytes".length()));
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported type '" + type + "'.");
        }
        if (n < 1 || n > 32) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported type '" + type + "' — bytesN needs N of 1..32.");
        }
        return n;
    }

    // ---- argument text helpers ------------------------------------------

    /** Strips one pair of surrounding double quotes and unescapes \" and \\. */
    static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return s;
    }

    private static byte[] hexArgument(String value, String label) {
        try {
            return Hex.fromHex(value);
        } catch (IllegalArgumentException bad) {
            throw new IllegalArgumentException("Parameter " + label
                    + " expects hex data — " + bad.getMessage());
        }
    }

    private static byte[] hexArgumentExact(String value, int n, String type, String label) {
        byte[] bytes = hexArgument(value, label);
        if (bytes.length != n) {
            throw new IllegalArgumentException("Parameter " + label + " is a " + type
                    + " — expected exactly " + n + " bytes, got " + bytes.length + ".");
        }
        return bytes;
    }

    // ---- byte plumbing ---------------------------------------------------

    private static byte[] uintWord(BigInteger nonNegative) {
        byte[] magnitude = nonNegative.toByteArray();
        int start = magnitude.length > WORD ? magnitude.length - WORD : 0;
        int length = Math.min(magnitude.length, WORD);
        byte[] word = new byte[WORD];
        System.arraycopy(magnitude, start, word, WORD - length, length);
        return word;
    }

    private static byte[] leftPad(byte[] bytes) {
        if (bytes.length == WORD) {
            return bytes;
        }
        byte[] word = new byte[WORD];
        System.arraycopy(bytes, 0, word, WORD - bytes.length, bytes.length);
        return word;
    }

    private static byte[] pad32(byte[] bytes) {
        return bytes.length >= WORD ? bytes : leftPad(bytes);
    }

    /** Length word + content right-padded to a word boundary. */
    private static byte[] lengthPrefixed(byte[] content) {
        int padded = (content.length + WORD - 1) / WORD * WORD;
        byte[] out = new byte[WORD + padded];
        System.arraycopy(uintWord(BigInteger.valueOf(content.length)), 0, out, 0, WORD);
        System.arraycopy(content, 0, out, WORD, content.length);
        return out;
    }

    private static byte[] slice(byte[] data, int from, int length, String label) {
        if (from < 0 || length < 0 || (long) from + length > data.length) {
            throw new IllegalArgumentException("The data is shorter than the ABI of '"
                    + label + "' expects — got " + data.length + " bytes.");
        }
        byte[] out = new byte[length];
        System.arraycopy(data, from, out, 0, length);
        return out;
    }

    private static int intWordAt(byte[] data, int pos, String label) {
        BigInteger value = new BigInteger(1, slice(data, pos, WORD, label));
        try {
            return value.intValueExact();
        } catch (ArithmeticException tooBig) {
            throw new IllegalArgumentException("The data of '" + label
                    + "' carries an impossible offset/length (" + value + ").");
        }
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static String joinArray(List<String> elements) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (String element : elements) {
            joiner.add(element);
        }
        return joiner.toString();
    }

    private static String joinTuple(List<String> names, List<String> values) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < values.size(); i++) {
            String name = names.get(i);
            joiner.add(name.isBlank() ? values.get(i) : name + ": " + values.get(i));
        }
        return joiner.toString();
    }

    private static String paramLabel(AbiParam param, int index) {
        return param.name() == null || param.name().isBlank()
                ? "#" + (index + 1) : "'" + param.name() + "'";
    }

    private static String describeCustomError(AbiEntry error, byte[] payload,
            String selector) {
        try {
            List<AbiType> types = typesOf(error.inputs());
            List<String> values = types.isEmpty()
                    ? List.of()
                    : decodeSequence(types, payload, 0, new Budget());
            StringJoiner joiner = new StringJoiner(", ", error.name() + "(", ")");
            for (int i = 0; i < values.size(); i++) {
                String name = error.inputs().get(i).name();
                joiner.add(name == null || name.isBlank()
                        ? values.get(i) : name + ": " + values.get(i));
            }
            return joiner.toString();
        } catch (RuntimeException malformed) {
            return "custom error 0x" + selector + " (" + error.name()
                    + ", but its data would not decode)";
        }
    }

    private static String panicName(int code) {
        return switch (code) {
            case 0x00 -> "generic compiler panic";
            case 0x01 -> "assertion failed";
            case 0x11 -> "arithmetic overflow";
            case 0x12 -> "division by zero";
            case 0x21 -> "invalid enum";
            case 0x22 -> "corrupted storage byte array";
            case 0x31 -> "pop on an empty array";
            case 0x32 -> "index out of bounds";
            case 0x41 -> "out of memory";
            case 0x51 -> "call to an uninitialized function pointer";
            default -> "unknown panic code";
        };
    }
}
