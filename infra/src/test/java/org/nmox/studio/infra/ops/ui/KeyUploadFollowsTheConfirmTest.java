package org.nmox.studio.infra.ops.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The law 3.12.0's review wrote after finding the opposite: this
 * machine's public key goes to the account only AFTER the priced question
 * was answered yes — a No must leave nothing behind at the provider. It
 * holds for every provider because no provider is asked by the dialog:
 * the dialog only reports that the user wants the key added, and the one
 * upload in the window is made by the caller with the dialog's answer in
 * hand, off the event thread.
 *
 * <p>A source law, because the question is a modal dialog no headless
 * test can answer; the providers' own tests hold the other half (a
 * catalogue read and a create upload nothing).
 */
class KeyUploadFollowsTheConfirmTest {

    private static String source(String name) throws IOException {
        String text = Files.readString(Path.of("src/main/java/org/nmox/studio/infra/ops/ui", name)).replace("\r\n", "\n");
        // comments say the word too; only code counts
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("the New Server dialog uploads nothing: it cannot reach a provider's key upload before its own confirm")
    void theDialogNeverUploads() throws IOException {
        assertThat(source("OpsDialogs.java")).doesNotContain("addSshKey");
    }

    @Test
    @DisplayName("the window's one upload is made with the dialog's answer in hand, on a worker, and only when the answer asked for it")
    void theOneUploadFollowsTheAnswer() throws IOException {
        String window = source("OpsHubTopComponent.java");
        assertThat(window.split("\\.addSshKey\\(", -1)).as("exactly one upload site").hasSize(2);
        int answered = window.indexOf("OpsDialogs.newServer(");
        int cancelled = window.indexOf("if (answer == null)", answered);
        int asked = window.indexOf("answer.addLocalKey()", cancelled);
        int handed = window.indexOf("createWithLocalKey(chosen, answer.spec(), local)", asked);
        assertThat(answered).isPositive();
        assertThat(cancelled).as("a cancelled dialog (or a No to the price) returns first").isGreaterThan(answered);
        assertThat(asked).isGreaterThan(cancelled);
        assertThat(handed).isGreaterThan(asked);
        int method = window.indexOf("private void createWithLocalKey(");
        int worker = window.indexOf("DIALOG_PREP.post(", method);
        int upload = window.indexOf(".addSshKey(", method);
        int create = window.indexOf("hub.createHost(", upload);
        assertThat(method).isGreaterThan(handed);
        assertThat(worker).as("the upload is the provider's network: off the event thread").isBetween(method, upload);
        assertThat(create).as("the server is asked for after the key is on the account").isGreaterThan(upload);
        assertThat(window.substring(0, method)).as("no upload outside that method").doesNotContain(".addSshKey(");
    }
}
