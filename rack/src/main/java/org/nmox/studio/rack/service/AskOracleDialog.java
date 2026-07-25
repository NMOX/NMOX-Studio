package org.nmox.studio.rack.service;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.engine.AskOracleEngine;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleConversation;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.RequestProcessor;

/**
 * The Ask ORACLE conversation window: one modeless dialog per selection,
 * a transcript above, a follow-up field below. The subject selection is
 * fixed for the conversation's whole life — follow-ups never widen what
 * was disclosed — and the exchange cap surfaces honestly in the
 * transcript when it is reached. Every send rides the engine's gates;
 * the input disables while a send is in flight so one conversation can
 * never interleave its own turns.
 */
public final class AskOracleDialog {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-ask-oracle", 1, true);

    private final OracleConversation convo;
    private final AskOracleEngine engine;
    private final JTextArea transcript = new JTextArea(22, 76);
    private final JTextField input = new JTextField();
    private final JButton ask = new JButton("Ask");

    private final String model;

    public AskOracleDialog(OracleConversation convo, AskOracleEngine engine) {
        this(convo, engine, OracleClient.MODEL_HAIKU);
    }

    public AskOracleDialog(OracleConversation convo, AskOracleEngine engine, String model) {
        this.convo = convo;
        this.engine = engine;
        this.model = model;
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        transcript.getAccessibleContext().setAccessibleName("ORACLE conversation");
        input.getAccessibleContext().setAccessibleName("Follow-up question");
    }

    /**
     * Opens the window. An empty conversation fires the first ask; a
     * SEEDED one (the device's completed EXPLAIN) renders its history
     * and waits — no auto-send, the exchange already happened.
     * Call on the EDT.
     */
    public void open(String firstQuestion) {
        JPanel south = new JPanel(new BorderLayout(6, 0));
        south.add(input, BorderLayout.CENTER);
        south.add(ask, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(transcript), BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);

        DialogDescriptor dd = new DialogDescriptor(panel,
                "ORACLE — " + convo.title(), false,
                new Object[]{DialogDescriptor.CLOSED_OPTION},
                DialogDescriptor.CLOSED_OPTION, DialogDescriptor.DEFAULT_ALIGN, null, null);
        JDialog dialog = (JDialog) DialogDisplayer.getDefault().createDialog(dd);

        Runnable submit = () -> {
            String text = input.getText().trim();
            if (!text.isEmpty() || convo.exchanges() == 0) {
                send(text.isEmpty() ? firstQuestion : text);
                input.setText("");
            }
        };
        ask.addActionListener(e -> submit.run());
        input.addActionListener(e -> submit.run());

        for (org.nmox.studio.rack.engine.OracleClient.Turn t : convo.history()) {
            append(("user".equals(t.role()) ? "You: " : "ORACLE: ") + t.text() + "\n\n");
        }
        dialog.setVisible(true);
        if (convo.exchanges() == 0) {
            send(firstQuestion);
        }
    }

    /** One exchange: append the question, disable input, answer off-EDT. */
    private void send(String question) {
        String shown = question == null || question.isBlank()
                ? "Explain what this code does." : question;
        append("You: " + shown + "\n");
        busy(true);
        RP.post(() -> {
            AskOracleEngine.Result r = engine.converse(convo, question, model);
            SwingUtilities.invokeLater(() -> {
                append("ORACLE: " + r.text() + "\n\n");
                busy(false);
                if (!convo.canAsk()) {
                    append("[conversation cap reached — start a new Ask from a selection]\n");
                    input.setEnabled(false);
                    ask.setEnabled(false);
                }
            });
        });
    }

    private void append(String text) {
        transcript.append(text);
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    private void busy(boolean b) {
        input.setEnabled(!b);
        ask.setEnabled(!b);
        ask.setText(b ? "Thinking…" : "Ask");
    }
}
