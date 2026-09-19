package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plural law (v2.85.0, widened here): a user-visible count never
 * reads "1 cards".
 *
 * <p>The first cut held two things it could not hold. Its population was
 * a hand-kept list of five nouns ({@code cards|rows|matches|checks|pieces}),
 * and it read only Java string CONCATENATION ({@code + " cards"}) — so it
 * could not look inside a message VALUE, which is where counts actually
 * live. {@code {0} devices, {1} cables} sat in a bundle reading "1 devices,
 * 1 cables" with the gate green (ledger 105).
 *
 * <p>So the population is DERIVED now, from every English message value
 * the product ships: the {@code @Messages} entries of all ten modules and
 * their hand-written {@code Bundle.properties}. Every {@code {n}} followed
 * by a word that could be a plural noun must be one of:
 *
 * <ul>
 *   <li>BRANCHED — the occurrence sits inside a {@code {n,choice,…}} body,
 *       so the sentence says "1 cable" and "2 cables" (the shape the l10n
 *       arc already uses, which pl/ru/uk can carry their three and four
 *       forms in);</li>
 *   <li>excluded by ENGLISH — the word is a function word that is never a
 *       plural noun ({@link #NEVER_PLURAL}), a fact about the language and
 *       not about our copy;</li>
 *   <li>excluded by the house's KEY-PAIR idiom — the key is the {@code
 *       …Many} half of a {@code …One}/{@code …Many} pair, where the caller
 *       picks the key by the count (core.util.Plural's shape);</li>
 *   <li>written down in {@link #LEDGER} with a reason a person can
 *       disagree with — it is a verb, it is not a count at all, it is a
 *       cap that can never be one, or the call site already branches.</li>
 * </ul>
 *
 * <p>A new counted sentence fails this test until a human decides, which
 * is the {@code SpawnSiteTrustLedgerTest} shape: enumeration beats
 * recollection. The old concatenation law is kept below — that shape is
 * still wrong wherever it returns.
 */
class PluralCopyGateTest {

    // ---- the old law: a bare plural concatenated in code -------------------

    // a NAME followed by the noun ("To Do cards", a list's accessible
    // name) is not a count — only a count's operand before the plural is
    private static final Pattern BARE = Pattern.compile(
            "(?<!name\\(\\) |label\\(\\) |title\\(\\) )\\+ \" (cards|rows|matches|checks|pieces)(\"| )");

    private static final String[] MODULES = {"core", "editor", "tools", "rack", "project",
        "ui", "apiclient", "dbstudio", "web3", "infra"};

    @Test
    @DisplayName("no user-visible count concatenates a bare plural for the nouns the sweep fixed")
    void noBarePlurals() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(p);
                    if (BARE.matcher(text).find()) {
                        offenders.add(module + "/" + src.relativize(p));
                    }
                }
            }
        }
        assertThat(offenders).as("count strings without a singular branch").isEmpty();
    }

    // ---- the message-value law: the population, derived --------------------

    /**
     * Words that end in {@code s} and are never a plural noun being
     * counted. A closed grammatical class — this shrinks the population by
     * English, never by opinion about our own copy.
     */
    private static final Set<String> NEVER_PLURAL = Set.of(
            "is", "was", "has", "as", "its", "this", "thus", "plus", "less",
            "does", "goes", "says", "yes", "his", "hers", "theirs", "always");

    /**
     * key → why its counted-looking placeholder needs no branch. VERB = the
     * word is what the subject does; NOT A COUNT = the argument is a name;
     * CONSTANT / OVER A CAP = the number can never be one; GUARDED = the
     * call site picks a different key at one.
     */
    private static final Map<String, String> LEDGER = ledger();

    private static Map<String, String> ledger() {
        Map<String, String> m = new LinkedHashMap<>();

        // VERB — the placeholder names a thing, and the word after it is
        // what that thing does. Nothing is being counted.
        m.put("CheckTranslationsAction_findingMismatch",
                "VERB: {1} is the catalog KEY — that key formats with different arguments");
        m.put("LanguageServerHealth_needsProject",
                "VERB: {0} is the server's package name — it installs into the project");
        m.put("LanguageServersPanel_needsProject",
                "VERB: {0} is the server's package name — it installs into the project");
        m.put("RenameClassAction_nowhere",
                "VERB: {0} is the class name — that class appears nowhere");
        m.put("DockerPanelTopComponent_noHostPorts",
                "VERB: {0} is the container's name — that container publishes no host ports");
        m.put("IrcTopComponent_filterAdded",
                "VERB: {0} is the filter's name — `/filter del <name>` removes it");
        m.put("CheckMyWorkAction_allPass",
                "VERB: {0} is ALREADY a pluralized phrase from core.util.Plural.of "
                + "(\"3 checks\" / \"1 check\") — \"pass\" is what the checks do");
        m.put("CheckMyWorkAction_somePass",
                "VERB: {1} is ALREADY a pluralized phrase from core.util.Plural.of "
                + "(\"3 checks\" / \"1 check\") — \"pass\" is what the checks do");

        // NOT A COUNT — the argument is a name that happens to sit before
        // a plural word.
        m.put("ShareDialog_fits",
                "NOT A COUNT: {0} is a ProjectKind NAME — the bundle's own comment says "
                + "\"e.g. RUST\", so the row reads \"Suggest it for RUST projects\"");
        m.put("TasksTopComponent_columnCardsA11y",
                "NOT A COUNT: {0} is the COLUMN's name — the list's accessible name reads "
                + "\"To Do cards\" (the shape the concatenation law above also excludes)");

        // CONSTANT / OVER A CAP — the number is a fixed ceiling or is past
        // one by construction, so it can never read "1".
        m.put("NmoxSymbolProvider_truncated",
                "CONSTANT: {0} is ProjectSymbols.MAX_FILES (2,000)");
        m.put("TestsExplorerTopComponent_truncated",
                "CONSTANT: {0} is TestIndex.MAX_FILES (2,000)");
        m.put("EditWithKvasirAction_tooLarge",
                "OVER A CAP: the refusal is said only when {0} EXCEEDS KvasirEdit.MAX_CODE_CHARS, "
                + "and {1} is that cap");
        m.put("GitStatusLine_diffConsentDetail",
                "CONSTANT: {1} is KvasirCommitMessage.MAX_DIFF_CHARS");
        m.put("GitStatusLine_showingFirstComments",
                "CONSTANT: {0} is GitReviews.LIMIT, and the line is said only when the list "
                + "was truncated to it");
        m.put("BrowserErrorDisclosure_whatWithSource",
                "CONSTANT: {0} is CONTEXT * 2 + 1 = 7 lines of context");
        m.put("EnvironmentDoctorAction_present",
                "CONSTANT: {1} is the fixed probe table's size (~60 rows)");
        m.put("ManageLearningSpacesAction_browse",
                "CONSTANT: {0} is the learning catalogue's size (93 spaces)");
        m.put("OverviewPanel_flowA11y",
                "CONSTANT: {1} is bins.length = OverviewPanel.FLOW_DAYS (14); {0} is already "
                + "pluralized by core.util.Plural.of");
        m.put("OverviewPanel_flowSection",
                "CONSTANT: {0} is OverviewPanel.FLOW_DAYS (14)");
        m.put("TextFilters_regexTooLong",
                "CONSTANT: {0} is TextFilters.MAX_REGEX");
        m.put("ContractSizeCheck_verdict",
                "CONSTANT: {2} is the EIP byte limit, already thousands-formatted for reading");

        // GUARDED — the call site picks a different key when the count is
        // one, which is the house's own plural idiom under other names.
        m.put("GhostText_armedAll",
                "GUARDED: said only when more > 0, so {0} = more + 1 is at least two; "
                + "the one-line case reads GhostText_armed");
        m.put("RackStatusLine_agentsStreaming",
                "GUARDED: the tooltip picks RackStatusLine_oneAgentStreaming at n == 1 "
                + "(and RackStatusLine_noAgentStreaming at 0)");
        m.put("ManageExperimentsAction_ageDays",
                "GUARDED: age() picks ManageExperimentsAction_ageDay at days == 1");
        m.put("ChannelListDialog_showingFirst",
                "GUARDED: said only when totalSeen > rows.size(), so {1} is at least two");
        return m;
    }

    /** The sibling keys the GUARDED rows above promise exist. */
    private static final Set<String> GUARD_SIBLINGS = Set.of(
            "GhostText_armed", "RackStatusLine_oneAgentStreaming",
            "RackStatusLine_noAgentStreaming", "ManageExperimentsAction_ageDay");

    /** One English message value, and where it was declared. */
    private record Message(String module, String file, String key, String value) {
    }

    /** {@code {n}} followed by a word that could be a plural noun. */
    private static final Pattern COUNTED = Pattern.compile("\\{(\\d+)\\}\\s+([A-Za-z]+s)\\b");

    @Test
    @DisplayName("every counted sentence branches its plural, or the ledger says why it need not")
    void everyCountedPluralIsBranchedOrLedgered() throws IOException {
        List<Message> messages = allMessages();
        assertThat(messages.size())
                .as("the derivation found the product's message values — a population this "
                        + "small means the scan broke, not that the product went quiet")
                .isGreaterThan(2_000);

        Set<String> keysWithSiblingOne = new TreeSet<>();
        for (Message m : messages) {
            keysWithSiblingOne.add(m.key());
        }

        Map<String, String> unclassified = new LinkedHashMap<>();
        Set<String> flagged = new TreeSet<>();
        for (Message m : messages) {
            for (int[] hit : countedPlurals(m.value())) {
                String word = m.value().substring(hit[1], hit[2]);
                if (NEVER_PLURAL.contains(word)) {
                    continue;
                }
                if (m.key().endsWith("Many")
                        && keysWithSiblingOne.contains(m.key().substring(0, m.key().length() - 4) + "One")) {
                    continue;
                }
                flagged.add(m.key());
                if (!LEDGER.containsKey(m.key())) {
                    unclassified.put(m.key() + " [" + m.module() + "/" + m.file() + "] "
                            + "{" + hit[3] + "} " + word, m.value());
                }
            }
        }

        assertThat(unclassified.keySet())
                .as("a counted sentence that reads \"1 cards\" — branch it in place with "
                        + "{n,choice,0#{n} cards|1#{n} card|1<{n} cards}, or write down in "
                        + "LEDGER why the number can never be one. Values: " + unclassified.values())
                .isEmpty();

        // the ledger cannot rot: every row must still name a real, still
        // unbranched sentence (the v2.136.0 second-home lesson)
        Set<String> stale = new TreeSet<>(LEDGER.keySet());
        stale.removeAll(flagged);
        assertThat(stale)
                .as("a LEDGER row that no longer names an unbranched counted sentence — the "
                        + "value was branched or deleted, so delete the row with it")
                .isEmpty();
    }

    /**
     * A branch is a {@link java.text.ChoiceFormat}, and ChoiceFormat
     * refuses anything that is not a {@link Number} — a call site handing
     * it {@code String.valueOf(n)} throws where the sentence should be
     * painted. So for every {@code {n,choice,…}} the n-th argument of the
     * call must be the number itself. (The value it DISPLAYS may well stay
     * a String on a sibling index — {@code Bundle.Key(String.valueOf(n), n)}
     * is the house's own shape where the count is pre-rendered.)
     *
     * <p>Best effort by design: it reads the {@code Bundle.Key(…)} accessor
     * the {@code @Messages} processor generates, which is how this product
     * calls its messages; a key reached by {@code NbBundle.getMessage} alone
     * is not seen here.
     */
    @Test
    @DisplayName("a branched count arrives at the call site as a number, never a String")
    void aBranchedCountArrivesAsANumber() throws IOException {
        Map<String, List<Integer>> branched = new LinkedHashMap<>();
        for (Message m : allMessages()) {
            List<Integer> indexes = new ArrayList<>();
            Matcher c = Pattern.compile("\\{(\\d+),choice,").matcher(m.value());
            while (c.find()) {
                indexes.add(Integer.parseInt(c.group(1)));
            }
            if (!indexes.isEmpty()) {
                branched.put(m.key(), indexes);
            }
        }
        assertThat(branched).as("the product ships branched counts to check").isNotEmpty();

        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : branched.entrySet()) {
            Pattern call = Pattern.compile("\\bBundle\\." + Pattern.quote(e.getKey()) + "\\s*\\(");
            for (Path p : javaSources()) {
                String text = Files.readString(p);
                Matcher m = call.matcher(text);
                while (m.find()) {
                    int close = closingParen(text, m.end() - 1);
                    if (close < 0) {
                        continue;
                    }
                    List<String> args = topLevelArguments(text.substring(m.end(), close));
                    for (int idx : e.getValue()) {
                        if (idx < args.size() && args.get(idx).contains("String.valueOf(")) {
                            wrong.add(e.getKey() + " arg " + idx + " in "
                                    + p.getFileName() + ": " + args.get(idx).strip());
                        }
                    }
                }
            }
        }
        assertThat(wrong)
                .as("a ChoiceFormat branch cannot format a String — pass the count itself")
                .isEmpty();
    }

    /** The call's arguments, split on the commas that are not nested. */
    private static List<String> topLevelArguments(String args) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(args, i);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(args.substring(start, i));
                start = i + 1;
            }
        }
        out.add(args.substring(start));
        return out;
    }

    private static List<Path> javaSources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                out.addAll(files.filter(p -> p.toString().endsWith(".java")).toList());
            }
        }
        return out;
    }

    @Test
    @DisplayName("the GUARDED ledger rows name keys that really exist")
    void guardedRowsNameRealSiblings() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Message m : allMessages()) {
            keys.add(m.key());
        }
        assertThat(keys)
                .as("a GUARDED row promises the call site picks a singular key at one — "
                        + "if that key was renamed, the promise is no longer checkable")
                .containsAll(GUARD_SIBLINGS);
    }

    // ---- the derivation ----------------------------------------------------

    private static List<Message> allMessages() throws IOException {
        List<Message> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".java")) {
                        String text = Files.readString(p);
                        if (!text.contains("Messages(")) {
                            continue;
                        }
                        for (String literal : messageLiterals(text)) {
                            addEntry(out, module, name, literal);
                        }
                    } else if (name.equals("Bundle.properties")) {
                        // the hand-written half (the @Messages processor
                        // merges into these at compile time)
                        for (String line : Files.readAllLines(p)) {
                            String trimmed = line.strip();
                            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                                continue;
                            }
                            addEntry(out, module, name, unescape(line));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void addEntry(List<Message> out, String module, String file, String raw) {
        int eq = raw.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String key = raw.substring(0, eq).strip();
        if (!key.matches("[A-Za-z][A-Za-z0-9_.]*")) {
            return;
        }
        out.add(new Message(module, file, key, raw.substring(eq + 1)));
    }

    /**
     * Every string literal declared inside an {@code @Messages(…)} /
     * {@code @NbBundle.Messages(…)} annotation, with {@code "a" + "b"}
     * concatenations joined and Java escapes decoded — a value split
     * across source lines is one value on screen.
     */
    private static List<String> messageLiterals(String text) {
        List<String> out = new ArrayList<>();
        Matcher ann = Pattern.compile("@(?:[A-Za-z0-9_.]+\\.)?(?:NbBundle\\.)?Messages\\s*\\(")
                .matcher(text);
        while (ann.find()) {
            int end = closingParen(text, ann.end() - 1);
            if (end < 0) {
                continue;
            }
            for (String joined : joinedLiterals(text.substring(ann.end(), end))) {
                out.add(joined);
            }
        }
        return out;
    }

    /** Index just past the {@code )} matching the one at {@code open}. */
    private static int closingParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Index of the closing quote of the literal starting at {@code i}. */
    private static int skipLiteral(String text, int i) {
        char quote = text.charAt(i);
        for (int j = i + 1; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == '\\') {
                j++;
            } else if (c == quote) {
                return j;
            }
        }
        return text.length() - 1;
    }

    private static List<String> joinedLiterals(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder current = null;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                int close = skipLiteral(body, i);
                String piece = unescape(body.substring(i + 1, close));
                if (current == null) {
                    current = new StringBuilder(piece);
                } else {
                    current.append(piece);
                }
                i = close + 1;
                // a following `+` means the next literal continues this value
                int j = i;
                while (j < body.length() && Character.isWhitespace(body.charAt(j))) {
                    j++;
                }
                if (j < body.length() && body.charAt(j) == '+') {
                    j++;
                    while (j < body.length() && Character.isWhitespace(body.charAt(j))) {
                        j++;
                    }
                    if (j < body.length() && body.charAt(j) == '"') {
                        i = j;
                        continue;
                    }
                }
                out.add(current.toString());
                current = null;
            } else {
                i++;
            }
        }
        if (current != null) {
            out.add(current.toString());
        }
        return out;
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                b.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try {
                            b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException notAnEscape) {
                            b.append(n);
                        }
                    } else {
                        b.append(n);
                    }
                }
                default -> b.append(n);
            }
        }
        return b.toString();
    }

    /**
     * Every {@code {n} <word>s} in the value that is NOT inside a {@code
     * {m,choice,…}} body, as {start, wordStart, wordEnd, index}. A branch
     * may sit on a sibling index ({@code {1,choice,1#{0} key|1<{0} keys}}),
     * which is why the test is containment and not a spelling.
     */
    private static List<int[]> countedPlurals(String value) {
        List<int[]> hits = new ArrayList<>();
        List<int[]> choices = choiceBodies(value);
        Matcher m = COUNTED.matcher(value);
        while (m.find()) {
            boolean inside = false;
            for (int[] span : choices) {
                if (span[0] <= m.start() && m.start() < span[1]) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                hits.add(new int[]{m.start(), m.start(2), m.end(2), Integer.parseInt(m.group(1))});
            }
        }
        return hits;
    }

    private static List<int[]> choiceBodies(String value) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\\d+,choice,").matcher(value);
        while (m.find()) {
            int depth = 1;
            int i = m.end();
            while (i < value.length() && depth > 0) {
                char c = value.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                i++;
            }
            spans.add(new int[]{m.start(), i});
        }
        return spans;
    }
}
