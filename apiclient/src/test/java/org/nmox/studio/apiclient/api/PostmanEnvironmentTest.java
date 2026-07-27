package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The environment import's one hard law: API Studio environments live
 * in the COMMITTABLE .nmoxapi.json, so a value Postman marks secret
 * never crosses the border — counted and pointed at the keychain
 * instead. (The v1.177.0 refusal for these files is closed by this
 * feature; the refusal now points at the menu item.)
 */
class PostmanEnvironmentTest {

    @Test
    @DisplayName("Name and plain values import; the file's identity is kept")
    void happyPath() {
        var got = PostmanCodec.parseEnvironment("""
                {"name":"Staging","values":[
                  {"key":"baseUrl","value":"https://staging.example.com","enabled":true},
                  {"key":"tenant","value":"acme","type":"default"}]}""");
        assertThat(got.name()).isEqualTo("Staging");
        assertThat(got.values())
                .containsEntry("baseUrl", "https://staging.example.com")
                .containsEntry("tenant", "acme");
        assertThat(got.notes()).isEmpty();
    }

    @Test
    @DisplayName("A secret-typed value NEVER imports — counted, keychain named")
    void secretsStayOut() {
        var got = PostmanCodec.parseEnvironment("""
                {"name":"Prod","values":[
                  {"key":"baseUrl","value":"https://api.example.com"},
                  {"key":"apiKey","value":"sk-live-999","type":"secret"},
                  {"key":"old","value":"x","enabled":false}]}""");
        assertThat(got.values()).containsOnlyKeys("baseUrl");
        assertThat(got.values().values()).noneMatch(v -> v.contains("sk-live-999"));
        assertThat(got.notes()).anyMatch(n -> n.contains("secret-typed")
                && n.contains("OS keychain"));
        assertThat(got.notes()).anyMatch(n -> n.contains("disabled"));
    }

    @Test
    @DisplayName("All-secrets, collections, and non-environments refuse with the fix")
    void honestRefusals() {
        assertThatThrownBy(() -> PostmanCodec.parseEnvironment("""
                {"name":"P","values":[
                  {"key":"k","value":"v","type":"secret"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keychain");
        assertThatThrownBy(() -> PostmanCodec.parseEnvironment("""
                {"info":{"name":"C"},"item":[]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postman Collection");
        assertThatThrownBy(() -> PostmanCodec.parseEnvironment("{\"x\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("values");
    }

    @Test
    @DisplayName("The collection import's refusal now points at this feature")
    void refusalPointsAtTheMenuItem() {
        assertThatThrownBy(() -> PostmanCodec.parse("""
                {"name":"Env","values":[{"key":"a","value":"1"}]}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postman Environment…");
    }
}
