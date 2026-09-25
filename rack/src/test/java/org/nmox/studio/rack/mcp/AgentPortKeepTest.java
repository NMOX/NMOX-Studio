package org.nmox.studio.rack.mcp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Agent Port connects once (3.2): the Claude Code line, and the opt-in
 * keep/forget/autostart lifecycle — through a map standing in for the
 * keychain and in-memory preferences, so no test touches the real
 * keychain or the real userdir.
 */
class AgentPortKeepTest {

    private final List<AgentPort> started = new java.util.ArrayList<>();

    @AfterEach
    void stopAll() {
        for (AgentPort p : started) {
            p.stop();
        }
    }

    private AgentPort track(AgentPort p) {
        started.add(p);
        return p;
    }

    // ---- the Claude Code line ------------------------------------------

    @Test
    @DisplayName("Copy for Claude Code builds exactly the documented command")
    void claudeCommandIsExact() {
        String token = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        assertThat(AgentPortAction.claudeCodeCommand("http://127.0.0.1:57511/mcp", token))
                .isEqualTo("claude mcp add --transport http nmox-studio http://127.0.0.1:57511/mcp"
                        + " --header \"Authorization: Bearer " + token + "\"");
        // the forge's placeholder passes the same alphabet
        assertThat(AgentPortAction.claudeCodeCommand("http://127.0.0.1:1/mcp", "TOKEN"))
                .endsWith("\"Authorization: Bearer TOKEN\"");
        assertThat(AgentPortAction.claudeCodeCommand("http://127.0.0.1:1/mcp", "url-safe_Base64-token"))
                .isNotNull();
    }

    @Test
    @DisplayName("every real token is in the shell-inert alphabet, so the line always builds")
    void realTokensAreShellInert() throws Exception {
        for (int i = 0; i < 20; i++) {
            String t = AgentPort.newToken();
            assertThat(t).matches("[A-Za-z0-9_-]{64}");
            assertThat(AgentPort.wellFormedToken(t)).isTrue();
            assertThat(AgentPortAction.claudeCodeCommand("http://127.0.0.1:1/mcp", t)).isNotNull();
        }
    }

    @Test
    @DisplayName("a token a shell would change is refused, never emitted")
    void badTokenRefused() {
        for (String bad : new String[]{"abc\"; rm -rf ~; \"", "a b", "$HOME", "`id`", "a\\b", "tok'en", "", null,
            "abc\n", "ünïcode"}) {
            assertThat(AgentPortAction.claudeCodeCommand("http://127.0.0.1:1/mcp", bad))
                    .as("refused: %s", bad).isNull();
        }
        assertThat(AgentPortAction.claudeCodeCommand("http://evil.example/mcp", "TOKEN"))
                .as("only this port's loopback URL").isNull();
        assertThat(AgentPortAction.claudeCodeCommand("http://127.0.0.1:1/mcp\" ; x", "TOKEN")).isNull();
    }

    @Test
    @DisplayName("the kept-address start refuses a malformed token rather than serve it")
    void startRefusesBadToken() {
        assertThatThrownBy(() -> AgentPort.start(new McpTools(List.of()), "t", 0, "short"))
                .hasMessageContaining("not well-formed");
        assertThatThrownBy(() -> AgentPort.start(new McpTools(List.of()), "t", 0,
                "x".repeat(40) + "\"")).hasMessageContaining("not well-formed");
    }

    // ---- keep / forget / reuse -----------------------------------------

    @Test
    @DisplayName("Keep stores the token in the keychain and the port in preferences; the next start reuses both")
    void keepThenReuse() throws Exception {
        MapSecrets secrets = new MapSecrets();
        Preferences prefs = new MemoryPreferences();
        AgentPortKeep keep = new AgentPortKeep(prefs, secrets);

        AgentPortKeep.Started first = keep.start(new McpTools(List.of()), "t");
        track(first.port());
        assertThat(secrets.map).as("nothing is kept until asked").isEmpty();
        assertThat(keep.keeping()).isFalse();

        assertThat(keep.keep(first.port())).isTrue();
        assertThat(new String(secrets.map.get(AgentPortKeep.TOKEN_KEY))).isEqualTo(first.port().token());
        assertThat(prefs.getInt(AgentPortKeep.PORT, 0)).isEqualTo(first.port().port());
        assertThat(prefs.keys()).as("the token is never a preference")
                .containsExactlyInAnyOrder(AgentPortKeep.PORT, AgentPortKeep.KEEP, AgentPortKeep.STARTED);
        for (String k : prefs.keys()) {
            assertThat(prefs.get(k, "")).doesNotContain(first.port().token());
        }
        int kept = first.port().port();
        String token = first.port().token();
        first.port().stop();
        started.clear();

        AgentPortKeep.Started second = keep.start(new McpTools(List.of()), "t");
        track(second.port());
        assertThat(second.port().port()).isEqualTo(kept);
        assertThat(second.port().token()).isEqualTo(token);
        assertThat(second.moved()).isFalse();
        assertThat(second.newToken()).isFalse();
    }

