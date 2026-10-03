package org.nmox.studio.tools.vscode;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.nmox.studio.core.util.PlainDialogs;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputOption;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

/**
 * The question one {@code ${input:id}} asks, as VS Code asks it: a {@code
 * promptString} is a line of text (hidden as it is typed when the input
 * says {@code "password": true}), a {@code pickString} a list of its
 * options with the default chosen. One dialog per input; OK answers,
 * Cancel answers nothing and the run does not start.
 *
 * <p>Every word in the dialog but its buttons is the repository's own —
 * the description, the options, the default — so it is shown as TEXT
 * (the v1.208.0 law): the description rides {@link PlainDialogs}, the
 * options {@link PlainText}. A password's characters are wiped from the
 * field's array as soon as the answer is taken; the answer itself lives
 * as long as the launch that needs it.
 */
final class VsCodeTaskPrompts {

    private static final Logger LOG = Logger.getLogger(VsCodeTaskPrompts.class.getName());

    private VsCodeTaskPrompts() {
    }

    /**
     * Asks {@code input} on behalf of the task {@code taskLabel}; empty
     * when the user cancels. Any thread: the dialog is built and shown on
     * the event thread and waited for.
     */
    static Optional<String> ask(String taskLabel, InputDef input) {
        if (EventQueue.isDispatchThread()) {
            return show(taskLabel, input);
        }
        AtomicReference<Optional<String>> out = new AtomicReference<>(Optional.empty());
        try {
            EventQueue.invokeAndWait(() -> out.set(show(taskLabel, input)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException failed) {
            // no answer is Cancel: the run does not start, and says so
            LOG.log(Level.INFO, "a task's question could not be shown", failed.getCause());
        }
        return out.get();
    }

    private static Optional<String> show(String taskLabel, InputDef input) {
        String question = question(input);
        JComponent field;
        Supplier<String> answer;
        if (input.pick()) {
            List<InputOption> options = input.options();
            JComboBox<String> pick = new JComboBox<>(shown(options));
            pick.setSelectedIndex(defaultIndex(input));
            pick.getAccessibleContext().setAccessibleName(question);
            field = pick;
            answer = () -> options.get(Math.max(0, pick.getSelectedIndex())).value();
        } else if (input.password()) {
            JPasswordField secret = TextDirection.keepLeftToRight(new JPasswordField(28));
            if (input.defaultValue() != null) {
                secret.setText(input.defaultValue());
            }
            secret.getAccessibleContext().setAccessibleName(question);
            field = secret;
            answer = () -> {
                char[] typed = secret.getPassword();
                try {
                    return new String(typed);
                } finally {
                    Arrays.fill(typed, '\0');
                }
            };
        } else {
            JTextField text = TextDirection.keepLeftToRight(new JTextField(28));
            if (input.defaultValue() != null) {
                text.setText(input.defaultValue());
                text.selectAll();
            }
            text.getAccessibleContext().setAccessibleName(question);
            field = text;
            answer = text::getText;
        }
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(PlainDialogs.plain(question,
                NbBundle.getMessage(VsCodeTaskPrompts.class, "VsCodeTaskPrompts_question")), BorderLayout.PAGE_START);
        panel.add(field, BorderLayout.PAGE_END);
        NotifyDescriptor descriptor = new NotifyDescriptor(panel,
                NbBundle.getMessage(VsCodeTaskPrompts.class, "VsCodeTaskPrompts_title", taskLabel),
                NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.QUESTION_MESSAGE, null,
                NotifyDescriptor.OK_OPTION);
        return NotifyDescriptor.OK_OPTION.equals(DialogDisplayer.getDefault().notify(descriptor))
                ? Optional.of(answer.get()) : Optional.empty();
    }

    /** What the dialog asks: the input's description, or its id when the description says nothing. */
    static String question(InputDef input) {
        return input.description() == null || input.description().isBlank() ? input.id() : input.description();
    }

    /** The options as the list shows them — VS Code's {@code label: value} — each one text, never markup. */
    static String[] shown(List<InputOption> options) {
        return options.stream().map(o -> PlainText.plain(o.display())).toArray(String[]::new);
    }

    /** The option chosen when the dialog opens: the one whose value is the input's default, else the first. */
    static int defaultIndex(InputDef input) {
        for (int i = 0; i < input.options().size(); i++) {
            if (input.options().get(i).value().equals(input.defaultValue())) {
                return i;
            }
        }
        return 0;
    }
}
