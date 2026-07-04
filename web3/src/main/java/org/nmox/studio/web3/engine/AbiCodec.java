package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

/**
 * The Solidity ABI codec — head/tail encoding exactly per the official
 * ABI specification. v1 speaks {@code uintN}/{@code intN},
 * {@code address}, {@code bool}, {@code bytesN}, {@code bytes},
 * {@code string}, and single-level arrays of those (dynamic {@code T[]}
 * and fixed {@code T[k]}). Tuples/structs and nested arrays are refused
 * with a status-bar-ready sentence — {@code cast} covers them until v2.
 *
 * <p>Every malformed argument throws {@link IllegalArgumentException}
 * with a human message that names the offending parameter; the UI can
 * show it verbatim.
 */
public final class AbiCodec {

    private static final int WORD = 32;
    private static final BigInteger TWO_POW_256 = BigInteger.ONE.shiftLeft(256);

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
     * (surrounding double quotes stripped if present), and
     * {@code [a, b, c]} for arrays.
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
            parts.add(encodeValue(param.type(), args.get(i), paramLabel(param, i)));
        }
        return encodeSequence(parts);
    }

    // ---- decoding ----------------------------------------------------

    /**
     * Decodes a call's return data against the function's outputs, one
     * display string per output: addresses 0x-prefixed lowercase,
     * numbers decimal, bools {@code true}/{@code false}, bytes 0x-hex,
     * strings as their UTF-8 text, arrays as {@code [a, b, c]}.
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
        List<String> types = new ArrayList<>(outputs.size());
        for (AbiParam output : outputs) {
            types.add(output.type());
        }
        return decodeSequence(types, data, 0);
    }

    /**
     * Decodes one event log against the event's ABI entry: indexed
     * parameters from the topics (dynamic indexed types are only their
     * hash on-chain, shown as {@code hash:0x…}), the rest ABI-decoded
     * from the data section. Returns parameter name → display value in
     * declaration order; unnamed parameters become {@code arg0},
     * {@code arg1}, ...
     */
    public static Map<String, String> decodeEventLog(AbiEntry event,
            List<String> topics, String data) {
        Map<String, String> out = new LinkedHashMap<>();

        List<String> dataTypes = new ArrayList<>();
        for (AbiParam input : event.inputs()) {
            if (!input.indexed()) {
                dataTypes.add(input.type());
            }
        }
        byte[] dataBytes = Hex.fromHex(data == null ? "" : data);
        List<String> dataValues = dataTypes.isEmpty()
                ? List.of()
                : decodeSequence(dataTypes, dataBytes, 0);

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
                if (isDynamic(param.type())) {
                    // dynamic indexed values are stored hashed; the value is gone
                    out.put(name, "hash:0x"
                            + Hex.strip0x(topic).toLowerCase(Locale.ROOT));
                } else {
                    byte[] word = Hex.fromHex(topic);
                    out.put(name, decodeStatic(param.type(), pad32(word), 0, name));
                }
            } else {
                out.put(name, dataValues.get(dataIndex++));
            }
        }
        return out;
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
                return decodeSequence(List.of("string"), payload, 0).get(0);
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

    private static Enc encodeValue(String type, String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter " + label + " has no value.");
        }
        String trimmed = value.trim();
        if (type.startsWith("tuple") || type.startsWith("(")) {
            throw new IllegalArgumentException(
                    "Tuple parameters aren't supported yet — use cast for this call.");
        }
        if (type.endsWith("]")) {
            return encodeArray(type, trimmed, label);
        }
        return switch (baseKind(type, label)) {
            case ADDRESS -> new Enc(false, encodeAddress(trimmed, label));
            case BOOL -> new Enc(false, encodeBool(trimmed, label));
            case UINT -> new Enc(false, encodeInteger(type, trimmed, label, false));
            case INT -> new Enc(false, encodeInteger(type, trimmed, label, true));
            case FIXED_BYTES -> new Enc(false, encodeFixedBytes(type, trimmed, label));
            case BYTES -> new Enc(true, lengthPrefixed(hexArgument(trimmed, label)));
            case STRING -> new Enc(true,
                    lengthPrefixed(unquote(trimmed).getBytes(StandardCharsets.UTF_8)));
        };
    }

    private static Enc encodeArray(String type, String value, String label) {
        int bracket = type.lastIndexOf('[');
        String base = type.substring(0, bracket);
        String sizeSpec = type.substring(bracket + 1, type.length() - 1);
        if (base.endsWith("]")) {
            throw new IllegalArgumentException(
                    "Nested arrays aren't supported yet — use cast for this call.");
        }
        if (base.startsWith("tuple") || base.startsWith("(")) {
            throw new IllegalArgumentException(
                    "Tuple parameters aren't supported yet — use cast for this call.");
        }
        List<String> elements = splitArray(value, label);
        List<Enc> encoded = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            encoded.add(encodeValue(base, elements.get(i), label + "[" + i + "]"));
        }
        if (sizeSpec.isEmpty()) {
            // dynamic T[]: count word + element sequence
            byte[] body = encodeSequence(encoded);
            byte[] out = new byte[WORD + body.length];
            System.arraycopy(uintWord(BigInteger.valueOf(elements.size())), 0, out, 0, WORD);
            System.arraycopy(body, 0, out, WORD, body.length);
            return new Enc(true, out);
        }
        int expected = parseArraySize(sizeSpec, type, label);
        if (elements.size() != expected) {
            throw new IllegalArgumentException("Parameter " + label + " is a " + type
                    + " — expected exactly " + expected + " elements, got "
                    + elements.size() + ".");
        }
        byte[] body = encodeSequence(encoded);
        // a fixed array is itself dynamic exactly when its element type is
        return new Enc(isDynamic(base), body);
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

    /** Decodes a head/tail sequence of types starting at {@code start}. */
    private static List<String> decodeSequence(List<String> types, byte[] data, int start) {
        List<String> out = new ArrayList<>(types.size());
        int cursor = start;
        for (String type : types) {
            if (isDynamic(type)) {
                int offset = intWordAt(data, cursor, type);
                out.add(decodeDynamic(type, data, start + offset));
                cursor += WORD;
            } else {
                out.add(decodeStatic(type, data, cursor, type));
                cursor += WORD * staticWords(type);
            }
        }
        return out;
    }

    private static String decodeDynamic(String type, byte[] data, int pos) {
        if (type.equals("bytes")) {
            int length = intWordAt(data, pos, type);
            return "0x" + Hex.toHex(slice(data, pos + WORD, length, type));
        }
        if (type.equals("string")) {
            int length = intWordAt(data, pos, type);
            return new String(slice(data, pos + WORD, length, type), StandardCharsets.UTF_8);
        }
        if (type.endsWith("]")) {
            int bracket = type.lastIndexOf('[');
            String base = type.substring(0, bracket);
            String sizeSpec = type.substring(bracket + 1, type.length() - 1);
            int count;
            int elementsStart;
            if (sizeSpec.isEmpty()) {
                count = intWordAt(data, pos, type);
                elementsStart = pos + WORD;
            } else {
                count = parseArraySize(sizeSpec, type, type);
                elementsStart = pos;
            }
            List<String> elementTypes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                elementTypes.add(base);
            }
            return joinArray(decodeSequence(elementTypes, data, elementsStart));
        }
        throw new IllegalArgumentException("Unsupported dynamic type '" + type + "'.");
    }

    private static String decodeStatic(String type, byte[] data, int pos, String label) {
        if (type.endsWith("]")) {
            int bracket = type.lastIndexOf('[');
            String base = type.substring(0, bracket);
            int count = parseArraySize(
                    type.substring(bracket + 1, type.length() - 1), type, label);
            List<String> elements = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                elements.add(decodeStatic(base, data, pos + i * WORD * staticWords(base), label));
            }
            return joinArray(elements);
        }
        return switch (baseKind(type, label)) {
            case ADDRESS -> "0x" + Hex.toHex(slice(data, pos + 12, 20, label));
            case BOOL -> new BigInteger(1, slice(data, pos, WORD, label)).signum() != 0
                    ? "true" : "false";
            case UINT -> new BigInteger(1, slice(data, pos, WORD, label)).toString();
            case INT -> decodeSignedWord(slice(data, pos, WORD, label), integerBits(type, label));
            case FIXED_BYTES -> "0x" + Hex.toHex(
                    slice(data, pos, fixedBytesWidth(type, label), label));
            case BYTES, STRING -> throw new IllegalArgumentException(
                    "'" + type + "' is dynamic, not static."); // unreachable via isDynamic
        };
    }

    private static String decodeSignedWord(byte[] word, int bits) {
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
        if (type.startsWith("tuple") || type.startsWith("(")) {
            throw new IllegalArgumentException(
                    "Tuple parameters aren't supported yet — use cast for this call.");
        }
        throw new IllegalArgumentException("Parameter " + label
                + " has unsupported type '" + type + "'.");
    }

    /** True when the type's encoding lives in the tail (offset in the head). */
    static boolean isDynamic(String type) {
        if (type.equals("bytes") || type.equals("string")) {
            return true;
        }
        if (type.endsWith("]")) {
            int bracket = type.lastIndexOf('[');
            String sizeSpec = type.substring(bracket + 1, type.length() - 1);
            if (sizeSpec.isEmpty()) {
                return true; // T[] is always dynamic
            }
            return isDynamic(type.substring(0, bracket)); // T[k] follows T
        }
        return false;
    }

    /** How many 32-byte words a static type occupies inline. */
    private static int staticWords(String type) {
        if (type.endsWith("]")) {
            int bracket = type.lastIndexOf('[');
            int count = parseArraySize(
                    type.substring(bracket + 1, type.length() - 1), type, type);
            return count * staticWords(type.substring(0, bracket));
        }
        return 1;
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

    private static int parseArraySize(String sizeSpec, String type, String label) {
        try {
            int size = Integer.parseInt(sizeSpec);
            if (size < 0) {
                throw new NumberFormatException(sizeSpec);
            }
            return size;
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Parameter " + label
                    + " has unsupported array type '" + type + "'.");
        }
    }

    // ---- argument text helpers ------------------------------------------

    /**
     * Splits {@code [a, "b,c", [nested is refused elsewhere]]} on
     * top-level commas, respecting double quotes and inner brackets.
     */
    static List<String> splitArray(String raw, String label) {
        String trimmed = raw.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '['
                || trimmed.charAt(trimmed.length() - 1) != ']') {
            throw new IllegalArgumentException("Parameter " + label
                    + " expects an array like [1, 2, 3].");
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        List<String> out = new ArrayList<>();
        if (body.isEmpty()) {
            return out;
        }
        int depth = 0;
        boolean inQuote = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inQuote) {
                current.append(c);
                if (c == '\\' && i + 1 < body.length()) {
                    current.append(body.charAt(++i));
                } else if (c == '"') {
                    inQuote = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inQuote = true;
                    current.append(c);
                }
                case '[' -> {
                    depth++;
                    current.append(c);
                }
                case ']' -> {
                    depth--;
                    current.append(c);
                }
                case ',' -> {
                    if (depth == 0) {
                        out.add(current.toString().trim());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
                default -> current.append(c);
            }
        }
        out.add(current.toString().trim());
        return out;
    }

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
        if (from < 0 || length < 0 || from + length > data.length) {
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

    private static String paramLabel(AbiParam param, int index) {
        return param.name() == null || param.name().isBlank()
                ? "#" + (index + 1) : "'" + param.name() + "'";
    }

    private static String describeCustomError(AbiEntry error, byte[] payload,
            String selector) {
        try {
            List<String> types = new ArrayList<>();
            for (AbiParam input : error.inputs()) {
                types.add(input.type());
            }
            List<String> values = types.isEmpty()
                    ? List.of()
                    : decodeSequence(types, payload, 0);
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