    @Test
    @DisplayName("Forget deletes the keychain entry and the preferences, and turns autostart off")
    void forgetDeletes() throws Exception {
        MapSecrets secrets = new MapSecrets();
        Preferences prefs = new MemoryPreferences();
        AgentPortKeep keep = new AgentPortKeep(prefs, secrets);
        AgentPort p = track(AgentPort.start(new McpTools(List.of()), "t"));
        keep.keep(p);
        assertThat(keep.setAutostart(true)).isTrue();
        assertThat(keep.autostart()).isTrue();

        keep.forget();
        assertThat(secrets.map).as("the keychain entry is gone").doesNotContainKey(AgentPortKeep.TOKEN_KEY);
        assertThat(secrets.deleted).contains(AgentPortKeep.TOKEN_KEY);
        assertThat(prefs.keys()).as("no port, no switches left behind").isEmpty();
        assertThat(keep.keeping()).isFalse();
        assertThat(keep.autostart()).isFalse();

        // and the next start is the default one again: a fresh token
        AgentPortKeep.Started next = keep.start(new McpTools(List.of()), "t");
        track(next.port());
        assertThat(next.port().token()).isNotEqualTo(p.token());
        assertThat(secrets.map).isEmpty();
    }

    @Test
    @DisplayName("a kept port someone else holds: a new port, kept, and the start says it moved")
    void takenPortMoves() throws Exception {
        MapSecrets secrets = new MapSecrets();
        Preferences prefs = new MemoryPreferences();
        AgentPortKeep keep = new AgentPortKeep(prefs, secrets);
        try (ServerSocket squatter = new ServerSocket()) {
            squatter.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int taken = squatter.getLocalPort();
            String token = AgentPort.newToken();
            secrets.map.put(AgentPortKeep.TOKEN_KEY, token.toCharArray());
            prefs.putInt(AgentPortKeep.PORT, taken);
            prefs.putBoolean(AgentPortKeep.KEEP, true);

            AgentPortKeep.Started s = keep.start(new McpTools(List.of()), "t");
            track(s.port());
            assertThat(s.moved()).as("the caller is told the address changed").isTrue();
            assertThat(s.newToken()).isFalse();
            assertThat(s.port().port()).isNotEqualTo(taken);
            assertThat(s.port().token()).as("the token survives the move").isEqualTo(token);
            assertThat(prefs.getInt(AgentPortKeep.PORT, 0)).as("the new address is the kept one now")
                    .isEqualTo(s.port().port());
        }
    }

    @Test
    @DisplayName("a kept token that is missing or malformed is replaced, re-kept, and said")
    void lostTokenIsReplaced() throws Exception {
        MapSecrets secrets = new MapSecrets();
        Preferences prefs = new MemoryPreferences();
        prefs.putBoolean(AgentPortKeep.KEEP, true);
        secrets.map.put(AgentPortKeep.TOKEN_KEY, "not a token".toCharArray());
        AgentPortKeep keep = new AgentPortKeep(prefs, secrets);
        AgentPortKeep.Started s = keep.start(new McpTools(List.of()), "t");
        track(s.port());
        assertThat(s.newToken()).isTrue();
        assertThat(new String(secrets.map.get(AgentPortKeep.TOKEN_KEY))).isEqualTo(s.port().token());
    }

    @Test
    @DisplayName("a keychain fallen back to memory reports the keep as session-only")
    void nonDurableKeepSaysSo() throws Exception {
        MapSecrets secrets = new MapSecrets();
        secrets.durable = false;
        AgentPortKeep keep = new AgentPortKeep(new MemoryPreferences(), secrets);
        AgentPort p = track(AgentPort.start(new McpTools(List.of()), "t"));
        assertThat(keep.keep(p)).isFalse();
    }

