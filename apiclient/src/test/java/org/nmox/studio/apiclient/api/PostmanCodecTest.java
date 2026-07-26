package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Postman import's promises: folders keep identity in the name,
 * secrets follow the v1.97.0 law into the Auth field, Postman's own
 * {@code {{variables}}} survive verbatim, and everything the model
 * can't represent is refused BY NAME, never silently mangled.
 */
class PostmanCodecTest {

    private static String collection(String items) {
        return """
               {"info":{"name":"Pet API","schema":
               "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
               "item":[%s]}""".formatted(items);
    }

    @Test
    @DisplayName("Folders flatten into 'Folder / Request' names, depth-first")
    void foldersKeepIdentity() {
        var got = PostmanCodec.parse(collection("""
                {"name":"Admin","item":[
                  {"name":"Users","item":[
                    {"name":"List","request":{"method":"GET",
                      "url":"https://api.example.com/users"}}]}]},
                {"name":"Ping","request":{"method":"GET",
                  "url":"https://api.example.com/ping"}}"""));
        assertThat(got.name()).isEqualTo("Pet API");
        assertThat(got.requests()).extracting(r -> r.name)
                .containsExactly("Admin / Users / List", "Ping");
    }

    @Test
    @DisplayName("Bearer auth lands in the Auth field — never a header row")
    void bearerFollowsTheSecretsLaw() {
        var got = PostmanCodec.parse(collection("""
                {"name":"Me","request":{"method":"GET",
                  "url":"https://api.example.com/me",
                  "auth":{"type":"bearer","bearer":[
                    {"key":"token","value":"sk-live-123"}]}}}"""));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.authType).isEqualTo(AuthType.BEARER);
        assertThat(r.authToken).isEqualTo("sk-live-123");
        assertThat(r.headers).noneMatch(
                h -> "Authorization".equalsIgnoreCase(h.name));
    }

    @Test
    @DisplayName("Collection-level auth is inherited; request-level wins")
    void authInheritance() {
        String json = """
               {"info":{"name":"C"},"auth":{"type":"bearer","bearer":[
                 {"key":"token","value":"inherited"}]},
                "item":[
                 {"name":"A","request":{"method":"GET","url":"https://x/a"}},
                 {"name":"B","request":{"method":"GET","url":"https://x/b",
                   "auth":{"type":"basic","basic":[
                     {"key":"username","value":"u"},
                     {"key":"password","value":"p"}]}}}]}""";
        var got = PostmanCodec.parse(json);
        assertThat(got.requests().get(0).authType).isEqualTo(AuthType.BEARER);
        assertThat(got.requests().get(0).authToken).isEqualTo("inherited");
        assertThat(got.requests().get(1).authType).isEqualTo(AuthType.BASIC);
        assertThat(got.requests().get(1).authToken).isEqualTo("u:p");
    }

    @Test
    @DisplayName("The query string moves to the params grid with Postman's enabled flags")
    void queryBecomesParams() {
        var got = PostmanCodec.parse(collection("""
                {"name":"Search","request":{"method":"GET","url":{
                  "raw":"https://api.example.com/search?q=cats&debug=1",
                  "query":[{"key":"q","value":"cats"},
                           {"key":"debug","value":"1","disabled":true}]}}}"""));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.url).isEqualTo("https://api.example.com/search");
        assertThat(r.params).hasSize(2);
        assertThat(r.params.get(0).enabled).isTrue();
        assertThat(r.params.get(1).enabled).isFalse();
    }

    @Test
    @DisplayName("Path :variables become {{variables}}; ports are untouched")
    void pathVariables() {
        var got = PostmanCodec.parse(collection("""
                {"name":"One","request":{"method":"GET",
                  "url":"http://localhost:8080/users/:userId/pets/:petId"}}"""));
        assertThat(got.requests().get(0).url)
                .isEqualTo("http://localhost:8080/users/{{userId}}/pets/{{petId}}");
    }

    @Test
    @DisplayName("Postman {{variables}} import verbatim — they ARE our syntax")
    void variablesVerbatim() {
        var got = PostmanCodec.parse(collection("""
                {"name":"V","request":{"method":"POST",
                  "url":"{{baseUrl}}/orders",
                  "header":[{"key":"X-Trace","value":"{{traceId}}"}],
                  "body":{"mode":"raw","raw":"{\\"id\\": \\"{{orderId}}\\"}"}}}"""));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.url).isEqualTo("{{baseUrl}}/orders");
        assertThat(r.headers.get(0).value).isEqualTo("{{traceId}}");
        assertThat(r.body).contains("{{orderId}}");
    }

    @Test
    @DisplayName("urlencoded renders faithfully; formdata is refused by name")
    void bodyModes() {
        var got = PostmanCodec.parse(collection("""
                {"name":"Form","request":{"method":"POST","url":"https://x/f",
                  "body":{"mode":"urlencoded","urlencoded":[
                    {"key":"a","value":"1"},
                    {"key":"skip","value":"no","disabled":true},
                    {"key":"b","value":"2"}]}}},
                {"name":"Upload","request":{"method":"POST","url":"https://x/u",
                  "body":{"mode":"formdata","formdata":[
                    {"key":"file","type":"file","src":"/tmp/x.png"}]}}}"""));
        assertThat(got.requests().get(0).body).isEqualTo("a=1&b=2");
        assertThat(got.requests().get(0).headers)
                .anyMatch(h -> "Content-Type".equalsIgnoreCase(h.name)
                        && h.value.contains("urlencoded"));
        assertThat(got.requests().get(1).body).isEmpty();
        assertThat(got.notes()).anyMatch(n -> n.contains("form-data"));
    }

    @Test
    @DisplayName("A graphql body becomes the JSON envelope Postman actually sends")
    void graphqlEnvelope() {
        var got = PostmanCodec.parse(collection("""
                {"name":"G","request":{"method":"POST","url":"https://x/graphql",
                  "body":{"mode":"graphql","graphql":{
                    "query":"query { pets { id } }",
                    "variables":"{\\"limit\\": 5}"}}}}"""));
        String body = got.requests().get(0).body;
        assertThat(body).contains("\"query\"").contains("pets { id }")
                .contains("\"limit\": 5");
    }

    @Test
    @DisplayName("Scripts are counted and named as not-runnable, never dropped silently")
    void scriptsAreCounted() {
        var got = PostmanCodec.parse(collection("""
                {"name":"S","event":[{"listen":"test","script":{"exec":
                  ["pm.test('ok', function () {});"]}}],
                 "request":{"method":"GET","url":"https://x/s"}}"""));
        assertThat(got.notes()).anyMatch(n -> n.contains("1 Postman script"));
    }

    @Test
    @DisplayName("An apikey survives as a visible row, with the plaintext warning")
    void apikeyIsVisibleAndNamed() {
        var got = PostmanCodec.parse(collection("""
                {"name":"K","request":{"method":"GET","url":"https://x/k",
                  "auth":{"type":"apikey","apikey":[
                    {"key":"key","value":"X-Api-Key"},
                    {"key":"value","value":"abc123"},
                    {"key":"in","value":"header"}]}}}"""));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.headers).anyMatch(
                h -> "X-Api-Key".equals(h.name) && "abc123".equals(h.value));
        assertThat(got.notes()).anyMatch(n -> n.contains(".nmoxapi.json"));
    }

    @Test
    @DisplayName("Collection variables come along; disabled ones don't")
    void collectionVariables() {
        var got = PostmanCodec.parse("""
                {"info":{"name":"V"},
                 "variable":[{"key":"baseUrl","value":"https://api.example.com"},
                             {"key":"old","value":"x","disabled":true}],
                 "item":[{"name":"A","request":"{{baseUrl}}/a"}]}""");
        assertThat(got.variables())
                .containsEntry("baseUrl", "https://api.example.com")
                .doesNotContainKey("old");
        // and the string-shorthand request imported as a GET
        assertThat(got.requests().get(0).method).isEqualTo("GET");
    }

    @Test
    @DisplayName("v1 collections and environment files are refused with the fix")
    void honestRefusals() {
        assertThatThrownBy(() -> PostmanCodec.parse(
                """
                {"id":"1","name":"Old","requests":[{"url":"https://x"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2.1");
        assertThatThrownBy(() -> PostmanCodec.parse(
                """
                {"name":"Env","values":[{"key":"a","value":"1"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");
        assertThatThrownBy(() -> PostmanCodec.parse("not json at all"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PostmanCodec.parse(
                """
                {"info":{"name":"Empty"},"item":[]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No requests");
    }
}
