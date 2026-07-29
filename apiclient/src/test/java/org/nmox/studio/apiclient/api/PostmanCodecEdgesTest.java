package org.nmox.studio.apiclient.api;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Postman importer's edges: the refusal family (v1 schema, empty
 * collections, all-secret environments), the tolerant-parse family
 * (stray array entries, keyless rows, folders without names), and the
 * body/auth modes the main suite's happy-path fixtures didn't reach —
 * urlencoded, GraphQL, apikey-in-query, v2.0 object-style auth. Every
 * fixture is the JSON shape Postman actually exports.
 */
class PostmanCodecEdgesTest {

    // ---- refusals ----

    @Test
    @DisplayName("a v1 schema url is refused with the re-export fix")
    void v1SchemaRefused() {
        assertThatThrownBy(() -> PostmanCodec.parse("""
                {"info": {"name": "Old", "schema": "https://schema.getpostman.com/json/collection/v1.0.0/"},
                 "item": []}"""))
                .hasMessageContaining("schema v1");
    }

    @Test
    @DisplayName("a collection with no requests anywhere is refused, not imported empty")
    void emptyCollectionRefused() {
        assertThatThrownBy(() -> PostmanCodec.parse(
                "{\"info\": {\"name\": \"Hollow\"}, \"item\": [\"stray\", {\"noRequestHere\": 1}]}"))
                .hasMessageContaining("No requests found");
    }

    @Test
    @DisplayName("an unparseable environment file is refused as not-JSON")
    void environmentNotJson() {
        assertThatThrownBy(() -> PostmanCodec.parseEnvironment("{nope"))
                .hasMessageContaining("Not JSON");
    }

    @Test
    @DisplayName("an all-secret environment is refused, pointing at the keychain")
    void allSecretEnvironmentRefused() {
        assertThatThrownBy(() -> PostmanCodec.parseEnvironment("""
                {"name": "Prod", "values": [
                  {"key": "token", "value": "shh", "type": "secret"}]}"""))
                .hasMessageContaining("all 1 are secret-typed")
                .hasMessageContaining("committable environment");
    }

    // ---- environment tolerance and notes ----

    @Test
    @DisplayName("an environment keeps its name; keyless, disabled and secret rows are counted out")
    void environmentCountsItsSkips() {
        PostmanCodec.ImportedEnvironment env = PostmanCodec.parseEnvironment("""
                {"name": "Staging", "values": [
                  {"value": "keyless"},
                  {"key": "host", "value": "st.example.com"},
                  {"key": "old", "value": "x", "enabled": false},
                  {"key": "apiKey", "value": "shh", "type": "secret"}]}""");

        assertThat(env.name()).isEqualTo("Staging");
        assertThat(env.values()).containsOnlyKeys("host");
        assertThat(env.notes()).anySatisfy(n ->
                assertThat(n).contains("1 secret-typed value NOT imported"));
        assertThat(env.notes()).anySatisfy(n ->
                assertThat(n).contains("1 disabled value skipped"));
    }

    // ---- collection walking ----