    @Test
    @DisplayName("autostart cannot be switched on while nothing is kept")
    void autostartNeedsKeep() {
        Preferences prefs = new MemoryPreferences();
        AgentPortKeep keep = new AgentPortKeep(prefs, new MapSecrets());
        assertThat(keep.setAutostart(true)).isFalse();
        assertThat(keep.autostart()).isFalse();
        prefs.putBoolean(AgentPortKeep.AUTOSTART, true);
        assertThat(keep.autostart()).as("a stray autostart pref alone does nothing").isFalse();
    }

    @Test
    @DisplayName("the menu's start says on the status line that the kept address moved, naming the new port")
    void actionSaysTheAddressMoved() throws Exception {
        AgentPortAction.stopForTest();
        MapSecrets secrets = new MapSecrets();
        Preferences prefs = new MemoryPreferences();
        AgentPortKeep keep = new AgentPortKeep(prefs, secrets);
        try (ServerSocket squatter = new ServerSocket()) {
            squatter.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            secrets.map.put(AgentPortKeep.TOKEN_KEY, AgentPort.newToken().toCharArray());
            prefs.putInt(AgentPortKeep.PORT, squatter.getLocalPort());
            prefs.putBoolean(AgentPortKeep.KEEP, true);
            AgentPortAction.start(keep, false);
            AgentPortAction.awaitLaneIdle();
            java.awt.EventQueue.invokeAndWait(() -> { });
            AgentPort running = AgentPortAction.running();
            assertThat(running).as("the port started on a new address").isNotNull();
            assertThat(org.openide.awt.StatusDisplayer.getDefault().getStatusText())
                    .contains("127.0.0.1:" + running.port())
                    .contains("give your agent the new address");
            assertThat(prefs.getBoolean(AgentPortKeep.STARTED, false)).as("the FIRST STEPS record").isTrue();
        } finally {
            AgentPortAction.stopForTest();
        }
    }

    // ---- the boot hook ---------------------------------------------------

    @Test
    @DisplayName("the start-with-the-IDE hook starts nothing unless BOTH preferences are on")
    void autostartHookNeedsBothPrefs() {
        Preferences prefs = new MemoryPreferences();
        AgentPortKeep keep = new AgentPortKeep(prefs, new MapSecrets());
        AtomicInteger starts = new AtomicInteger();
        AgentPortAutostart hook = new AgentPortAutostart(() -> keep, k -> starts.incrementAndGet());

        hook.run();
        assertThat(starts).as("neither").hasValue(0);
        prefs.putBoolean(AgentPortKeep.AUTOSTART, true);
        hook.run();
        assertThat(starts).as("autostart without keep").hasValue(0);
        prefs.remove(AgentPortKeep.AUTOSTART);
        prefs.putBoolean(AgentPortKeep.KEEP, true);
        hook.run();
        assertThat(starts).as("keep without autostart").hasValue(0);
        prefs.putBoolean(AgentPortKeep.AUTOSTART, true);
        hook.run();
        assertThat(starts).as("both").hasValue(1);
        System.setProperty("nmox.shots.dir", "/tmp/shots");
        try {
            hook.run();
            assertThat(starts).as("never under the docs forge").hasValue(1);
        } finally {
            System.clearProperty("nmox.shots.dir");
        }
    }

    @Test
    @DisplayName("the note under the boxes says what each ticked box does, and nothing when neither is")
    void keepNoteIsHonest() {
        assertThat(AgentPortAction.keepNote(false, false)).isEmpty();
        assertThat(AgentPortAction.keepNote(false, true)).isEmpty();
        assertThat(AgentPortAction.keepNote(true, false)).contains("system keychain").doesNotContain("starts with");
        assertThat(AgentPortAction.keepNote(true, true)).contains("system keychain").contains("starts with NMOX Studio");
    }

    // ---- stand-ins ---------------------------------------------------------

    /** The keychain as a map; records deletes so forget can be proven. */
    static final class MapSecrets implements AgentPortKeep.Secrets {

        final Map<String, char[]> map = new HashMap<>();
        final List<String> deleted = new java.util.ArrayList<>();
        boolean durable = true;

        @Override
        public char[] read(String key) {
            char[] v = map.get(key);
            return v == null ? null : v.clone();
        }

        @Override
        public void save(String key, char[] value, String description) {
            map.put(key, value.clone());
        }

        @Override
        public void delete(String key) {
            deleted.add(key);
            map.remove(key);
        }

        @Override
        public boolean durable() {
            return durable;
        }
    }

    /** A parentless in-memory preferences node — never the real userRoot. */
    static final class MemoryPreferences extends AbstractPreferences {

        private final Map<String, String> values = new HashMap<>();

        MemoryPreferences() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            values.clear();
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            throw new UnsupportedOperationException(name);
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
