package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OpenAPI 3 (JSON) into API Studio, pinned on a petstore-shaped doc. */
class OpenApiCodecTest {

    private static final String DOC = """
            {
              "openapi": "3.0.3",
              "info": { "title": "Pet Shop" },
              "servers": [ { "url": "http://localhost:8080/v1" } ],
              "paths": {
                "/pets": {
                  "parameters": [
                    { "name": "trace", "in": "header", "example": "on" }
                  ],
                  "get": {
                    "summary": "List pets",
                    "parameters": [
                      { "name": "limit", "in": "query", "required": true, "example": 20 },
                      { "name": "tag", "in": "query" }
                    ]
                  },
                  "post": {
                    "summary": "Create a pet",
                    "requestBody": {
                      "content": {
                        "application/json": {
                          "example": { "name": "Rex", "kind": "dog" }
                        }
                      }
                    }
                  }
                },
                "/pets/{petId}": {
                  "get": { "summary": "Get one pet" }
                }
              },
              "components": { "securitySchemes": { "bearer": { "type": "http" } } }
            }
            """;

    @Test
    @DisplayName("A petstore-shaped doc becomes named requests with params, body and baseUrl")
    void petstore() {
        var got = OpenApiCodec.parse(DOC);
        assertThat(got.title()).isEqualTo("Pet Shop");
        assertThat(got.variables()).containsEntry("baseUrl", "http://localhost:8080/v1");
        assertThat(got.requests()).hasSize(3);

        ApiModel.Request list = got.requests().get(0);
        assertThat(list.name).isEqualTo("List pets");
        assertThat(list.method).isEqualTo("GET");
        assertThat(list.url).isEqualTo("{{baseUrl}}/pets");
        // required param enabled, optional off, path-item header inherited
        assertThat(list.params).hasSize(2);
        assertThat(list.params.get(0).name).isEqualTo("limit");
        assertThat(list.params.get(0).value).isEqualTo("20");
        assertThat(list.params.get(0).enabled).isTrue();
        assertThat(list.params.get(1).enabled).isFalse();
        assertThat(list.headers).extracting(h -> h.name).containsExactly("trace");

        ApiModel.Request create = got.requests().get(1);
        assertThat(create.method).isEqualTo("POST");
        assertThat(create.body).contains("\"name\": \"Rex\"");
        assertThat(create.headers).extracting(h -> h.name)
                .contains("Content-Type");

        // path template {petId} becomes API Studio's own {{petId}}
        assertThat(got.requests().get(2).url).isEqualTo("{{baseUrl}}/pets/{{petId}}");

        // security schemes are noted, never guessed into a token
        assertThat(got.notes()).anySatisfy(n -> assertThat(n).contains("Auth field"));
    }

    @Test
    @DisplayName("Schema-only bodies import as {} with a note")
    void schemaOnlyBody() {
        var got = OpenApiCodec.parse("""
                { "openapi": "3.1.0",
                  "paths": { "/x": { "post": {
                    "requestBody": { "content": { "application/json": {
                      "schema": { "type": "object" } } } } } } } }
                """);
        assertThat(got.requests().get(0).body).isEqualTo("{}");
        assertThat(got.notes()).anySatisfy(n -> assertThat(n).contains("no example"));
    }

    @Test
    @DisplayName("Honest refusals: YAML, Swagger 2, no operations, junk")
    void refusals() {
        // v1.191.0: YAML is no longer refused — it parses through the
        // same pipeline, so an operations-free YAML doc gets the same
        // message its JSON twin always got (see OpenApiYamlTest)
        assertThatThrownBy(() -> OpenApiCodec.parse("openapi: 3.0.0\npaths: {}"))
                .hasMessageContaining("no operations");
        assertThatThrownBy(() -> OpenApiCodec.parse("{\"swagger\":\"2.0\"}"))
                .hasMessageContaining("Swagger 2.0");
        assertThatThrownBy(() -> OpenApiCodec.parse("{\"openapi\":\"3.0.0\",\"paths\":{}}"))
                .hasMessageContaining("no operations");
        assertThatThrownBy(() -> OpenApiCodec.parse("{nope"))
                .hasMessageContaining("Not valid JSON");
    }
}
