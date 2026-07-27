package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAPI YAML — the import family's last honest refusal, closed. The
 * YAML path must produce EXACTLY what the equivalent JSON produces
 * (one parser downstream, two front doors), and hostile YAML tags must
 * be refused by the safe loader, never instantiated.
 */
class OpenApiYamlTest {

    private static final String YAML = """
            openapi: 3.0.0
            info:
              title: Pet API
            servers:
              - url: https://api.example.com/v1
            paths:
              /pets/{petId}:
                get:
                  summary: Get a pet
                  parameters:
                    - name: petId
                      in: path
                      required: true
                    - name: verbose
                      in: query
                      required: true
                      example: "true"
            """;

    private static final String JSON = """
            {"openapi":"3.0.0","info":{"title":"Pet API"},
             "servers":[{"url":"https://api.example.com/v1"}],
             "paths":{"/pets/{petId}":{"get":{"summary":"Get a pet",
               "parameters":[
                 {"name":"petId","in":"path","required":true},
                 {"name":"verbose","in":"query","required":true,
                  "example":"true"}]}}}}
            """;

    @Test
    @DisplayName("YAML and the equivalent JSON import identically")
    void yamlEqualsJson() {
        var fromYaml = OpenApiCodec.parse(YAML);
        var fromJson = OpenApiCodec.parse(JSON);

        assertThat(fromYaml.title()).isEqualTo(fromJson.title()).isEqualTo("Pet API");
        assertThat(fromYaml.variables()).isEqualTo(fromJson.variables());
        assertThat(fromYaml.requests()).hasSameSizeAs(fromJson.requests());
        var y = fromYaml.requests().get(0);
        var j = fromJson.requests().get(0);
        assertThat(y.method).isEqualTo(j.method);
        assertThat(y.url).isEqualTo(j.url).isEqualTo("{{baseUrl}}/pets/{{petId}}");
        assertThat(y.name).isEqualTo(j.name);
        assertThat(y.params).hasSameSizeAs(j.params);
    }

    @Test
    @DisplayName("A hostile YAML tag is refused by the safe loader, never instantiated")
    void hostileTagsRefused() {
        assertThatThrownBy(() -> OpenApiCodec.parse("""
                openapi: 3.0.0
                info: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader []]
                paths: {}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not valid YAML");
    }

    @Test
    @DisplayName("YAML that isn't a mapping is refused with the reason")
    void nonMappingRefused() {
        assertThatThrownBy(() -> OpenApiCodec.parse("- just\n- a\n- list\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    @DisplayName("Swagger 2 in YAML gets the same honest refusal as in JSON")
    void swaggerYamlRefused() {
        assertThatThrownBy(() -> OpenApiCodec.parse("swagger: \"2.0\"\npaths: {}\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OpenAPI 3");
    }
}
