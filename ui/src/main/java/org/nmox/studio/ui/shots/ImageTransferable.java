package org.nmox.studio.ui.shots;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * An image on the clipboard, and nothing else: the one flavor is
 * {@link DataFlavor#imageFlavor}, so a paste target that wants text gets
 * an honest "unsupported" rather than a stringified image. Used by Copy
 * Editor Screenshot (v2.87.0) — the developer evangelist pastes straight
 * into a chat or a slide far more often than they save a file.
 */
public final class ImageTransferable implements Transferable {

    private final Image image;

    public ImageTransferable(Image image) {
        this.image = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {DataFlavor.imageFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return image;
    }
}
