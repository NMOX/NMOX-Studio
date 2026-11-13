package org.nmox.studio.infra.ops.ui;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.ops.provider.ProviderOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A price is shown under the sign of the currency its provider states it
 * in. The three sentences that name a price printed {@code $} themselves
 * while DigitalOcean was the only provider; Hetzner's price list says
 * EUR, and a euro figure under a dollar sign in the question that names
 * the bill is a wrong bill. The sign is an argument now, in every
 * language.
 */
class PriceCarriesItsCurrencyTest {

    private static final Path BUNDLES = Path.of("src/main/resources/org/nmox/studio/infra/ops/ui");

    @Test
    @DisplayName("the menu's sign: $ for USD, € for EUR, and any other currency by its own code")
    void sign() {
        assertThat(new ProviderOptions(List.of(), List.of(), List.of(), List.of(), "USD").currencySign()).isEqualTo("$");
        assertThat(new ProviderOptions(List.of(), List.of(), List.of(), List.of(), "EUR").currencySign()).isEqualTo("€");
        assertThat(new ProviderOptions(List.of(), List.of(), List.of(), List.of(), "CHF").currencySign()).isEqualTo("CHF");
    }

    @Test
    @DisplayName("English: the price line, the simulated one and the question that names the bill print the sign they are given")
    void english() {
        assertThat(Bundle.NewServer_cost("4.51", "0.0073", "€")).startsWith("≈ €4.51/month (€0.0073/hour)").doesNotContain("$");
        assertThat(Bundle.NewServer_cost("6.00", "0.00893", "$")).startsWith("≈ $6.00/month ($0.00893/hour)");
        assertThat(Bundle.NewServer_costSimulated("6.00", "$")).startsWith("≈ $6.00/month");
        assertThat(Bundle.NewServer_confirm("nmox-a1", "Hetzner Cloud", "cx22", "Falkenstein, DE", "4.51", "€"))
                .contains("≈ €4.51/month").doesNotContain("$");
    }

    @Test
    @DisplayName("every translation prints the sign it is given where it wrote a dollar sign, and writes none of its own")
    void everyLanguage() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(BUNDLES)) {
            files = s.filter(p -> p.getFileName().toString().matches("Bundle_[a-z]+\\.properties")).sorted().toList();
        }
        assertThat(files).as("the fourteen translations").hasSize(14);
        for (Path file : files) {
            Properties p = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(in);
            }
            String cost = MessageFormat.format(p.getProperty("NewServer_cost"), "4.51", "0.0073", "€");
            String simulated = MessageFormat.format(p.getProperty("NewServer_costSimulated"), "4.51", "€");
            String confirm = MessageFormat.format(p.getProperty("NewServer_confirm"), "a1", "Hetzner Cloud", "cx22", "fsn1", "4.51", "€");
            for (String said : List.of(cost, simulated, confirm)) {
                assertThat(said).as(file.getFileName() + ": " + said).contains("€").contains("4.51").doesNotContain("$");
            }
            assertThat(cost.chars().filter(c -> c == '€').count()).as(file.getFileName() + ": monthly and hourly").isEqualTo(2);
            assertThat(MessageFormat.format(p.getProperty("OpsHub_providerLive"), "Hetzner Cloud")).contains("Hetzner Cloud").doesNotContain("DigitalOcean");
            assertThat(MessageFormat.format(p.getProperty("OpsHub_providerNoToken"), "Hetzner Cloud")).contains("Hetzner Cloud").doesNotContain("DigitalOcean");
        }
    }
}
