package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON→TypeScript emission rules (v2.33.0), each pinned: nested
 * interfaces, merged array elements with optionals, honest unknowns,
 * quoted property names, non-JSON refusal.
 */
class JsonTypesTest {

    @Test
    @DisplayName("a flat object becomes one interface with primitive types")
    void flatObject() {
        assertThat(JsonTypes.interfacesFor(
                "{\"id\": 7, \"name\": \"Ada\", \"active\": true}", "User"))
                .isEqualTo("interface User {\n"
                        + "  active: boolean;\n"
                        + "  id: number;\n"
                        + "  name: string;\n"
                        + "}\n");
    }

    @Test
    @DisplayName("nested objects become nested interfaces, emitted dependency-first")
    void nestedObjects() {
        String ts = JsonTypes.interfacesFor(
                "{\"name\": \"Ada\", \"address\": {\"city\": \"London\", \"zip\": \"N1\"}}",
                "User");
        assertThat(ts).contains("interface Address {\n  city: string;\n  zip: string;\n}");
        assertThat(ts).contains("address: Address;");
        assertThat(ts.indexOf("interface Address"))
                .as("dependencies emit before their users")
                .isLessThan(ts.indexOf("interface User"));
    }

    @Test
    @DisplayName("array elements merge: union of keys, missing keys optional")
    void arrayMerging() {
        String ts = JsonTypes.interfacesFor(
                "{\"users\": [ {\"id\": 1, \"name\": \"Ada\"}, {\"id\": 2, \"email\": \"g@x.io\"} ]}",
                "Team");
        assertThat(ts).contains("users: User[];");
        assertThat(ts).contains("id: number;");
        assertThat(ts).contains("name?: string;");
        assertThat(ts).contains("email?: string;");
    }

    @Test
    @DisplayName("honest unknowns: null values and empty arrays never guess")
    void honestUnknowns() {
        String ts = JsonTypes.interfacesFor(
                "{\"tags\": [], \"meta\": null}", "Thing");
        assertThat(ts).contains("tags: unknown[];");
        assertThat(ts).contains("meta: unknown;");
    }

    @Test
    @DisplayName("property names quote only when TS requires it")
    void quotedProps() {
        String ts = JsonTypes.interfacesFor(
                "{\"content-type\": \"a\", \"plain\": \"b\"}", "H");
        assertThat(ts).contains("\"content-type\": string;");
        assertThat(ts).contains("plain: string;");
    }

    @Test
    @DisplayName("a top-level array types its elements and aliases the list")
    void topLevelArray() {
        String ts = JsonTypes.interfacesFor(
                "[ {\"id\": 1}, {\"id\": 2} ]", "User");
        assertThat(ts).startsWith("type Users = User[];");
        assertThat(ts).contains("interface User {\n  id: number;\n}");
    }

    @Test
    @DisplayName("non-JSON refuses with null — the button's disable cue")
    void refusals() {
        assertThat(JsonTypes.interfacesFor("<html>not json</html>", "X")).isNull();
        assertThat(JsonTypes.interfacesFor("42", "X")).isNull();
        assertThat(JsonTypes.interfacesFor(null, "X")).isNull();
    }
}
