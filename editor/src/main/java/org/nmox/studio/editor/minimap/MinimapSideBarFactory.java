package org.nmox.studio.editor.minimap;

import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.SideBarFactory;

/**
 * Registers the minimap as an East, non-scrollable side bar for EVERY
 * editor (the root {@code Editors/SideBar} folder in layer.xml, position
 * 6900 — just inside the platform's error stripe at 7000). The platform
 * instantiates one strip per opened editor pane through
 * {@link #createSideBar}; the strip itself decides visibility from the
 * View ▸ Minimap preference.
 */
public final class MinimapSideBarFactory implements SideBarFactory {

    @Override
    public JComponent createSideBar(JTextComponent target) {
        return new MinimapSideBar(target);
    }
}
