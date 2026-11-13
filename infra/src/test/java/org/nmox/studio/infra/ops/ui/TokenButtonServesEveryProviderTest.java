package org.nmox.studio.infra.ops.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.ops.provider.DigitalOceanOps;
import org.nmox.studio.infra.ops.provider.HetznerOps;
import org.nmox.studio.infra.ops.provider.OpsProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Hub's Token… button sets a token for every provider that takes one.
 * With one provider it was written for DigitalOcean by name; the second
 * provider (3.13.0) could then be given a token only from another window.
 */
class TokenButtonServesEveryProviderTest {

    @Test
    @DisplayName("every provider class that is not a simulation says it takes a token; the button offers each of them")
    void everyRealProviderIsServed() throws Exception {
        // the population is the source's: a third provider added tomorrow is in it
        List<String> silent = new java.util.ArrayList<>();
        int real = 0;
        try (java.util.stream.Stream<Path> files = Files.list(
                Path.of("src/main/java/org/nmox/studio/infra/ops/provider"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                if (!src.contains("implements OpsProvider")) {
                    continue;
                }
                boolean simulated = src.replaceAll("\\s+", " ").contains("public boolean simulated() { return true; }");
                if (simulated) {
                    assertThat(src).as(f.getFileName() + " is a simulation: no token").doesNotContain("takesToken()");
                    continue;
                }
                real++;
                if (!src.contains("public boolean takesToken()") || !src.contains("public void storeToken(String token)")) {
                    silent.add(f.getFileName().toString());
                }
            }
        }
        assertThat(real).as("real providers in the source").isGreaterThanOrEqualTo(2);
        assertThat(silent).as("real providers the Hub's Token… button could not configure").isEmpty();
        List<OpsProvider> takers = OpsHubTopComponent.tokenTakers(List.of(new DigitalOceanOps(), new HetznerOps()));
        assertThat(takers).extracting(OpsProvider::id).containsExactly(new DigitalOceanOps().id(), new HetznerOps().id());
        assertThat(takers.get(0).id()).isNotEqualTo(takers.get(1).id());
    }

    @Test
    @DisplayName("the default is to take none, so a provider must say it does")
    void theDefaultTakesNone() {
        OpsProvider bare = (OpsProvider) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {OpsProvider.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    return switch (method.getName()) {
                        case "simulated", "configured" -> false;
                        default -> null;
                    };
                });
        assertThat(bare.takesToken()).isFalse();
        assertThat(OpsHubTopComponent.tokenTakers(List.of(bare))).isEmpty();
        assertThat(new DigitalOceanOps().takesToken()).isTrue();
        assertThat(new HetznerOps().takesToken()).isTrue();
    }

    @Test
    @DisplayName("the dialog stores through the provider it was opened for, and names it in its title")
    void theDialogNamesNoProvider() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/infra/ops/ui/OpsDialogs.java"));
        int at = src.indexOf("public static boolean tokens(");
        String body = src.substring(at, src.indexOf("\n    }\n", at));
        assertThat(body).contains("provider.storeToken(token)").contains("Bundle.Tokens_title(provider.displayName())");
        assertThat(body).as("no provider is named in the dialog").doesNotContain("CloudProvider.");
    }
}
