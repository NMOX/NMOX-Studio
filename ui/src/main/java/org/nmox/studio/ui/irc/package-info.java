/**
 * The IRC client's WINDOW layer (⌥⌘3). Three-layer architecture, each
 * in its own package so the boundaries are visible in the imports:
 * {@code irc.protocol} (pure message parsing — no sockets, no Swing),
 * {@code irc.engine} (connections, TLS+SASL, logging — no Swing), and
 * this package (the {@code TopComponent}, tree of networks/channels,
 * transcripts, input line).
 *
 * <p>The window classes to read first:
 * {@code IrcTopComponent} routes engine events onto the EDT and owns
 * the view keys ({@code network + '\u0000' + target});
 * {@link org.nmox.studio.ui.irc.SmartFilter} is WeeChat's signature
 * join/part/quit filter as a pure class (speech within five minutes
 * earns a presence line);
 * {@link org.nmox.studio.ui.irc.Hotlist} decides where Ctrl+J jumps
 * (mentions outrank unread). Everything server-controlled renders
 * PLAIN — nick lists and topics go through {@code PlainTables}, because
 * an IRC server is external input (v1.307.0).
 */
package org.nmox.studio.ui.irc;
