package org.nmox.studio.rack.sharing;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.rack.model.RackCard;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * The one dialog a rack leaves through: the sender says what the rack is
 * (its {@link RackCard}), reads what travels with it, and picks where it goes
 * — a file, the clipboard, or My Racks. Until v2.179.0 Share… was a file
 * chooser, which let a rack leave without a name and without its sender ever
 * seeing the commands and paths inside it.
 *
 * <p>The author field starts EMPTY and stays that way unless typed in: a name
 * is never read from the machine (the v2.176.0 rule — a home path is a
 * username — applied to the one field that asks for one).
 */
@Messages({
    "ShareDialog_title=Share Rack",
    "ShareDialog_name=Name:",
    "ShareDialog_description=What it does:",
    "ShareDialog_descriptionName=What the rack does",
    "ShareDialog_author=Shared by:",
    "ShareDialog_authorHint=Optional — left blank, the file names nobody",
    "ShareDialog_requires=Needs on the PATH:",
    "ShareDialog_requiresHint=Tool names the rack runs, separated by commas — read from its commands, edit freely",
    "# {0} - project kind, e.g. RUST",
    "ShareDialog_fits=Suggest it for {0} projects",
    "ShareDialog_leaving=What leaves with this rack — read it before it goes:",
    "ShareDialog_leavingName=What leaves with this rack",
    "ShareDialog_toFile=Save to File…",
    "ShareDialog_toClipboard=Copy to Clipboard",
    "ShareDialog_toMyRacks=Keep in My Racks"
})
public final class ShareDialog {

    /** Where the sender sent it. */
    public enum Destination { FILE, CLIPBOARD, MY_RACKS }

    /** What the sender said and where it goes. */
    public record Result(RackCard card, Destination destination) {
    }

    private ShareDialog() {
    }

    /**
     * EDT. Shows the dialog; empty when cancelled.
     *
     * @param suggestedName the project's name — a starting point, not a claim
     * @param detectedKind the aimed project's {@code ProjectKind} name, or null/blank when none was detected
     * @param suggestedRequires tools read from the rack's commands ({@link ShareCards#suggestRequires})
     * @param leavingText the audit of what the file carries, already rendered for reading
     */
    public static Optional<Result> ask(String suggestedName, String detectedKind,
            List<String> suggestedRequires, String leavingText) {
        JTextField nameField = new JTextField(suggestedName == null ? "" : suggestedName, 32);
        JTextArea descriptionArea = new JTextArea(3, 32);
        descriptionArea.getAccessibleContext().setAccessibleName(Bundle.ShareDialog_descriptionName());
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextField authorField = new JTextField("", 32);
        authorField.setToolTipText(PlainText.plain(Bundle.ShareDialog_authorHint()));
        JTextField requiresField = new JTextField(String.join(", ", suggestedRequires), 32);
        requiresField.setToolTipText(PlainText.plain(Bundle.ShareDialog_requiresHint()));
        boolean hasKind = detectedKind != null && !detectedKind.isBlank();
        // the label's text is decided first, then guarded whole: the button gate
        // reads the constructor's HEAD, and a ternary there is not a guard
        String fitsText = hasKind ? Bundle.ShareDialog_fits(detectedKind) : "";
        JCheckBox fitsBox = new JCheckBox(PlainText.plain(fitsText), hasKind);
        JTextArea leavingArea = new JTextArea(leavingText == null ? "" : leavingText, 9, 32);
        leavingArea.setEditable(false);
        leavingArea.setLineWrap(false);
        leavingArea.setCaretPosition(0);
        leavingArea.getAccessibleContext().setAccessibleName(Bundle.ShareDialog_leavingName());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.LINE_START;
        int row = 0;
        // each label names its own field in so many words: the input-naming
        // gate reads for setLabelFor(<that variable>), and a helper hides it
        JLabel nameLabel = new JLabel(Bundle.ShareDialog_name());
        nameLabel.setLabelFor(nameField);
        row = place(panel, c, row, nameLabel, nameField);
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        JLabel descriptionLabel = new JLabel(Bundle.ShareDialog_description());
        descriptionLabel.setLabelFor(descriptionArea);
        row = place(panel, c, row, descriptionLabel, descriptionScroll);
        JLabel requiresLabel = new JLabel(Bundle.ShareDialog_requires());
        requiresLabel.setLabelFor(requiresField);
        row = place(panel, c, row, requiresLabel, requiresField);
        JLabel authorLabel = new JLabel(Bundle.ShareDialog_author());
        authorLabel.setLabelFor(authorField);
        row = place(panel, c, row, authorLabel, authorField);
        if (hasKind) {
            c.gridx = 1;
            c.gridy = row++;
            c.gridwidth = 1;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            panel.add(fitsBox, c);
        }
        JLabel leavingLabel = new JLabel(Bundle.ShareDialog_leaving());
        leavingLabel.setLabelFor(leavingArea);
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(leavingLabel, c);
        JScrollPane leavingScroll = new JScrollPane(leavingArea);
        leavingScroll.setPreferredSize(new Dimension(520, 170));
        c.gridy = row;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        panel.add(leavingScroll, c);

        Object toFile = Bundle.ShareDialog_toFile();
        Object toClipboard = Bundle.ShareDialog_toClipboard();
        Object toMyRacks = Bundle.ShareDialog_toMyRacks();
        DialogDescriptor descriptor = new DialogDescriptor(panel, Bundle.ShareDialog_title(), true,
                new Object[]{toFile, toClipboard, toMyRacks, DialogDescriptor.CANCEL_OPTION},
                toFile, DialogDescriptor.DEFAULT_ALIGN, null, null);
        Object chosen = DialogDisplayer.getDefault().notify(descriptor);
        Destination destination;
        if (toFile.equals(chosen)) {
            destination = Destination.FILE;
        } else if (toClipboard.equals(chosen)) {
            destination = Destination.CLIPBOARD;
        } else if (toMyRacks.equals(chosen)) {
            destination = Destination.MY_RACKS;
        } else {
            return Optional.empty();
        }
        List<String> kinds = hasKind && fitsBox.isSelected() ? List.of(detectedKind) : List.of();
        RackCard card = new RackCard(nameField.getText(), descriptionArea.getText(), authorField.getText(),
                kinds, ShareCards.splitList(requiresField.getText()));
        return Optional.of(new Result(card, destination));
    }

    private static int place(JPanel panel, GridBagConstraints c, int row, JLabel label, java.awt.Component input) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(input, c);
        return row + 1;
    }
}
