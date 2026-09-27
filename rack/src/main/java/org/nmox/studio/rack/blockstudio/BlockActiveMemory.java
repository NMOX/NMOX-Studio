package org.nmox.studio.rack.blockstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Which Block Studio component each person had open, per project (3.4).
 *
 * <p>Until 3.4 the open component was an {@code active} index inside the
 * committed {@code .nmoxblocks.json}, rewritten on every switch: two people
 * working in two components of one workspace produced a merge conflict on a
 * line that recorded nothing about the work. It is personal state, so it
 * lives with the person — the userdir's preferences — keyed by the project's
 * absolute path.
 *
 * <p>The value is the component's TAG, not its index: a teammate adding or
 * removing a component shifts every index after it, and an index that
 * survives a pull would open the wrong component; a tag still names the same
 * one, and a tag that is gone falls back to whatever the file says.
 *
 * <p>The key is a digest of the path because a preference key is capped at
 * {@link Preferences#MAX_KEY_LENGTH} characters and a project path is not.
 */
final class BlockActiveMemory {

    /** Where the memory lives; a map in tests, the userdir's preferences in the product. */
    interface Store {
        String get(String key);

        void put(String key, String value);
    }

    private static volatile Store store = new PrefsStore();

    private BlockActiveMemory() {
    }

    /** Test seam: an in-memory store, so tests never write the real preferences. */
    static Store useMemoryStoreForTests() {
        Map<String, String> map = new ConcurrentHashMap<>();
        Store memory = new Store() {
            @Override
            public String get(String key) {
                return map.get(key);
            }

            @Override
            public void put(String key, String value) {
                map.put(key, value);
            }
        };
        store = memory;
        return memory;
    }

    /** The tag this person last had open in {@code projectDir}, or null. */
    static String recall(File projectDir) {
        if (projectDir == null) {
            return null;
        }
        return store.get(key(projectDir));
    }

    /** Remembers {@code tag} as the open component in {@code projectDir}. */
    static void remember(File projectDir, String tag) {
        if (projectDir == null || tag == null || tag.isBlank()) {
            return;
        }
        String key = key(projectDir);
        if (!tag.equals(store.get(key))) {
            store.put(key, tag);
        }
    }

    /**
     * Opens the remembered component when the workspace still has it; else
     * leaves the workspace where the file put it (a legacy {@code active},
     * or the first component) and remembers THAT, which is the one-time
     * migration of an old file's {@code active}.
     */
    static void apply(File projectDir, BlockWorkspace ws) {
        String tag = recall(projectDir);
        int at = tag == null ? -1 : ws.indexOfTag(tag);
        if (at >= 0) {
            ws.setActive(at);
        } else {
            remember(projectDir, ws.activeDoc().root().param("tag"));
        }
    }

    static String key(File projectDir) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(projectDir.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
            return "active." + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            // every JDK ships SHA-256; a truncated path is the honest fallback
            String p = projectDir.getAbsolutePath();
            return "active." + p.substring(Math.max(0, p.length() - 60));
        }
    }

    private static final class PrefsStore implements Store {
        private static Preferences prefs() {
            return NbPreferences.forModule(BlockActiveMemory.class);
        }

        @Override
        public String get(String key) {
            return prefs().get(key, null);
        }

        @Override
        public void put(String key, String value) {
            prefs().put(key, value);
        }
    }
}
