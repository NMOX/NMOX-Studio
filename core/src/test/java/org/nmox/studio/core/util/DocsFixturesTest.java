package org.nmox.studio.core.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocsFixturesTest {

    private static final String FIXTURES = """
            {"en": {"board": {"epic": "Payment", "todo": ["a", "b"]},
                    "db": {"rows": [["Ada", "Bristol", "412"]]}},
             "de": {"board": {"epic": "Kasse", "todo": ["x"]}}}
            """;

    @Test
    @DisplayName("no public signature carries an org.json type — each module loads its own org.json")
    void onlyJdkTypesCrossTheBoundary() {
        List<String> leaks = new ArrayList<>();
        for (Method m : DocsFixtures.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            List<Type> types = new ArrayList<>(List.of(m.getGenericParameterTypes()));
            types.add(m.getGenericReturnType());
            for (Type t : types) {
                if (t.getTypeName().contains("org.json")) {
                    leaks.add(m.getName() + " -> " + t.getTypeName());
                }
            }
        }
        assertThat(leaks)
                .as("a JSONObject from core is a different class in every other module's loader")
                .isEmpty();
    }

    @Test
    @DisplayName("a language's own section wins")
    void ownSectionWins() {
        assertThat(DocsFixtures.text(FIXTURES, "de", "board", "epic")).isEqualTo("Kasse");
        assertThat(DocsFixtures.strings(FIXTURES, "de", "board", "todo")).containsExactly("x");
    }

    @Test
    @DisplayName("a missing section falls back to English, section by section")
    void missingSectionFallsBack() {
        assertThat(DocsFixtures.rows(FIXTURES, "de", "db", "rows"))
                .containsExactly(List.of("Ada", "Bristol", "412"));
    }

    @Test
    @DisplayName("a missing language, or none, reads English")
    void missingLanguageReadsEnglish() {
        assertThat(DocsFixtures.text(FIXTURES, "fr", "board", "epic")).isEqualTo("Payment");
        assertThat(DocsFixtures.text(FIXTURES, "", "board", "epic")).isEqualTo("Payment");
    }
}
