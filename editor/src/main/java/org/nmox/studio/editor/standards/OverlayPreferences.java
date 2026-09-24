package org.nmox.studio.editor.standards;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * A few values laid over someone else's preferences: a read of an
 * overridden key answers the override, every other read answers the
 * base, and every write goes to the base untouched - the overlay never
 * stores anything, so the Options dialog and a project's own formatting
 * settings keep working exactly as they did underneath it.
 *
 * <p>This is how {@code .editorconfig} indentation reaches the editor
 * without touching the per-language settings every other file of that
 * language shares. The platform wraps whatever a
 * {@code CodeStylePreferences.Provider} returns in its own caching
 * preferences whenever the result is root-like (no parent), so the
 * overlay is a root node named {@code ""} - the only name the
 * {@code AbstractPreferences} contract allows a parentless node.
 */
final class OverlayPreferences extends AbstractPreferences {

    private final Preferences base;
    private final Map<String, String> overrides;

    OverlayPreferences(Preferences base, Map<String, String> overrides) {
        this(null, "", base, overrides);
    }

    private OverlayPreferences(AbstractPreferences parent, String name,
            Preferences base, Map<String, String> overrides) {
        super(parent, name);
        this.base = base;
        this.overrides = Map.copyOf(overrides);
    }

    @Override
    protected String getSpi(String key) {
        String over = overrides.get(key);
        if (over != null) {
            return over;
        }
        return base == null ? null : base.get(key, null);
    }

    @Override
    protected void putSpi(String key, String value) {
        if (base != null) {
            base.put(key, value);
        }
    }

    @Override
    protected void removeSpi(String key) {
        if (base != null) {
            base.remove(key);
        }
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
        throw new BackingStoreException("an .editorconfig overlay cannot be removed");
    }

    @Override
    protected String[] keysSpi() throws BackingStoreException {
        Set<String> keys = new LinkedHashSet<>();
        if (base != null) {
            keys.addAll(java.util.List.of(base.keys()));
        }
        keys.addAll(overrides.keySet());
        return keys.toArray(String[]::new);
    }

    @Override
    protected String[] childrenNamesSpi() throws BackingStoreException {
        return base == null ? new String[0] : base.childrenNames();
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        // children carry no .editorconfig meaning: pass them straight through
        return new OverlayPreferences(this, name, base == null ? null : base.node(name), Map.of());
    }

    @Override
    protected void syncSpi() throws BackingStoreException {
        if (base != null) {
            base.sync();
        }
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        if (base != null) {
            base.flush();
        }
    }

    /** For the tests: the values this overlay answers instead of its base. */
    Map<String, String> overrides() {
        return overrides;
    }
}
