package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The OpenAPI importer's edges: version-field refusals, the
 * multi-server note, tolerant path/parameter walking ($ref-only
 * params, duplicate headers), and requestBody shapes beyond the single
 * inline example — examples maps, array/scalar examples, and content
 * types the importer honestly declines.
 */
class OpenApiCodecEdgesTest {

    @Test
    @DisplayName("a JSON document without an openapi field is refused by name")
    void missingVersionRefused() {
        assertThatThrownBy(() -> OpenApiCodec.parse("{\"info\": {\"title\": \"x\"}}"))
                .hasMessageContaining("no 'openapi' version field");
    }

    @Test
    @DisplayName("extra servers are noted; the first becomes {{baseUrl}}")
    void multiServerNote() {
        OpenApiCodec.Imported imported = OpenApiCodec.parse("""
                {"openapi": "3.0.0",
                 "servers": [{"url": "https://api.example.com"},
                             {"url": "https://staging.example.com"}],
                 "paths": {"/ping": {"get": {}},
                           "stray-not-an-object": 42}}""");

        assertThat(imported.variables()).containsEntry("baseUrl", "https://api.example.com");
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("2 servers"));
        assertThat(imported.requests()).hasSize(1);
    }

    @Test
    @DisplayName("$ref-only parameters are skipped; duplicate header params collapse")
    void parameterTolerance() {
        OpenApiCodec.Imported imported = OpenApiCodec.parse("""
                {"openapi": "3.0.0", "paths": {"/a": {
                   "parameters": [{"$ref": "#/components/parameters/X"},
                                  {"name": "X-Trace", "in": "header", "example": "t1"}],
                   "get": {"parameters": [
                      {"name": "x-trace", "in": "header", "example": "t2"},
                      {"name": "session", "in": "cookie"}]}}}}""");

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.headers).filteredOn(h -> "X-Trace".equalsIgnoreCase(h.name))
                .hasSize(1)
                .allSatisfy(h -> assertThat(h.value).isEqualTo("t1"));
    }

    @Test
    @DisplayName("an examples map, an array example, and a scalar example all become bodies")
    void exampleShapes() {
        OpenApiCodec.Imported imported = OpenApiCodec.parse("""
                {"openapi": "3.0.0", "paths": {
                  "/map": {"post": {"requestBody": {"content": {"application/json": {
                     "examples": {"first": {"value": {"a": 1}}}}}}}},
                  "/arr": {"post": {"requestBody": {"content": {"application/json": {
                     "example": [1, 2]}}}}},
                  "/num": {"post": {"requestBody": {"content": {"application/json": {
                     "example": 7}}}}}}}""");

        var bodies = imported.requests().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.url, r -> r.body));
        assertThat(bodies.get("{{baseUrl}}/map")).contains("\"a\": 1");
        assertThat(bodies.get("{{baseUrl}}/arr")).contains("1").contains("2");
        assertThat(bodies.get("{{baseUrl}}/num")).isEqualTo("7");
    }

    @Test
    @DisplayName("a non-JSON body content type is declined with its name")
    void nonJsonBodyDeclined() {
        OpenApiCodec.Imported imported = OpenApiCodec.parse("""
                {"openapi": "3.0.0", "paths": {
                  "/upload": {"post": {"requestBody": {"content": {
                     "application/xml": {"example": "<x/>"}}}}}}}""");

        assertThat(imported.requests().get(0).body).isEmpty();
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("body content type application/xml not imported"));
    }
}
