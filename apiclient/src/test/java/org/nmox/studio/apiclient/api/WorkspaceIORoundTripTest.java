package org.nmox.studio.apiclient.api;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workspace persistence: everything toJson writes, fromJson restores
 * with nothing lost - and fields from a newer file degrade to a polite
 * skip instead of a crash.
 */
class WorkspaceIORoundTripTest {

    private static Workspace richWorkspace() {
        Workspace w = new Workspace();

        Collection users = new Collection();
        users.name = "Users API";
        Request create = new Request();
        create.name = "Create user";
        create.method = "POST";
        create.url = "{{base}}/users";
        create.body = "{\"name\":\"ada\"}";
        create.params.add(new Pair("expand", "profile"));
        Pair debug = new Pair("debug", "1");
        debug.enabled = false;
        create.params.add(debug);
        create.headers.add(new Pair("X-Api-Key", "{{key}}"));
        Pair trace = new Pair("X-Trace", "off");
        trace.enabled = false;
        create.headers.add(trace);
        create.authType = AuthType.BASIC;
        create.authToken = "user:pass";
        create.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "201"));
        create.tests.add(new Assertion(Assertion.Kind.TIME_UNDER_MS, "500"));
        create.tests.add(new Assertion(Assertion.Kind.BODY_CONTAINS, "id"));
        create.tests.add(new Assertion(Assertion.Kind.JSON_HAS_PATH, "data.id"));
        create.tests.add(new Assertion(Assertion.Kind.HEADER_PRESENT, "Location"));
        users.requests.add(create);
        w.collections.add(users);

        Collection empty = new Collection();
        empty.name = "Drafts";
        w.collections.add(empty);

        Environment local = new Environment();
        local.name = "Local";
        local.variables.put("base", "http://localhost:3000");
        local.variables.put("key", "dev-key");
        w.environments.add(local);
        Environment prod = new Environment();
        prod.name = "Prod";
        prod.variables.put("base", "https://api.x.dev");
        prod.variables.put("key", "prod-key");
        w.environments.add(prod);
        w.activeEnvironment = "Prod";
        return w;
    }

    @Test
    @DisplayName("A fully-loaded workspace survives toJson/fromJson with nothing lost")
    void fullRoundTrip() {
        Workspace back = WorkspaceIO.fromJson(WorkspaceIO.toJson(richWorkspace()));

        assertThat(back.collections).extracting(c -> c.name)
                .containsExactly("Users API", "Drafts");
        assertThat(back.collections.get(1).requests).isEmpty();

        Request req = back.collections.get(0).requests.get(0);
        assertThat(req.name).isEqualTo("Create user");
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.url).isEqualTo("{{base}}/users");
        assertThat(req.body).isEqualTo("{\"name\":\"ada\"}");
        assertThat(req.authType).isEqualTo(AuthType.BASIC);
        assertThat(req.authToken).isEqualTo("user:pass");

        assertThat(req.params).hasSize(2);
        assertThat(req.params.get(0).name).isEqualTo("expand");
        assertThat(req.params.get(0).enabled).isTrue();
        assertThat(req.params.get(1).name).isEqualTo("debug");
        assertThat(req.params.get(1).enabled).as("disabled param stays disabled").isFalse();

        assertThat(req.headers).hasSize(2);
        assertThat(req.headers.get(0).value).isEqualTo("{{key}}");
        assertThat(req.headers.get(1).enabled).as("disabled header stays disabled").isFalse();

        assertThat(req.tests).extracting(a -> a.kind).containsExactly(
                Assertion.Kind.STATUS_IS, Assertion.Kind.TIME_UNDER_MS,
                Assertion.Kind.BODY_CONTAINS, Assertion.Kind.JSON_HAS_PATH,
                Assertion.Kind.HEADER_PRESENT);
        assertThat(req.tests).extracting(a -> a.target)
                .containsExactly("201", "500", "id", "data.id", "Location");

        assertThat(back.environments).extracting(e -> e.name).containsExactly("Local", "Prod");
        assertThat(back.activeEnvironment).isEqualTo("Prod");
        assertThat(back.active()).isNotNull();
        assertThat(back.active().variables)
                .containsEntry("base", "https://api.x.dev")
                .containsEntry("key", "prod-key");
    }

    @Test
    @DisplayName("save writes .nmoxapi.json beside the project and load reads the same workspace back")
    void saveAndLoad(@TempDir Path dir) throws Exception {
        WorkspaceIO.save(dir.toFile(), richWorkspace());

        assertThat(dir.resolve(WorkspaceIO.FILENAME)).exists();
        assertThat(Files.readString(dir.resolve(WorkspaceIO.FILENAME))).contains("\"version\": 1");

        Workspace back = WorkspaceIO.load(dir.toFile());
        assertThat(back).isNotNull();
        assertThat(back.activeEnvironment).isEqualTo("Prod");
        assertThat(back.collections.get(0).requests.get(0).tests).hasSize(5);
    }

    @Test
    @DisplayName("A corrupt .nmoxapi.json is copied to .bak BEFORE the empty fallback")
    void corruptFileIsBackedUpBeforeEmptyFallback(@TempDir Path dir) throws Exception {
        String corrupt = "{ \"collections\": [ definitely-not-json";
        Files.writeString(dir.resolve(WorkspaceIO.FILENAME), corrupt);

        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir.toFile());

        assertThat(outcome.workspace()).as("the fallback is empty (caller starts fresh)").isNull();
        assertThat(outcome.backup()).isNotNull();
        assertThat(outcome.backup().getName()).isEqualTo(WorkspaceIO.FILENAME + ".bak");
        assertThat(Files.readString(outcome.backup().toPath()))
                .as("the backup carries the original bytes").isEqualTo(corrupt);
    }

    @Test
    @DisplayName("A missing file makes no backup and no workspace; a clean file makes no backup")
    void guardedLoadHandlesMissingAndCleanFiles(@TempDir Path dir) throws Exception {
        WorkspaceIO.LoadOutcome missing = WorkspaceIO.loadGuarded(dir.toFile());
        assertThat(missing.workspace()).isNull();
        assertThat(missing.backup()).isNull();
        assertThat(dir.resolve(WorkspaceIO.FILENAME + ".bak")).doesNotExist();

        WorkspaceIO.save(dir.toFile(), richWorkspace());
        WorkspaceIO.LoadOutcome clean = WorkspaceIO.loadGuarded(dir.toFile());
        assertThat(clean.workspace()).isNotNull();
        assertThat(clean.workspace().activeEnvironment).isEqualTo("Prod");
        assertThat(clean.backup()).isNull();
        assertThat(dir.resolve(WorkspaceIO.FILENAME + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("Unknown fields, assertion kinds and auth types from a newer file are skipped, not fatal")
    void newerFileDegradesPolitely() {
        String futureJson = """
            {
              "version": 9,
              "futureFlag": true,
              "activeEnvironment": "Local",
              "collections": [{
                "name": "c",
                "pinned": true,
                "requests": [{
                  "name": "r",
                  "method": "POST",
                  "url": "https://x.dev",
                  "authType": "OAUTH2",
                  "retryPolicy": {"count": 3},
                  "tests": [
                    {"kind": "REGEX_MATCHES", "target": ".*"},
                    {"kind": "STATUS_IS", "target": "200"}
                  ]
                }]
              }],
              "environments": [{"name": "Local", "variables": {"a": "1"}}]
            }
            """;

        Workspace back = WorkspaceIO.fromJson(futureJson);

        Request req = back.collections.get(0).requests.get(0);
        assertThat(req.authType).as("unknown auth type falls back to NONE")
                .isEqualTo(AuthType.NONE);
        assertThat(req.tests).as("only the known assertion kind survives").hasSize(1);
        assertThat(req.tests.get(0).kind).isEqualTo(Assertion.Kind.STATUS_IS);
        assertThat(back.active().variables).containsEntry("a", "1");
    }

    @Test
    @DisplayName("An empty JSON object loads as an empty workspace with no active environment")
    void emptyObjectLoadsEmpty() {
        Workspace back = WorkspaceIO.fromJson("{}");

        assertThat(back.collections).isEmpty();
        assertThat(back.environments).isEmpty();
        assertThat(back.activeEnvironment).isEmpty();
        assertThat(back.active()).isNull();
    }

    @Test
    @DisplayName("The active environment is found by name; a dangling name resolves to null")
    void danglingActiveEnvironmentIsNull() {
        Workspace w = richWorkspace();
        w.activeEnvironment = "Ghost";

        Workspace back = WorkspaceIO.fromJson(WorkspaceIO.toJson(w));

        assertThat(back.activeEnvironment).isEqualTo("Ghost");
        assertThat(back.active()).isNull();
    }
}
