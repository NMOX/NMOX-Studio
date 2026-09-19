package org.nmox.studio.rack.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A rack as a file another NMOX Studio user can mount (v2.176.0).
 *
 * <p>The patch format ({@link RackIO}) has always been a file, and the drop-in
 * presets since v1.294.0 proved it travels: a saved patch dropped into
 * {@code ~/.nmox/presets.d} appears on the Presets menu. What was missing is
 * the pair of doors — Share writes the patch somewhere the user chooses, Import
 * reads one from wherever it arrived — and the two things a file from another
 * machine needs that a file from this one does not:
 *
 * <ul>
 * <li><b>Portability without disclosure.</b> A device's state can hold an
 * absolute path (TAIL's file, an INSPECTOR entry, a CMD working directory), and
 * a path under the sender's home names the sender: {@code /Users/david/…} is a
 * username. {@link #export} rewrites EVERY occurrence of the sender's home that
 * sits at a path boundary inside a state value to {@code ~} — the value that is
 * a path, and the path inside a command ({@code tail -f /Users/david/logs/app.log},
 * {@code ssh -i /Users/david/.ssh/key host}, {@code LOG=/Users/david/x}) alike.
 * Until v2.179.0 only a value that STARTED with the home was rewritten, and a
 * SOLDER command left with the username in it. {@link #imported} expands
 * {@code ~} at the same boundaries to the receiver's home — it has to, because
 * SOLDER runs argv with no shell and nothing else would. A remote path
 * ({@code host:/Users/x}, {@code host:~/dir}) and a URL tilde
 * ({@code http://host/~user/}) are not the sender's disk and are left alone in
 * both directions. Nothing else in the state is touched — the whole point of
 * sharing a rack is the commands and settings in it, the receiver is shown them
 * before mounting, and {@link #audit} shows the SENDER what is about to leave.</li>
 * <li><b>Arrival at rest.</b> A shared rack never runs anything by being
 * imported. Loading a patch spawns nothing by construction, and every command
 * device gates its own GO on Workspace Trust; but a REFLEX saved {@code armed}
 * would start watching on mount, a TEMPO saved {@code running} would start
 * firing triggers into whatever it was cabled to, and a TAIL saved
 * {@code follow} would start polling a path the sender chose. Which keys those
 * are is asked of the device, never listed here (the {@code selfStartingByType}
 * overloads; {@code devices.SelfStarting} is the installed answer):
 * {@link #imported} sets them off and {@link #inspect} counts the devices, so
 * the receiver reads "3 devices arrive at rest" rather than discovering a clock
 * they never started.</li>
 * <li><b>Honesty about what is inside.</b> {@link #inspect} lists the devices
 * (naming any this install does not have — they mount as placeholders that keep
 * their slot and cables, the v1.54.0 rule), the cable count, and every setting
 * that reads like a command, path or address, so the decision to mount is made
 * on what the file says, not on who sent it.</li>
 * </ul>
 *
 * <p>Pure: every method takes what it needs and touches no disk, so the rules
 * are unit tests rather than a walk. (The two-argument {@link #imported} and
 * {@link #inspect} ask the device catalog which switches start something; the
 * three-argument forms take that answer as a function.)
 */
public final class RackShare {

    /** The header a shared file carries beside the patch; absent on a plain patch. */
    public static final String SHARED = "shared";

    /**
     * A resolver's answer meaning "every switch": any state value that reads
     * {@code true} arrives {@code false}. Given for a device this class cannot
     * ask — an installed extension (whose toggles run plugin code on restore)
     * and a type this install does not have (a MISSING placeholder keeps its
     * state verbatim, and the real device may be installed later).
     */
    public static final Set<String> EVERY_SWITCH = Set.of("*");

    /** What a shared file holds, read before anything mounts. */
    public record Manifest(List<Device> devices, int cables, List<Setting> settings,
            List<String> unknownTypes, int atRest, String sharedBy, RackCard card) {

        public Manifest {
            devices = List.copyOf(devices);
            settings = List.copyOf(settings);
            unknownTypes = List.copyOf(unknownTypes);
            card = card == null ? RackCard.EMPTY : card;
        }

        /** True when this install can mount every device the file names. */
        public boolean complete() {
            return unknownTypes.isEmpty();
        }
    }

    /** One device in the shared file: its type id and whether this install knows it. */
    public record Device(String typeId, boolean known) {
    }

    /** A setting the receiver should read before mounting: a command, a path, an address. */
    public record Setting(String typeId, String key, String value) {
    }

    /**
     * What the SENDER should read before the file leaves: the settings the
     * receiver will be shown, the values that still name somebody's home after
     * the rewrite, and the values that look like a credential. A secret-looking
     * value is MASKED in every list — the audit must never be a second copy of
     * the secret it warns about.
     */
    public record Audit(List<Setting> settings, List<Setting> personalPaths, List<Setting> secretLooking) {

        public Audit {
            settings = List.copyOf(settings);
            personalPaths = List.copyOf(personalPaths);
            secretLooking = List.copyOf(secretLooking);
        }

        /** True when nothing personal or secret-looking was found. */
        public boolean clean() {
            return personalPaths.isEmpty() && secretLooking.isEmpty();
        }
    }

    private RackShare() {
    }

    /**
     * The patch made portable, saying nothing about itself: see
     * {@link #export(JSONObject, Path, String, RackCard)}.
     */
    public static JSONObject export(JSONObject patch, Path home, String productVersion) {
        return export(patch, home, productVersion, RackCard.EMPTY);
    }

    /**
     * The patch made portable: a {@code shared} header naming the product version
     * it came from plus whatever {@code card} says (name, description, author,
     * kinds, requires — only the fields the sender filled), and every occurrence
     * of {@code home} at a path boundary inside a state value rewritten to
     * {@code ~}. The receiver's product version, not the sender's, decides what
     * the file means, so nothing about the machine is recorded — no user name,
     * no host, no date; an author appears only when the sender typed one.
     */
    public static JSONObject export(JSONObject patch, Path home, String productVersion, RackCard card) {
        JSONObject out = new JSONObject(patch.toString());
        JSONObject header = new JSONObject();
        header.put("product", productVersion == null ? "" : productVersion);
        (card == null ? RackCard.EMPTY : card).writeTo(header);
        out.put(SHARED, header);
        // compared with one separator so a Windows home (C:\Users\x) matches a value
        // spelled either way; the shared tail is always written with '/' — the file
        // travels between platforms, and imported() expands it with the receiver's
        String prefix = home == null ? null : slashes(home.toAbsolutePath().normalize().toString());
        // hideHome returns the value untouched for a null prefix, but the header
        // above must be written either way — and rewriteStates is what refuses a
        // device slot that is not an object, which export promises by name
        rewriteStates(out, value -> hideHome(value, prefix));
        return out;
    }

    /**
     * A shared file made mountable here, asking the installed devices which of
     * their switches start something: see
     * {@link #imported(JSONObject, Path, Function)}.
     */
    public static JSONObject imported(JSONObject shared, Path home) {
        return imported(shared, home, org.nmox.studio.rack.devices.SelfStarting::keysFor);
    }

    /**
     * A shared file made mountable here: {@code ~} expanded to this user's home,
     * every self-starting flag set off, and the {@code shared} header dropped so
     * what lands in the rack is a plain patch a later Save Patch writes as one.
     *
     * @param selfStartingByType device type id → the state keys whose restore
     *        starts something ({@code RackDevice.selfStartingKeys()} for a
     *        built-in), or {@link #EVERY_SWITCH}; a null function or a null
     *        answer is read as {@link #EVERY_SWITCH}, the conservative one
     */
    public static JSONObject imported(JSONObject shared, Path home,
            Function<String, Set<String>> selfStartingByType) {
        JSONObject out = new JSONObject(shared.toString());
        out.remove(SHARED);
        String prefix = home == null ? null : home.toAbsolutePath().normalize().toString();
        JSONArray devices = out.optJSONArray("devices");
        if (devices == null) {
            return out;
        }
        for (int i = 0; i < devices.length(); i++) {
            JSONObject dj = deviceAt(devices, i);
            JSONObject state = dj.optJSONObject("state");
            if (state == null) {
                continue;
            }
            // asked only once a switch reads on: for a built-in, asking builds the device
            Set<String> off = null;
            for (String key : new ArrayList<>(state.keySet())) {
                String value = state.optString(key, "");
                if (readsOn(value)) {
                    if (off == null) {
                        off = keysOf(selfStartingByType, dj.optString("type", "?"));
                    }
                    if (arrivesOff(off, key)) {
                        state.put(key, "false");
                    }
                } else {
                    // written back only when it changed: a value that was not a
                    // string stays what it was, for RackIO to judge as it always has
                    String expanded = expandHome(value, prefix, java.io.File.separatorChar);
                    if (!expanded.equals(value)) {
                        state.put(key, expanded);
                    }
                }
            }
        }
        return out;
    }

    /**
     * What the file holds, asking the installed devices which switches start
     * something: see {@link #inspect(JSONObject, Predicate, Function)}.
     */
    public static Manifest inspect(JSONObject shared, Predicate<String> known) {
        return inspect(shared, known, org.nmox.studio.rack.devices.SelfStarting::keysFor);
    }

    /**
     * What the file holds, for the receiver to read before mounting: the card it
     * carries, every device with whether {@code known} accepts its type id, the
     * cable count, the settings worth reading, the types this install lacks, and
     * how many DEVICES were saved with a self-starting switch on (and will arrive
     * at rest) — by the same {@code selfStartingByType} rule {@link #imported}
     * applies, so the count and the mount cannot disagree.
     */
    public static Manifest inspect(JSONObject shared, Predicate<String> known,
            Function<String, Set<String>> selfStartingByType) {
        List<Device> devices = new ArrayList<>();
        List<Setting> settings = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        int atRest = 0;
        JSONArray deviceArr = shared.optJSONArray("devices");
        if (deviceArr != null) {
            for (int i = 0; i < deviceArr.length(); i++) {
                JSONObject dj = deviceAt(deviceArr, i);
                String typeId = dj.optString("type", "?");
                boolean isKnown = known.test(typeId);
                devices.add(new Device(typeId, isKnown));
                if (!isKnown && !unknown.contains(typeId)) {
                    unknown.add(typeId);
                }
                JSONObject state = dj.optJSONObject("state");
                if (state == null) {
                    continue;
                }
                Set<String> off = null;
                boolean rests = false;
                for (String key : state.keySet()) {
                    String value = state.optString(key, "");
                    if (readsOn(value)) {
                        if (off == null) {
                            off = keysOf(selfStartingByType, typeId);
                        }
                        rests |= arrivesOff(off, key);
                    } else if (worthReading(value)) {
                        settings.add(new Setting(typeId, key, value));
                    }
                }
                if (rests) {
                    atRest++;
                }
            }
        }
        JSONArray cables = shared.optJSONArray("cables");
        JSONObject header = shared.optJSONObject(SHARED);
        String sharedBy = header == null ? null : header.optString("product", "");
        return new Manifest(devices, cables == null ? 0 : cables.length(), settings, unknown, atRest,
                sharedBy, RackCard.of(shared));
    }

    /**
     * What the sender should see before the file leaves, read from the document
     * {@link #export} produced (so the home rewrite has already happened and a
     * path listed here is one the rewrite could not or should not touch).
     */
    public static Audit audit(JSONObject shared) {
        List<Setting> settings = new ArrayList<>();
        List<Setting> personal = new ArrayList<>();
        List<Setting> secrets = new ArrayList<>();
        JSONArray deviceArr = shared.optJSONArray("devices");
        if (deviceArr != null) {
            for (int i = 0; i < deviceArr.length(); i++) {
                JSONObject dj = deviceAt(deviceArr, i);
                String typeId = dj.optString("type", "?");
                JSONObject state = dj.optJSONObject("state");
                if (state == null) {
                    continue;
                }
                for (String key : state.keySet()) {
                    String value = state.optString(key, "");
                    boolean secret = looksSecret(value);
                    Setting shown = new Setting(typeId, key, secret ? masked(value) : value);
                    if (worthReading(value)) {
                        settings.add(shown);
                    }
                    if (namesAHome(value)) {
                        personal.add(shown);
                    }
                    if (secret) {
                        secrets.add(shown);
                    }
                }
            }
        }
        return new Audit(settings, personal, secrets);
    }

    /** True when the file carries the {@code shared} header — it came through Share, not Save Patch. */
    public static boolean isShared(JSONObject doc) {
        return doc.has(SHARED);
    }

    /**
     * A value the receiver should see before mounting: anything that could be a
     * command, a path or an address. Knob positions ({@code "2"}), switches
     * ({@code "false"}) and short words are the device's own business.
     */
    static boolean worthReading(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (isNumber(v) || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return false;
        }
        return v.contains(" ") || v.contains("/") || v.contains("\\") || v.contains("://")
                || v.toLowerCase(Locale.ROOT).startsWith("~");
    }

    /** {@code -12}, {@code 3.5}: a knob position or a count — one linear pass, no regex. */
    static boolean isNumber(String v) {
        int i = v.startsWith("-") ? 1 : 0;
        boolean digits = false;
        boolean dot = false;
        for (; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                digits = true;
            } else if (c == '.' && !dot && digits) {
                dot = true;
                digits = false;
            } else {
                return false;
            }
        }
        return digits;
    }

    private static Set<String> keysOf(Function<String, Set<String>> byType, String typeId) {
        Set<String> keys = byType == null ? null : byType.apply(typeId);
        return keys == null ? EVERY_SWITCH : keys;
    }

    /**
     * A switch saved ON, read the way {@code RackDevice.param} restores one
     * ({@code Boolean.parseBoolean}: {@code true} in any case, nothing else).
     * Only such a value can start anything, so only such a value is ever set
     * off — a self-starting key holding anything else is already at rest.
     */
    private static boolean readsOn(String value) {
        return "true".equalsIgnoreCase(value);
    }

    /**
     * THE decision, in one place: would restoring {@code key=value} on a device
     * of {@code typeId} start something by itself? {@link #imported} sets off
     * exactly the settings this answers true for, {@link #inspect} counts them,
     * and the community-rack gate ({@code gallery.RackJudge}) refuses a rack that
     * ships with one — all three through these same two tests, so a device that
     * declares a new self-starting switch tomorrow changes all three at once.
     * (v2.179.0's judge kept its own list — {@code armed}, {@code running},
     * TAIL's {@code follow} — written in parallel with the authority it
     * duplicated: the hand-kept-set defect the release existed to remove,
     * rebuilt one package over. Found by an outside review the day it shipped.)
     *
     * @param selfStartingByType as for {@link #imported(JSONObject, Path, Function)}
     */
    public static boolean startsByItself(String typeId, String key, String value,
            Function<String, Set<String>> selfStartingByType) {
        return value != null && readsOn(value) && arrivesOff(keysOf(selfStartingByType, typeId), key);
    }

    /** {@link #startsByItself(String, String, String, Function)} by this install's device declarations. */
    public static boolean startsByItself(String typeId, String key, String value) {
        return startsByItself(typeId, key, value, org.nmox.studio.rack.devices.SelfStarting::keysFor);
    }

    /** For a value that {@link #readsOn}: its key was declared self-starting, or the answer was {@link #EVERY_SWITCH}. */
    private static boolean arrivesOff(Set<String> keys, String key) {
        return keys.contains(key) || keys.contains("*");
    }

    // ---- the home rewrite: one linear scan each way, no regex ----

    /**
     * What may stand BEFORE a local path inside a value: the start, whitespace,
     * a quote, or {@code =} ({@code KEY=/Users/x}). Deliberately not {@code :}
     * ({@code host:/Users/x} is a REMOTE path in scp and ssh) and not {@code /}
     * ({@code /srv/Users/sender} is not the sender's home).
     */
    static boolean opensPath(String value, int at) {
        if (at == 0) {
            return true;
        }
        char c = value.charAt(at - 1);
        return Character.isWhitespace(c) || c == '"' || c == '\'' || c == '=';
    }

    /**
     * What may stand AFTER the home itself: the end, a separator, whitespace or
     * a quote — never another name character ({@code /Users/senderling}).
     */
    private static boolean closesHome(String value, int at) {
        if (at >= value.length()) {
            return true;
        }
        char c = value.charAt(at);
        return c == '/' || c == '\\' || Character.isWhitespace(c) || c == '"' || c == '\'';
    }

    /**
     * Where the path token that starts at {@code pathStart} ends, scanning from
     * {@code from}: at the quote that opened it when there was one, else at the
     * next whitespace or quote. Only the token is re-spelled; the rest of a
     * command is never touched (a regex argument's backslashes are not
     * separators). The known cost: an UNQUOTED path with a space in it is
     * re-spelled up to the space only — the home is still hidden and expanded,
     * and only a Windows sender's backslashes after the space travel as typed.
     */
    private static int tokenEnd(String value, int pathStart, int from) {
        char opener = pathStart > 0 ? value.charAt(pathStart - 1) : 0;
        boolean quoted = opener == '"' || opener == '\'';
        int i = from;
        for (; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quoted ? c == opener : (Character.isWhitespace(c) || c == '"' || c == '\'')) {
                break;
            }
        }
        return i;
    }

    /**
     * True when {@code value} holds {@code prefix} at {@code at}, in either
     * separator spelling; a drive-letter home compares without case, as the
     * file system it names does.
     */
    private static boolean homeAt(String value, int at, String prefix, boolean ignoreCase) {
        if (at + prefix.length() > value.length()) {
            return false;
        }
        for (int k = 0; k < prefix.length(); k++) {
            char v = value.charAt(at + k);
            char p = prefix.charAt(k);
            if (v == '\\') {
                v = '/';
            }
            if (v != p && !(ignoreCase && Character.toLowerCase(v) == Character.toLowerCase(p))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every occurrence of {@code prefix} (the sender's home, spelled with
     * {@code /}) at a path boundary becomes {@code ~}, and the rest of that path
     * token is written with {@code /}. One pass: each character is visited once,
     * and a candidate costs at most the prefix length, only at a boundary.
     */
    static String hideHome(String value, String prefix) {
        // a one-character "home" (the file system root) names nobody and would match every path
        if (value == null || prefix == null || prefix.length() < 2 || value.length() < prefix.length()) {
            return value;
        }
        boolean ignoreCase = prefix.charAt(1) == ':'; // a drive-letter home; the guard above left at least two characters
        // Only a Windows-spelled home (a drive letter, or a UNC share) has
        // backslash SEPARATORS to re-spell. Under a slash-spelled home a
        // backslash is a character of the path — "my\ notes" — and turning it
        // into a slash names a different file (the 2026-09-18 fold review).
        boolean windowsHome = ignoreCase || prefix.startsWith("//");
        StringBuilder out = null;
        int copied = 0;
        int i = 0;
        while (i < value.length()) {
            if (opensPath(value, i) && homeAt(value, i, prefix, ignoreCase)
                    && closesHome(value, i + prefix.length())) {
                if (out == null) {
                    out = new StringBuilder(value.length());
                }
                out.append(value, copied, i).append('~');
                int end = tokenEnd(value, i, i + prefix.length());
                for (int k = i + prefix.length(); k < end; k++) {
                    char c = value.charAt(k);
                    out.append(windowsHome && c == '\\' ? '/' : c);
                }
                copied = end;
                i = Math.max(end, i + 1);
            } else {
                i++;
            }
        }
        return out == null ? value : out.append(value, copied, value.length()).toString();
    }

    /**
     * The inverse: a {@code ~} at a path boundary, followed by the end, {@code /},
     * whitespace or a quote, becomes {@code home}, and the rest of that path token
     * is written with the receiver's {@code separator}. {@code host:~/dir} and
     * {@code http://host/~user/} have no boundary before their tilde and stay.
     */
    static String expandHome(String value, String home, char separator) {
        if (value == null || home == null || value.indexOf('~') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length() + home.length());
        int copied = 0;
        int i = 0;
        while (i < value.length()) {
            if (value.charAt(i) == '~' && opensPath(value, i) && closesTilde(value, i + 1)) {
                out.append(value, copied, i).append(home);
                int end = tokenEnd(value, i, i + 1);
                for (int k = i + 1; k < end; k++) {
                    char c = value.charAt(k);
                    out.append(c == '/' ? separator : c);
                }
                copied = end;
                i = end;
            } else {
                i++;
            }
        }
        return out.append(value, copied, value.length()).toString();
    }

    private static boolean closesTilde(String value, int at) {
        if (at >= value.length()) {
            return true;
        }
        char c = value.charAt(at);
        return c == '/' || Character.isWhitespace(c) || c == '"' || c == '\'';
    }

    // ---- the sender's audit: hand scanners, each linear ----

    private static final String[] HOME_ROOTS = {"/Users/", "/home/"};
    /** Directories under a home root that are not a person. */
    private static final Set<String> NOBODY = Set.of("shared", "public", "default");

    /**
     * True when the value still names somebody's home: {@code /Users/<name>},
     * {@code /home/<name>} or {@code X:\Users\<name>} (either separator). The
     * audit is deliberately WIDER than the rewrite: anything may stand before
     * the root except a path-name character, so a remote path
     * ({@code host:/home/david/x}) and a file URL
     * ({@code file:///Users/david/site/}) — which the rewrite rightly leaves
     * alone, since {@code ~} means nothing in either — are still shown to the
     * sender, because they still carry a name. {@code /srv/Users/x} and
     * {@code http://localhost/Users/x} are not homes and are not flagged.
     */
    static boolean namesAHome(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (i > 0 && pathNameChar(value.charAt(i - 1))) {
                continue;
            }
            int nameStart = -1;
            for (String root : HOME_ROOTS) {
                if (value.startsWith(root, i)) {
                    nameStart = i + root.length();
                }
            }
            if (nameStart < 0 && isDriveUsers(value, i)) {
                nameStart = i + DRIVE_USERS_LENGTH;
            }
            if (nameStart < 0) {
                continue;
            }
            int end = nameStart;
            while (end < value.length() && !closesHome(value, end)) {
                end++;
            }
            if (end > nameStart && !NOBODY.contains(value.substring(nameStart, end).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** The length of {@code C:\Users\}. */
    private static final int DRIVE_USERS_LENGTH = 9;

    /** {@code C:\Users\} or {@code c:/users/} at {@code at}: any drive letter, either separator, any case. */
    private static boolean isDriveUsers(String value, int at) {
        if (at + DRIVE_USERS_LENGTH > value.length()
                || !asciiLetter(value.charAt(at)) || value.charAt(at + 1) != ':') {
            return false;
        }
        return isSeparator(value.charAt(at + 2)) && value.regionMatches(true, at + 3, "Users", 0, 5)
                && isSeparator(value.charAt(at + 8));
    }

    private static boolean isSeparator(char c) {
        return c == '/' || c == '\\';
    }

    /** Assignment-shaped credential words, matched without case. */
    private static final String[] SECRET_ASSIGNMENTS = {"password=", "passwd=", "token="};
    /** Words that are a credential when {@code =} or {@code :} follows (quotes and spaces between allowed). */
    private static final String[] SECRET_KEYS = {"api_key", "apikey", "api-key"};
    /** Well-known token prefixes: a word of their own with {@link #MIN_TOKEN_TAIL} token characters after. */
    private static final String[] SECRET_PREFIXES = {"sk-", "ghp_", "gho_", "github_pat_", "xoxb-", "xoxp-"};
    /** {@code sk-learn} is a package; {@code sk-} and eight more token characters is a key. */
    static final int MIN_TOKEN_TAIL = 8;

    /** True when the value looks like it carries a credential; each detector says what it reads. */
    static boolean looksSecret(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return hasBearer(value) || hasSecretAssignment(value) || hasApiKey(value)
                || hasTokenPrefix(value) || hasAwsKeyId(value) || hasPrivateKey(value);
    }

    /** {@code Authorization: Bearer abc…}: the scheme as a word, any case, one space, then something. */
    static boolean hasBearer(String value) {
        String needle = "bearer ";
        for (int i = 0; i + needle.length() < value.length(); i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length()) && !wordChar(value, i - 1)
                    && !Character.isWhitespace(value.charAt(i + needle.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code password=}, {@code passwd=}, {@code token=} anywhere, any case.
     * {@code --token-file ./t} has no {@code =} after the word and is not one.
     */
    static boolean hasSecretAssignment(String value) {
        for (String needle : SECRET_ASSIGNMENTS) {
            if (indexOfIgnoreCase(value, needle, 0) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** {@code api_key=…}, {@code "apikey": …}, {@code api-key: …}. */
    static boolean hasApiKey(String value) {
        for (String needle : SECRET_KEYS) {
            int from = 0;
            int at;
            while ((at = indexOfIgnoreCase(value, needle, from)) >= 0) {
                int k = at + needle.length();
                while (k < value.length()
                        && (value.charAt(k) == ' ' || value.charAt(k) == '"' || value.charAt(k) == '\'')) {
                    k++;
                }
                if (k < value.length() && (value.charAt(k) == '=' || value.charAt(k) == ':')) {
                    return true;
                }
                from = at + 1;
            }
        }
        return false;
    }

    /**
     * {@code sk-…}, {@code ghp_…}, {@code gho_…}, {@code github_pat_…},
     * {@code xoxb-…}, {@code xoxp-…} as a word of their own: {@code task-runner}
     * holds {@code sk-} and is not one.
     */
    static boolean hasTokenPrefix(String value) {
        for (String prefix : SECRET_PREFIXES) {
            int from = 0;
            int at;
            while ((at = value.indexOf(prefix, from)) >= 0) {
                if (!wordChar(value, at - 1) && tokenRun(value, at + prefix.length()) >= MIN_TOKEN_TAIL) {
                    return true;
                }
                from = at + 1;
            }
        }
        return false;
    }

    /** An AWS access key id: {@code AKIA} and exactly sixteen upper-case letters or digits, a word of its own. */
    static boolean hasAwsKeyId(String value) {
        int from = 0;
        int at;
        while ((at = value.indexOf("AKIA", from)) >= 0) {
            int k = at + 4;
            while (k < value.length() && upperOrDigit(value.charAt(k))) {
                k++;
            }
            if (k - (at + 4) == 16 && !wordChar(value, at - 1) && !wordChar(value, k)) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    /** A PEM private key: {@code -----BEGIN } and, after it, {@code PRIVATE KEY}. */
    static boolean hasPrivateKey(String value) {
        int begin = value.indexOf("-----BEGIN ");
        return begin >= 0 && value.indexOf("PRIVATE KEY", begin) >= 0;
    }

    /** The first four code points and an ellipsis: enough to find the field, never enough to use. */
    static String masked(String value) {
        int shown = Math.min(4, value.codePointCount(0, value.length()));
        return value.substring(0, value.offsetByCodePoints(0, shown)) + "\u2026";
    }

    private static boolean upperOrDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean asciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** A character a directory name ends with: after one, {@code /Users/} is a directory somewhere else, not a home root. */
    private static boolean pathNameChar(char c) {
        return asciiLetter(c) || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_' || c == '~';
    }

    /**
     * An ASCII letter or digit, {@code -} or {@code _} at {@code at}; false off
     * either end. ASCII on purpose: this is the alphabet credentials are issued
     * in (a token format decides it, not the reader's script), so no Unicode
     * letter class is consulted.
     */
    private static boolean wordChar(String value, int at) {
        if (at < 0 || at >= value.length()) {
            return false;
        }
        char c = value.charAt(at);
        return asciiLetter(c) || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    private static int tokenRun(String value, int from) {
        int k = from;
        while (wordChar(value, k)) {
            k++;
        }
        return k - from;
    }

    private static int indexOfIgnoreCase(String value, String needle, int from) {
        for (int i = Math.max(0, from); i + needle.length() <= value.length(); i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The device object at {@code i}, or a refusal that names the slot: a
     * file from another machine can hold anything in its {@code devices}
     * array, and org.json's own message ("JSONArray[0] is not a JSONObject")
     * used to escape {@code inspect} uncaught on the EDT — a red exception
     * dialog where a refusal belongs (the 2026-09-17 arc review, hostile
     * input lens).
     */
    private static JSONObject deviceAt(JSONArray devices, int i) {
        JSONObject dj = devices.optJSONObject(i);
        if (dj == null) {
            throw new IllegalArgumentException("devices[" + i + "] is not a device object");
        }
        return dj;
    }

    private static String slashes(String path) {
        return path.replace('\\', '/');
    }

    private static void rewriteStates(JSONObject patch, java.util.function.UnaryOperator<String> rewrite) {
        JSONArray devices = patch.optJSONArray("devices");
        if (devices == null) {
            return;
        }
        for (int i = 0; i < devices.length(); i++) {
            JSONObject state = deviceAt(devices, i).optJSONObject("state");
            if (state == null) {
                continue;
            }
            for (String key : new ArrayList<>(state.keySet())) {
                state.put(key, rewrite.apply(state.optString(key, "")));
            }
        }
    }
}
