package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Insomnia import's promises: the flat resources[] list gets its
 * folder structure back, {@code {{ _.var }}} becomes our
 * {@code {{var}}}, auth follows the secrets law into the keychain
 * field, and everything unrepresentable is refused BY NAME.
 */
class InsomniaCodecTest {

    private static String export(String resources) {
        return """
               {"_type":"export","__export_format":4,"resources":[%s]}"""
                .formatted(resources);
    }

    @Test
    @DisplayName("parentId chains become 'Folder / Request' names; workspace names the collection")
    void structureComesBack() {
        var got = InsomniaCodec.parse(export("""
                {"_type":"workspace","_id":"wrk_1","name":"Shop API"},
                {"_type":"request_group","_id":"fld_1","parentId":"wrk_1","name":"Admin"},
                {"_type":"request_group","_id":"fld_2","parentId":"fld_1","name":"Users"},
                {"_type":"request","_id":"req_1","parentId":"fld_2","name":"List",
                 "method":"get","url":"https://api.example.com/users"},
                {"_type":"request","_id":"req_2","parentId":"wrk_1","name":"Ping",
                 "method":"GET","url":"https://api.example.com/ping"}"""));
        assertThat(got.name()).isEqualTo("Shop API");
        assertThat(got.requests()).extracting(r -> r.name)
                .containsExactly("Admin / Users / List", "Ping");
        assertThat(got.requests().get(0).method).isEqualTo("GET");
    }

    @Test
    @DisplayName("Insomnia {{ _.var }} templates become API Studio {{var}}")
    void templatesTranslate() {
        var got = InsomniaCodec.parse(export("""
                {"_type":"request","_id":"r","name":"T","method":"POST",
                 "url":"{{ _.baseUrl }}/orders",
                 "headers":[{"name":"X-Trace","value":"{{ _.traceId }}"}],
                 "body":{"mimeType":"application/json",
                         "text":"{\\"id\\": \\"{{ _.orderId }}\\"}"}}"""));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.url).isEqualTo("{{baseUrl}}/orders");
        assertThat(r.headers.get(0).value).isEqualTo("{{traceId}}");
        assertThat(r.body).contains("{{orderId}}");
    }

    @Test
    @DisplayName("Bearer and basic auth land in the Auth field, never a header row")
    void authFollowsTheSecretsLaw() {
        var got = InsomniaCodec.parse(export("""
                {"_type":"request","_id":"a","name":"B","method":"GET",
                 "url":"https://x/a",
                 "authentication":{"type":"bearer","token":"sk-live-42"}},
                {"_type":"request","_id":"b","name":"C","method":"GET",
                 "url":"https://x/b",
                 "authentication":{"type":"basic","username":"u","password":"p"}},
                {"_type":"request","_id":"c","name":"D","method":"GET",
                 "url":"https://x/c",
                 "authentication":{"type":"oauth2","accessTokenUrl":"https://t"}}"""));
        assertThat(got.requests().get(0).authType).isEqualTo(AuthType.BEARER);
        assertThat(got.requests().get(0).authToken).isEqualTo("sk-live-42");
        assertThat(got.requests().get(1).authType).isEqualTo(AuthType.BASIC);
        assertThat(got.requests().get(1).authToken).isEqualTo("u:p");
        assertThat(got.requests()).allSatisfy(r ->
                assertThat(r.headers).noneMatch(
                        h -> "Authorization".equalsIgnoreCase(h.name)));
        assertThat(got.notes()).anyMatch(n -> n.contains("oauth2"));
    }

    @Test
    @DisplayName("Environment plain values come along; params keep disabled flags")
    void environmentsAndParams() {
        var got = InsomniaCodec.parse(export("""
                {"_type":"environment","_id":"env_1","parentId":"wrk_1",
                 "data":{"baseUrl":"https://api.example.com","retries":3,
                         "nested":{"not":"imported"}}},
                {"_type":"request","_id":"r","name":"S","method":"GET",
                 "url":"https://x/s",
                 "parameters":[{"name":"q","value":"cats"},
                               {"name":"debug","value":"1","disabled":true}]}"""));
        assertThat(got.variables())
                .containsEntry("baseUrl", "https://api.example.com")
                .containsEntry("retries", "3")
                .doesNotContainKey("nested");
        assertThat(got.requests().get(0).params).hasSize(2);
        assertThat(got.requests().get(0).params.get(1).enabled).isFalse();
    }

    @Test
    @DisplayName("Multipart, WebSocket, and gRPC are refused by name")
    void honestRefusals() {
        var got = InsomniaCodec.parse(export("""
                {"_type":"request","_id":"u","name":"Up","method":"POST",
                 "url":"https://x/u",
                 "body":{"mimeType":"multipart/form-data","params":[]}},
                {"_type":"websocket_request","_id":"w","name":"Live"},
                {"_type":"grpc_request","_id":"g","name":"Rpc"}"""));
        assertThat(got.requests().get(0).body).isEmpty();
        assertThat(got.notes()).anyMatch(n -> n.contains("multipart"));
        assertThat(got.notes()).anyMatch(n -> n.contains("WebSocket"));
        assertThat(got.notes()).anyMatch(n -> n.contains("gRPC"));
    }

    @Test
    @DisplayName("Not-an-export and empty exports refuse with the fix")
    void refusesNonExports() {
        assertThatThrownBy(() -> InsomniaCodec.parse("{\"resources\": []}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insomnia export");
        assertThatThrownBy(() -> InsomniaCodec.parse(export(
                "{\"_type\":\"workspace\",\"_id\":\"w\",\"name\":\"Empty\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No HTTP requests");
        assertThatThrownBy(() -> InsomniaCodec.parse("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
