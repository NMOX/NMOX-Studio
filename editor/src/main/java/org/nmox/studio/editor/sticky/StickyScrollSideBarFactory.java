package org.nmox.studio.editor.sticky;

import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.SideBarFactory;

/**
 * Registers sticky scroll as a North, non-scrollable side bar for EVERY
 * editor (root {@code Editors/SideBar} in layer.xml, positioned after the
 * editor toolbar so the pinned rows sit directly above the text).
 */
public final class StickyScrollSideBarFactory implements SideBarFactory {

    @Override
    public JComponent createSideBar(JTextComponent target) {
        return new StickyScrollSideBar(target);
    }
}
