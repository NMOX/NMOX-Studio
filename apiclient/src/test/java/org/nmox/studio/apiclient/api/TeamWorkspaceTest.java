package org.nmox.studio.apiclient.api;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a second developer's merge does to {@code .nmoxapi.json} (3.4,
 * question 1), measured on the file shapes a real {@code git merge} leaves.
 */
public class TeamWorkspaceTest {

    /** Git's markers, built so no tracked line of this file starts with one. */
    static final String OURS = "<".repeat(7);
    static final String SPLIT = "=".repeat(7);
    static final String THEIRS = ">".repeat(7);

    /** A workspace file exactly as git leaves it after two people renamed one request. */
    public static String conflicted() {
        return "{\n  \"version\": 1,\n  \"collections\": [{\n    \"name\": \"Payments\",\n"
                + "    \"requests\": [{\n"
                + OURS + " HEAD\n"
                + "      \"name\": \"List charges\",\n"
                + SPLIT + "\n"
                + "      \"name\": \"List all charges\",\n"
                + THEIRS + " feature/bob\n"
                + "      \"id\": \"r1\", \"method\": \"GET\", \"url\": \"{{base}}/charges\"\n"
                + "    }]\n  }],\n  \"environments\": []\n}\n";
    }

    @Test
    @DisplayName("a file holding git's merge conflict is left exactly as it is, and read-only")
    void conflictedFileIsLeftAlone(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), conflicted(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(f.toPath());

        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);

        assertThat(outcome.conflicted()).isTrue();
        assertThat(outcome.readOnly()).as("nothing may be written over it").isTrue();
        assertThat(outcome.workspace()).as("both people's work is in the file, not in a model")
                .isNull();
        assertThat(outcome.backup()).as("it is not corrupt, so nothing is moved aside").isNull();
        assertThat(dir.list()).containsExactly(WorkspaceIO.FILENAME);
        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(before);
    }

    @Test
    @DisplayName("an auth type a newer NMOX Studio wrote binds read-only and refuses to send")
    void newerAuthTypeIsReadOnly(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, WorkspaceIO.FILENAME).toPath(), """
            {"version": 1, "collections": [{"name": "c", "requests": [
              {"id": "r1", "name": "r", "method": "GET", "url": "https://x.dev",
               "authType": "OAUTH2", "tests": []}]}], "environments": []}
            """, StandardCharsets.UTF_8);

        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);

        assertThat(outcome.newerFormat()).isTrue();
        assertThat(outcome.readOnly())
                .as("the next save would have written NONE for everyone").isTrue();
        Request r = outcome.workspace().collections.get(0).requests.get(0);
        assertThat(r.foreignAuthType).isEqualTo("OAUTH2");
        assertThat(ApiClient.credential(r)).isEqualTo(ApiClient.Credential.UNKNOWN_TYPE);
    }

    @Test
    @DisplayName("an assertion kind a newer NMOX Studio wrote binds read-only")
    void newerAssertionIsReadOnly(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, WorkspaceIO.FILENAME).toPath(), """
            {"version": 1, "collections": [{"name": "c", "requests": [
              {"id": "r1", "name": "r", "method": "GET", "url": "https://x.dev",
               "tests": [{"kind": "REGEX_MATCHES", "target": ".*"}]}]}], "environments": []}
            """, StandardCharsets.UTF_8);

        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);

        assertThat(outcome.newerFormat()).isTrue();
        assertThat(outcome.readOnly()).isTrue();
    }

    @Test
    @DisplayName("a known file is neither conflicted nor newer")
    void ordinaryFileIsWritable(@TempDir File dir) throws Exception {
        WorkspaceIO.save(dir, Workspace.starter("A", "B", "C"));
        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);
        assertThat(outcome.readOnly()).isFalse();
    }

    @Test
    @DisplayName("a second corrupt file never overwrites the first rescue")
    void secondRescueKeepsTheFirst(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), "{ first broken", StandardCharsets.UTF_8);
        File first = WorkspaceIO.loadGuarded(dir).backup();
        Files.writeString(f.toPath(), "{ second broken", StandardCharsets.UTF_8);
        File second = WorkspaceIO.loadGuarded(dir).backup();

        assertThat(first.getName()).isEqualTo(WorkspaceIO.FILENAME + ".bak");
        assertThat(second.getName()).isEqualTo(WorkspaceIO.FILENAME + ".2.bak");
        assertThat(Files.readString(first.toPath())).isEqualTo("{ first broken");
        assertThat(Files.readString(second.toPath())).isEqualTo("{ second broken");
    }

    @Test
    @DisplayName("a pre-3.4 file's history and active environment are read once and never written")
    void legacyPersonalFieldsMigrate() {
        Workspace legacy = WorkspaceIO.fromJson("""
            {"version": 1, "activeEnvironment": "Prod",
             "collections": [], "environments": [{"name": "Prod", "variables": {}}],
             "history": [{"timestamp": 7, "name": "old send", "method": "GET", "url": "u"}]}
            """);

        assertThat(legacy.activeEnvironment).isEqualTo("Prod");
        assertThat(legacy.history).extracting(e -> e.name).containsExactly("old send");
        String shared = WorkspaceIO.toJson(legacy);
        assertThat(shared).doesNotContain("old send").doesNotContain("activeEnvironment");
        assertThat(WorkspaceIO.personalJson(legacy)).contains("old send").contains("Prod");
    }

    @Test
    @DisplayName("a Bearer or Basic request with no token is refused, and never sent")
    void missingTokenIsRefused() {
        Request r = new Request();
        r.url = "http://127.0.0.1:9/never";
        assertThat(ApiClient.credential(r)).isEqualTo(ApiClient.Credential.PRESENT);

        r.authType = AuthType.BEARER;
        assertThat(ApiClient.credential(r)).isEqualTo(ApiClient.Credential.MISSING);
        r.authType = AuthType.BASIC;
        r.authToken = "  ";
        assertThat(ApiClient.credential(r)).isEqualTo(ApiClient.Credential.MISSING);

        ApiResponse refused = new ApiClient().send(r, Map.of());
        assertThat(refused.reached()).isFalse();
        assertThat(refused.error()).as("refused before any connection was tried")
                .startsWith("not sent");

        r.authToken = "{{creds}}";
        assertThat(ApiClient.credential(r)).isEqualTo(ApiClient.Credential.PRESENT);
    }
}
