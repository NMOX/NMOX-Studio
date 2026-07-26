package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The .http/.rest dialect, pinned against real request-file shapes. */
class HttpFileCodecTest {

    @Test
    @DisplayName("A typical two-request file: names, headers, bodies, blank-line law")
    void typicalFile() {
        var got = HttpFileCodec.parse("""
                @base = https://api.example.com

                ### List users
                GET {{base}}/users?limit=5 HTTP/1.1
                Accept: application/json

                ### Create user
                POST {{base}}/users
                Content-Type: application/json

                {
                  "name": "Ada"
                }
                """);
        assertThat(got.requests()).hasSize(2);
        ApiModel.Request list = got.requests().get(0);
        assertThat(list.name).isEqualTo("List users");
        assertThat(list.method).isEqualTo("GET");
        assertThat(list.url).isEqualTo("{{base}}/users?limit=5");
        assertThat(list.headers).hasSize(1);
        assertThat(list.body).isEmpty();
        ApiModel.Request create = got.requests().get(1);
        assertThat(create.method).isEqualTo("POST");
        assertThat(create.body).contains("\"name\": \"Ada\"");
        assertThat(got.variables()).containsEntry("base", "https://api.example.com");
    }

    @Test
    @DisplayName("A bare URL line means GET; unnamed requests name themselves")
    void bareUrl() {
        var got = HttpFileCodec.parse("https://h/x\n");
        assertThat(got.requests().get(0).method).isEqualTo("GET");
        assertThat(got.requests().get(0).name).isEqualTo("h/x");
    }

    @Test
    @DisplayName("Comments vanish outside the body but survive inside it")
    void commentsOnlyOutsideBody() {
        var got = HttpFileCodec.parse("""
                # a comment
                POST https://h/x
                Content-Type: text/plain
                // another comment

                line1
                // this is body text, not a comment
                """);
        assertThat(got.requests().get(0).body)
                .contains("// this is body text");
        assertThat(got.requests().get(0).headers).hasSize(1);
    }

    @Test
    @DisplayName("Authorization headers are lifted into the Auth field (secrets law)")
    void authLift() {
        var got = HttpFileCodec.parse("""
                GET https://h/a
                Authorization: Bearer tok123

                ###
                GET https://h/b
                Authorization: Basic dXNlcjpwYXNz

                ###
                GET https://h/c
                Authorization: Basic user pass
                """);
        assertThat(got.requests().get(0).authType).isEqualTo(AuthType.BEARER);
        assertThat(got.requests().get(0).authToken).isEqualTo("tok123");
        assertThat(got.requests().get(0).headers).isEmpty();
        assertThat(got.requests().get(1).authToken).isEqualTo("user:pass");
        assertThat(got.requests().get(2).authToken).isEqualTo("user:pass");
    }

    @Test
    @DisplayName("A {{var}} Authorization value stays a header, with a note")
    void varAuthStaysHeader() {
        var got = HttpFileCodec.parse("""
                GET https://h/x
                Authorization: Basic {{creds}}
                """);
        assertThat(got.requests().get(0).authType).isEqualTo(AuthType.NONE);
        assertThat(got.requests().get(0).headers).hasSize(1);
        assertThat(got.notes()).anySatisfy(n -> assertThat(n).contains("keychain"));
    }

    @Test
    @DisplayName("Body file references are refused into a note, never read")
    void bodyFileRefused() {
        var got = HttpFileCodec.parse("""
                POST https://h/x
                Content-Type: application/json

                < ./payload.json
                """);
        assertThat(got.requests().get(0).body).isEmpty();
        assertThat(got.notes()).anySatisfy(n -> assertThat(n).contains("payload.json"));
    }

    @Test
    @DisplayName("An XML body is a body, not a file reference (v1.168.0)")
    void xmlBodyImports() {
        // the review find: startsWith("<") refused every XML payload —
        // the dialect's file reference is "< path", bracket THEN space
        var got = HttpFileCodec.parse("""
                POST https://h/soap
                Content-Type: text/xml

                <envelope><body>hi</body></envelope>
                """);
        assertThat(got.requests().get(0).body)
                .isEqualTo("<envelope><body>hi</body></envelope>");
        assertThat(got.notes()).isEmpty();
    }

    @Test
    @DisplayName("An empty file is an honest error")
    void emptyFile() {
        assertThatThrownBy(() -> HttpFileCodec.parse("# nothing here\n"))
                .hasMessageContaining("No requests");
    }
}
