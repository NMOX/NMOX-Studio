package org.nmox.studio.ui.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate over the two shelf dialogs: while a worker moves or recursively
 * deletes a tree, EVERY button on the shelf greys — not a hand-kept subset.
 *
 * <p>v2.184.0 found both halves of the same shape. ManageExperimentsAction
 * built four buttons, greyed three, and carried a comment reading "All three
 * buttons grey while a worker runs" — so Duplicate stayed live through a
 * discard and could fork the very tree being deleted. ManageLearningSpaces
 * greyed two of three during a discard and none at all during its own promote.
 *
 * <p>The population is DERIVED from the buttons each dialog adds to its panel,
 * so a fifth button cannot ship outside the rule, and enablement must go
 * through the shared runnables — a bare {@code someButton.setEnabled(...)}
 * fails here by name, because that is exactly how the subset was written the
 * first time. A count in a comment cannot be kept true; a derived set can.
 */
class ShelfButtonsGateTest {

    private static final Pattern ADDED = Pattern.compile("buttons\\.add\\((\\w+)\\);");
    private static final Pattern DECLARED = Pattern.compile("JButton\\[\\] workButtons = \\{([^}]*)\\}");

    private static String source(String simpleName) throws Exception {
        return Files.readString(Path.of("src/main/java/org/nmox/studio/ui/actions/" + simpleName + ".java"),
                StandardCharsets.UTF_8);
    }

    private static Set<String> addedButtons(String src) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = ADDED.matcher(src);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Set<String> declaredButtons(String src) {
        Matcher m = DECLARED.matcher(src);
        assertThat(m.find()).as("the dialog declares its work buttons in one place").isTrue();
        Set<String> names = new LinkedHashSet<>();
        for (String part : m.group(1).split(",")) {
            String name = part.strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ManageExperimentsAction", "ManageLearningSpacesAction"})
    @DisplayName("every button on the shelf is in the set the workers grey — no subset, no count in a comment")
    void everyShelfButtonIsGreyedByAWorker(String action) throws Exception {
        String src = source(action);
        Set<String> added = addedButtons(src);
        assertThat(added).as("%s adds its buttons to one panel", action).hasSizeGreaterThanOrEqualTo(3);
        assertThat(declaredButtons(src))
                .as("%s: a button on the shelf that no worker greys can act on a tree being moved or deleted", action)
                .containsExactlyInAnyOrderElementsOf(added);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ManageExperimentsAction", "ManageLearningSpacesAction"})
    @DisplayName("enablement runs through the shared runnables — a per-button setEnabled is how the subset was born")
    void enablementHasOneHome(String action) throws Exception {
        String src = source(action);
        List<String> offenders = new ArrayList<>();
        for (String button : addedButtons(src)) {
            if (src.contains(button + ".setEnabled(")) {
                offenders.add(button + ".setEnabled(");
            }
        }
        assertThat(offenders)
                .as("%s: grey the shelf with disableButtons/enableButtons, never one button at a time", action)
                .isEmpty();
        assertThat(src).contains("disableButtons.run()").contains("enableButtons.run()");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ManageExperimentsAction", "ManageLearningSpacesAction"})
    @DisplayName("every worker posted to the shelf's RequestProcessor greys the shelf first")
    void everyWorkerGreysFirst(String action) throws Exception {
        String src = source(action);
        String lane = action.startsWith("ManageExperiments") ? "EXPERIMENTS_RP.post(" : "SPACES_RP.post(";
        int from = src.indexOf("dialog = DialogDisplayer");
        assertThat(from).as("the listeners are wired after the dialog is built").isPositive();
        int workers = 0;
        for (int at = src.indexOf(lane, from); at >= 0; at = src.indexOf(lane, at + 1)) {
            // the nearest disable above this post, inside the same listener
            int guard = src.lastIndexOf("disableButtons.run();", at);
            int listener = src.lastIndexOf(".addActionListener(", at);
            assertThat(guard)
                    .as("%s: the worker posted at offset %d runs with the shelf still live", action, at)
                    .isGreaterThan(listener);
            workers++;
        }
        assertThat(workers).as("%s: the shelf's workers were found", action).isGreaterThanOrEqualTo(2);
    }
}
