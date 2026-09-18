/**
 * The doors a rack travels through between people (v2.179.0): what the
 * sender says about it ({@link org.nmox.studio.rack.sharing.ShareCards}),
 * where a kept rack lives ({@link org.nmox.studio.rack.sharing.MyRacks},
 * the {@code ~/.nmox/presets.d} drop-in directory the Presets menu has read
 * since v1.294.0), and reading one from pasted text
 * ({@link org.nmox.studio.rack.sharing.RackText}).
 *
 * <p>Everything here is pure or plain file work so it is tested without a
 * window; the Swing half is {@link org.nmox.studio.rack.sharing.ShareDialog}.
 * The format itself — the {@code shared} header, the home rewrite, arrival at
 * rest — is {@link org.nmox.studio.rack.model.RackShare}'s, and what a file
 * says about itself is {@link org.nmox.studio.rack.model.RackCard}'s.
 *
 * <p>Reading order: {@code ShareCards} → {@code MyRacks} → {@code RackText}
 * → {@code ShareDialog}.
 */
package org.nmox.studio.rack.sharing;
