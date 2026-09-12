package org.nmox.studio.ui.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.ContractKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Contract Kit's chain list, and the restraint that makes it right.
 *
 * <p>Ten of the eleven rows are pure technology. Translating "Cairo
 * (Starknet)" would not help anybody and would break the one thing a chain
 * name is for: being recognised. So the law here is mostly a law about NOT
 * translating, with one row that does carry a word.
 */
class ChainTextTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    @Test
    @DisplayName("a chain whose label is a word is read in the reader's language")
    void theOneLabelWithAWordIsTranslated() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(ChainText.label(ContractKit.Chain.SOLANA))
                .as("\"native program\" is prose sitting beside a chain's name")
                .isNotEqualTo(ContractKit.Chain.SOLANA.label)
                .startsWith("Solana");
    }

    @Test
    @DisplayName("a chain whose label is only names survives every language")
    void aNameOnlyLabelIsUntouched() {
        List<String> moved = new ArrayList<>();
        for (Locale locale : List.of(Locale.GERMAN, Locale.FRENCH,
                Locale.forLanguageTag("zh"), Locale.forLanguageTag("hi"))) {
            Locale.setDefault(locale);
            for (ContractKit.Chain chain : ContractKit.Chain.values()) {
                if (chain == ContractKit.Chain.SOLANA) {
                    continue;
                }
                if (!ChainText.label(chain).equals(chain.label)) {
                    moved.add(locale.getLanguage() + "/" + chain.name());
                }
            }
        }
        assertThat(moved).as("a chain's name is how a person recognises it; "
                + "translating one would be a mistake, not a service").isEmpty();
    }

    @Test
    @DisplayName("the English record never moves")
    void englishStaysTheRecord() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(ContractKit.Chain.SOLANA.label).isEqualTo("Solana — native program");
    }
}
