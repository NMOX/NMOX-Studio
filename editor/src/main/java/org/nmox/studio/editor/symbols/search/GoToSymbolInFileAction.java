package org.nmox.studio.editor.symbols.search;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;

/**
 * Navigate ▸ Go to Symbol in This File… (VS Code's Go to Symbol in
 * Editor): opens Quick Search with {@code @} already typed, which lists
 * the file's symbols ({@link FileSymbolSearchProvider}); type a name and
 * press Enter.
 *
 * <p>It has no chord. VS Code's ⇧⌘O is Open Project in four keymap
 * profiles and Fix Imports in Eclipse's, measured in the assembled
 * cluster, and a profile's own meaning of a chord wins.
 *
 * <p><b>How the {@code @} gets there.</b> Quick Search has no API for
 * opening it with text. Its action is a toolbar presenter, and the field
 * is the text component inside the presenter it hands out, reached with
 * nothing but Swing. The field only searches while it has the focus (its
 * document listener asks {@code isFocusOwner}, RELEASE310 bytecode), and
 * clears its hint when the focus arrives, so the {@code @} is typed after
 * the focus has landed. A Quick Search that is not on screen is said on
 * the status line instead.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.symbols.search.GoToSymbolInFileAction")
@ActionRegistration(displayName = "#CTL_GoToSymbolInFile", lazy = true)
@ActionReference(path = "Menu/GoTo", position = 152)
public final class GoToSymbolInFileAction implements ActionListener {

    /** How long a field that never took the focus keeps waiting for it. */
    private static final int FOCUS_WAIT_MILLIS = 2000;

    /** Where a Quick Search that could not be reached is said; a seam for tests. */
    static Consumer<String> status = text -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text));

    @Override
    public void actionPerformed(ActionEvent e) {
        Action quickSearch = Actions.forID("Edit", "org.netbeans.modules.quicksearch.QuickSearchAction");
        JTextComponent field = quickSearch instanceof Presenter.Toolbar toolbar
                ? textFieldIn(toolbar.getToolbarPresenter()) : null;
        if (quickSearch == null || field == null || !field.isShowing()) {
            status.accept(NbBundle.getMessage(GoToSymbolInFileAction.class, "GoToSymbolInFile_noQuickSearch"));
            return;
        }
        quickSearch.actionPerformed(e);
        typeOnceFocused(field, "@");
    }

    /** The first text component inside {@code root}, itself included; null when there is none. */
    static JTextComponent textFieldIn(Component root) {
        if (root instanceof JTextComponent text) {
            return text;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextComponent found = textFieldIn(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Replaces {@code field}'s text with {@code text} once it has the focus:
     * now when it already does, else at the focus event it is waiting for.
     * A field the focus never reaches stops waiting, so a later click into
     * it does not find the text typed for it.
     */
    static void typeOnceFocused(JTextComponent field, String text) {
        Runnable type = () -> {
            field.setText(text);
            field.setCaretPosition(field.getDocument().getLength());
        };
        if (field.isFocusOwner()) {
            type.run();
            return;
        }
        FocusAdapter once = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent gained) {
                field.removeFocusListener(this);
                // after the field's own focus handling, which clears its hint
                EventQueue.invokeLater(type);
            }
        };
        field.addFocusListener(once);
        Timer giveUp = new Timer(FOCUS_WAIT_MILLIS, tick -> field.removeFocusListener(once));
        giveUp.setRepeats(false);
        giveUp.start();
    }
}
