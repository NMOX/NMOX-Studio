package org.nmox.studio.apiclient.api;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.api.ApiClient.Credential;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The credential as the SEND will see it, variables resolved (3.4): a
 * token of {@code {{token}}} unset or empty in the environment went out as
 * {@code Bearer } and nothing, a Basic credential without its colon went
 * out with no header, and a hand-made {@code Authorization} row was never
 * looked at.
 */
class ResolvedCredentialTest {

    private static Request bearer(String token) {
        Request r = new Request();
        r.url = "http://localhost/x";
        r.authType = AuthType.BEARER;
        r.authToken = token;
        return r;
    }

    @Test
    @DisplayName("a token variable that is empty or unset is no credential")
    void emptyOrUnsetVariable() {
        assertThat(ApiClient.credential(bearer("{{token}}"), Map.of("token", "")))
                .isEqualTo(Credential.EMPTY);
        assertThat(ApiClient.credential(bearer("{{token}}"), Map.of()))
                .as("an unset variable stays literal and would have gone out as written")
                .isEqualTo(Credential.EMPTY);
        assertThat(ApiClient.credential(bearer("{{token}}"), Map.of("token", "abc")))
                .isEqualTo(Credential.PRESENT);
        assertThat(ApiClient.credential(bearer("abc"), Map.of())).isEqualTo(Credential.PRESENT);
    }

    @Test
    @DisplayName("Basic without its colon, once resolved, is refused")
    void basicWithoutColon() {
        Request r = bearer("{{creds}}");
        r.authType = AuthType.BASIC;
        assertThat(ApiClient.credential(r, Map.of("creds", "alice"))).isEqualTo(Credential.NO_COLON);
        assertThat(ApiClient.credential(r, Map.of("creds", "alice:pw"))).isEqualTo(Credential.PRESENT);
    }

    @Test
    @DisplayName("an Authorization header row that resolves to a bare scheme is refused")
    void authorizationHeaderRow() {
        Request r = bearer("");
        r.authType = AuthType.NONE;
        r.headers.add(new Pair("Authorization", "Bearer {{token}}"));
        assertThat(ApiClient.credential(r, Map.of())).isEqualTo(Credential.HEADER_EMPTY);
        assertThat(ApiClient.credential(r, Map.of("token", ""))).isEqualTo(Credential.HEADER_EMPTY);
        assertThat(ApiClient.credential(r, Map.of("token", "abc"))).isEqualTo(Credential.PRESENT);

        r.headers.get(0).enabled = false;
        assertThat(ApiClient.credential(r, Map.of())).as("a disabled row sends nothing").isEqualTo(Credential.PRESENT);
        r.headers.get(0).enabled = true;
        r.headers.get(0).name = "X-Api-Key";
        assertThat(ApiClient.credential(r, Map.of())).as("only the Authorization row is judged")
                .isEqualTo(Credential.PRESENT);
    }

    @Test
    @DisplayName("the send itself refuses, never going out")
    void sendRefuses() {
        ApiResponse response = new ApiClient().send(bearer("{{token}}"), Map.of());
        assertThat(response.reached()).isFalse();
        assertThat(response.error()).contains("EMPTY");
    }
}