    @Test
    @DisplayName("a nameless collection imports as 'Postman import'; one script is counted in the singular")
    void namelessCollectionAndSingularScript() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"item": [
                  {"name": "Ping", "request": "https://api.example.com/ping",
                   "event": [
                     "stray",
                     {"listen": "test"},
                     {"listen": "test", "script": {"exec": []}},
                     {"listen": "test", "script": {"exec": ["", "pm.test('x')"]}}
                   ]}
                ]}""");

        assertThat(imported.name()).isEqualTo("Postman import");
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("1 Postman script "));
    }

    @Test
    @DisplayName("a nameless folder adds no prefix, and folder-level auth is inherited")
    void namelessFolderInheritsAuth() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "  ", "auth": {"type": "bearer",
                                          "bearer": [{"key": "token", "value": "tok-folder"}]},
                   "item": [
                     {"name": "In folder", "request": {"method": "GET",
                        "url": "https://api.example.com/a"}}
                   ]}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.name).isEqualTo("In folder");
        assertThat(r.authType).isEqualTo(AuthType.BEARER);
        assertThat(r.authToken).isEqualTo("tok-folder");
    }

    @Test
    @DisplayName("a nameless request is named by its URL, and 48+ chars gain an ellipsis")
    void namelessRequestNamedByUrl() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"request": {"method": "GET",
                     "url": "https://api.example.com/a/very/long/path/that/keeps/going/and/going"}}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.name).doesNotStartWith("https://").endsWith("…");
        assertThat(r.name.length()).isEqualTo(49);
    }

    @Test
    @DisplayName("an unusual url shape (a number) is left un-imported rather than crashed on")
    void numberUrlTolerated() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "Odd", "request": {"method": "GET", "url": 42}}
                ]}""");
        assertThat(imported.requests().get(0).url).isEmpty();
    }

    @Test
    @DisplayName("a raw query string splits into params; keyless declared entries are skipped")
    void queryStringSplits() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "Raw", "request": {"method": "GET",
                     "url": "https://api.example.com/list?page=2&flag&&q="}},
                  {"name": "Declared", "request": {"method": "GET",
                     "url": {"raw": "https://api.example.com/list?x=1",
                             "query": [{"value": "keyless"}, {"key": "x", "value": "1"}]}}}
                ]}""");

        ApiModel.Request raw = imported.requests().get(0);
        assertThat(raw.url).isEqualTo("https://api.example.com/list");
        assertThat(raw.params).extracting(p -> p.name)
                .containsExactly("page", "flag", "q");

        ApiModel.Request declared = imported.requests().get(1);
        assertThat(declared.params).extracting(p -> p.name).containsExactly("x");
    }

    @Test
    @DisplayName("keyless headers are skipped and disabled headers import switched off")
    void headerTolerance() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "H", "request": {"method": "GET",
                     "url": "https://api.example.com/h",
                     "header": ["stray", {"value": "nameless"},
                                {"key": "X-Debug", "value": "1", "disabled": true}]}}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.headers).hasSize(1);
        assertThat(r.headers.get(0).name).isEqualTo("X-Debug");
        assertThat(r.headers.get(0).enabled).isFalse();
    }

    // ---- auth modes ----

    @Test
    @DisplayName("noauth means exactly nothing; unknown auth types leave an honest note")
    void noauthAndUnknownAuth() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "None", "request": {"method": "GET",
                     "url": "https://a.example.com", "auth": {"type": "noauth"}}},
                  {"name": "Sig", "request": {"method": "GET",
                     "url": "https://b.example.com", "auth": {"type": "awsv4"}}}
                ]}""");

        assertThat(imported.requests().get(0).authType).isEqualTo(AuthType.NONE);
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("Auth type \"awsv4\" not imported"));
    }

    @Test
    @DisplayName("basic auth joins user:pass into the keychain-backed field")
    void basicAuth() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "B", "request": {"method": "GET",
                     "url": "https://a.example.com",
                     "auth": {"type": "basic", "basic": [
                        {"key": "username", "value": "ada"},
                        {"key": "password", "value": "pw"}]}}}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.authType).isEqualTo(AuthType.BASIC);
        assertThat(r.authToken).isEqualTo("ada:pw");
    }

    @Test
    @DisplayName("v2.0 object-style auth params read the same as v2.1 arrays")
    void v20ObjectAuth() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "B", "request": {"method": "GET",
                     "url": "https://a.example.com",
                     "auth": {"type": "bearer", "bearer": {"token": "tok-v20"}}}}
                ]}""");

        assertThat(imported.requests().get(0).authToken).isEqualTo("tok-v20");
    }

    @Test
    @DisplayName("auth sections that are missing or don't carry the key read as empty")
    void authValueMisses() {
        // bearer without its section, and an array without the wanted key:
        // both must import as no-auth rather than crash
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "A", "request": {"method": "GET",
                     "url": "https://a.example.com", "auth": {"type": "bearer"}}},
                  {"name": "B", "request": {"method": "GET",
                     "url": "https://b.example.com",
                     "auth": {"type": "bearer", "bearer": [{"key": "other", "value": "x"}]}}}
                ]}""");

        assertThat(imported.requests().get(0).authType).isEqualTo(AuthType.NONE);
        assertThat(imported.requests().get(1).authType).isEqualTo(AuthType.NONE);
    }

    @Test
    @DisplayName("apikey auth: keyless is dropped, in=query lands in params with the plaintext note")
    void apikeyModes() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "NoKey", "request": {"method": "GET",
                     "url": "https://a.example.com",
                     "auth": {"type": "apikey", "apikey": [{"key": "value", "value": "v"}]}}},
                  {"name": "InQuery", "request": {"method": "GET",
                     "url": "https://b.example.com",
                     "auth": {"type": "apikey", "apikey": [
                        {"key": "key", "value": "api_key"},
                        {"key": "value", "value": "12345"},
                        {"key": "in", "value": "query"}]}}}
                ]}""");

        assertThat(imported.requests().get(0).params).isEmpty();
        assertThat(imported.requests().get(1).params)
                .anySatisfy(p -> {
                    assertThat(p.name).isEqualTo("api_key");
                    assertThat(p.value).isEqualTo("12345");
                });
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("API-key auth imported as a plain api_key row"));
    }

    // ---- body modes ----

    @Test
    @DisplayName("a raw json body brings Content-Type: application/json with it")
    void rawJsonBody() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "J", "request": {"method": "POST",
                     "url": "https://a.example.com",
                     "body": {"mode": "raw", "raw": "{\\"a\\": 1}",
                              "options": {"raw": {"language": "json"}}}}}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.body).isEqualTo("{\"a\": 1}");
        assertThat(r.headers).anySatisfy(h -> {
            assertThat(h.name).isEqualTo("Content-Type");
            assertThat(h.value).isEqualTo("application/json");
        });
    }

    @Test
    @DisplayName("urlencoded fields join as a form body, skipping disabled rows")
    void urlencodedBody() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "F", "request": {"method": "POST",
                     "url": "https://a.example.com",
                     "body": {"mode": "urlencoded", "urlencoded": [
                        {"key": "a", "value": "1"},
                        {"key": "off", "value": "x", "disabled": true},
                        {"key": "b", "value": "2"}]}}}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.body).isEqualTo("a=1&b=2");
        assertThat(r.headers).anySatisfy(h ->
                assertThat(h.value).isEqualTo("application/x-www-form-urlencoded"));
    }

    @Test
    @DisplayName("a GraphQL body becomes the JSON envelope; non-JSON variables ride as text")
    void graphqlBody() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "G", "request": {"method": "POST",
                     "url": "https://a.example.com/graphql",
                     "body": {"mode": "graphql", "graphql": {
                        "query": "query { me { id } }",
                        "variables": "not-json"}}}}
                ]}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.body).contains("query { me { id } }").contains("not-json");
        assertThat(r.headers).anySatisfy(h ->
                assertThat(h.value).isEqualTo("application/json"));
    }

    @Test
    @DisplayName("a file body is refused with a note, never silently read")
    void fileBodyNoted() {
        PostmanCodec.Imported imported = PostmanCodec.parse("""
                {"info": {"name": "C"}, "item": [
                  {"name": "F", "request": {"method": "POST",
                     "url": "https://a.example.com",
                     "body": {"mode": "file", "file": {"src": "/tmp/x.bin"}}}}
                ]}""");

        assertThat(imported.requests().get(0).body).isEmpty();
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("file body not imported"));
    }
}
