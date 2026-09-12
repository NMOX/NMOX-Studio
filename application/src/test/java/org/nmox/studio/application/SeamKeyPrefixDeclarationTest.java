package org.nmox.studio.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A seam says what it owns, where its author is looking.
 *
 * <p>{@code Bundles.optional} (v2.135.0) marks the one place in the product
 * where a missing key is not a defect: the English it falls back to is the
 * record. That has a consequence two files away — these keys ship in twelve
 * translated bundles and in no base bundle, so {@code
 * LocaleBundleParityTest} must know about them or it reports every one as
 * extra.
 *
 * <p>It knew about them by keeping its own list of prefixes, and that list
 * went stale twice in three releases. Twice is a class, and the class is
 * v2.131.0's: <b>the defect is the second home, not the disagreement.</b>
 * So the fact moved to the seam, built from the same literal the lookup
 * uses, and this gate makes the declaration mandatory — a sixth seam either
 * says what it owns or fails here, with the reason in the message, instead
 * of failing a parity gate whose complaint points at the wrong file.
 *
 * <p>What this gate deliberately does NOT check: that a declared prefix is
 * really the one the lookups build. Two proofs already cover it and both
 * are stronger than reading source. The seam tests ({@code CatalogTextTest},
 * {@code BlockTextTest}, {@code ChainTextTest}, {@code CatalogueSeamsTest})
 * assert real translated values come back from the shipped bundles, so a
 * wrong constant fails them; and the parity gate this feeds reports the
 * real keys as extra, by name, when a prefix does not cover them.
 */
class SeamKeyPrefixDeclarationTest {

    @Test
    @DisplayName("every seam that looks up an optional key declares the families it owns")
    void everySeamDeclaresWhatItOwns() {
        List<Path> callers = SeamKeyPrefixes.callers();
        assertThat(callers)
                .as("shipping files calling Bundles.optional — an empty scan would "
                        + "exempt nothing and prove nothing")
                .isNotEmpty();

        List<String> silent = new ArrayList<>();
        for (Path caller : callers) {
            String name = caller.getFileName().toString();
            if (SeamKeyPrefixes.OWNS_NO_FAMILY.containsKey(name)) {
                continue;
            }
            if (SeamKeyPrefixes.declaredBy(caller).isEmpty()) {
                silent.add(name);
            }
        }
        assertThat(silent).as("a seam whose keys have no base bundle must declare its "
                + "KEY_PREFIXES, or LocaleBundleParityTest will call every one of them "
                + "extra and point at the wrong file. Declare them beside the lookup, "
                + "or record here why this seam owns no family of its own").isEmpty();
    }

    @Test
    @DisplayName("a seam blessed as owning no family gives a real reason")
    void everyBlessingIsADecision() {
        List<String> thin = new ArrayList<>();
        for (Map.Entry<String, String> e : SeamKeyPrefixes.OWNS_NO_FAMILY.entrySet()) {
            if (e.getValue().length() < 60) {
                thin.add(e.getKey() + ": a reason this short decides nothing");
            }
            boolean ships = SeamKeyPrefixes.callers().stream()
                    .anyMatch(p -> p.getFileName().toString().equals(e.getKey()));
            if (!ships) {
                thin.add(e.getKey() + ": blessed, but no shipping file by that name "
                        + "looks up an optional key");
            }
        }
        assertThat(thin).as("a blessing nobody can check has stopped being a decision")
                .isEmpty();
    }

    /**
     * A floor under the derivation, not a second copy of the fact.
     *
     * <p>The families named below are a claim about what ships TODAY, and a
     * sixth seam never needs to touch them — that is the difference between
     * this and the list that used to live in the parity gate. It exists
     * because a reader that resolved a constant to the wrong string would
     * still return something non-empty, so the declaration law above would
     * pass and the failure would surface one phase later, in a gate whose
     * complaint names bundle keys rather than the reader that mangled them.
     */
    @Test
    @DisplayName("the families the parity gate exempts are the ones the seams declare")
    void theExemptionIsDerived() {
        Set<String> declared = SeamKeyPrefixes.all();

        // the five seams that exist today, named so a deletion is loud
        assertThat(declared).as("the catalogue seams' key families, read from the seams")
                .contains("DeviceDesc_", "BlockKind_", "TemplateName_", "TemplateDesc_",
                        "Chain_", "LearnCategory_", "LearnFamily_");

        assertThat(declared).as("every declared family is a key PREFIX, not a whole key: "
                + "a prefix that did not end in its separator would exempt a "
                + "neighbouring family by accident")
                .allSatisfy(prefix -> assertThat(prefix).endsWith("_"));
    }
}
