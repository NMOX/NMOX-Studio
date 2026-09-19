package org.nmox.studio.rack.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.GateSources;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aim's refusal outlives the project opening that hides it (ledger 109).
 *
 * <p>Ledger 104 gave the refusal a voice; the v2.181.0 Hebrew walk measured
 * what that voice was worth and found the sentence gone within about five
 * seconds. The cause is not an overwriter — the entry originally guessed at
 * the platform's project-open progress and that guess was WRONG. Read from the
 * shipped bytecode: {@code NbStatusDisplayer.setStatusText(String)} is
 * {@code add(text, 0); clear(SURVIVING_TIME)} with SURVIVING_TIME =
 * {@code Integer.getInteger("org.openide.awt.StatusDisplayer.DISPLAY_TIME", 5000)}.
 * <b>A plain status message deletes itself.</b> The two-argument form calls
 * {@code add(text, importance)} and returns without scheduling any clear.
 *
 * <p>These are value and wiring laws rather than a rendering assertion: the
 * platform's real displayer is not present in a plain unit test (the fallback
 * is {@code StatusDisplayer$Trivial}, whose two-argument form delegates to the
 * one-argument one), so asserting on a live strip here would assert on a
 * stand-in and prove nothing about what ships. What CAN be pinned is that the
 * refusal asks for a message that does not delete itself, and that the numbers
 * it asks with are the ones the platform's own contract requires.
 */
class PatchRefusalLingersTest {

    /** The platform's documented default, from `Integer.getInteger(…, 5000)` in NbStatusDisplayer. */
    private static final int PLATFORM_SELF_CLEAR_MS = 5_000;

    /** The platform's own importance family, read from StatusDisplayer's class file. */
    private static final int PLATFORM_LOWEST_NAMED_IMPORTANCE = 700; // IMPORTANCE_ERROR_HIGHLIGHT

    private static String source() throws Exception {
        return GateSources.stripComments(Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "rack", "service", "RackService.java"), StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private static int intConstant(String src, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("int\\s+" + name + "\\s*=\\s*([0-9_]+)").matcher(src);
        assertThat(m.find()).as("constant " + name + " is declared").isTrue();
        return Integer.parseInt(m.group(1).replace("_", ""));
    }

    @Test
    @DisplayName("the refusal is set at an importance ABOVE ZERO — that is the whole fix, because importance zero is the branch that schedules its own deletion")
    void theImportanceIsAboveZero() throws Exception {
        int importance = intConstant(source(), "PATCH_REFUSAL_IMPORTANCE");
        assertThat(importance)
                .as("zero takes NbStatusDisplayer's self-clearing branch; the platform also throws on <= 0")
                .isGreaterThan(0);
        assertThat(importance)
                .as("but it must stay under the platform's own family, so an editor annotation or a find still wins the strip")
                .isLessThan(PLATFORM_LOWEST_NAMED_IMPORTANCE);
    }

    @Test
    @DisplayName("it stays longer than the five seconds that were measured too short, and is still BOUNDED — because an importance above zero also hides ordinary status text")
    void theLingerIsLongerThanTheDefaultAndStillBounded() throws Exception {
        int linger = intConstant(source(), "PATCH_REFUSAL_LINGER_MS");
        assertThat(linger).as("longer than the platform default the walk measured too short")
                .isGreaterThan(PLATFORM_SELF_CLEAR_MS);
        assertThat(linger)
                .as("bounded: while it shows, it outranks and therefore HIDES every plain status message")
                .isLessThanOrEqualTo(60_000);
    }

    @Test
    @DisplayName("the refusal call site asks for the lingering form, and the plain self-clearing form never carries it")
    void theRefusalUsesTheLingeringForm() throws Exception {
        String src = source();
        int auto = src.indexOf("private void autoLoadPatch()");
        assertThat(auto).as("autoLoadPatch exists").isPositive();
        String body = src.substring(auto, src.indexOf("private void resetToStarterRack", auto));
        int caught = body.indexOf("catch (Exception");
        assertThat(caught).as("the load is guarded").isPositive();
        String rescue = body.substring(caught);

        assertThat(rescue).as("the refusal asks for a message that does not delete itself")
                .contains("statusThatLingers(patchNotLoadedText(");
        assertThat(rescue).as("and never the plain form, which is add(text, 0) + clear(5000)")
                .doesNotContain("status(patchNotLoadedText(");
    }

    @Test
    @DisplayName("the Message is held in a FIELD, not a local — v2.182.0 asserted the constants, shipped, and the walk found the sentence dying at a GC instead of at its bound")
    void theMessageIsHeldSoAGarbageCollectionCannotTakeIt() throws Exception {
        String src = source();
        int helper = src.indexOf("private static void statusThatLingers(String text)");
        assertThat(helper).as("the lingering helper exists").isPositive();
        String body = src.substring(helper, src.indexOf("private static void status(String text)", helper));

        // NbStatusDisplayer keeps only a WeakReference to each message AND gives
        // MessageImpl a finalize() that removes it — so a message nobody holds
        // dies at the first GC, and a project opening allocates heavily.
        assertThat(body)
                .as("assigned to the held field, never to a local the method drops on return")
                .contains("patchRefusalShown = ");
        assertThat(body)
                .as("a local would compile and pass every constant assertion — that is exactly what v2.182.0 shipped")
                .doesNotContain("Message shown =");

        assertThat(src).as("and the field is really a field, kept for the life of the message")
                .contains("private static volatile org.openide.awt.StatusDisplayer.Message patchRefusalShown");
    }

    @Test
    @DisplayName("the lingering helper holds the returned Message and clears it — the list keeps only a WeakReference, so a set with no clear could vanish at a GC")
    void theHelperKeepsTheMessageAliveAndBoundsIt() throws Exception {
        String src = source();
        int helper = src.indexOf("private static void statusThatLingers(String text)");
        assertThat(helper).as("the lingering helper exists").isPositive();
        String body = src.substring(helper, src.indexOf("private static void status(String text)", helper));

        assertThat(body).as("the two-argument form is what skips the self-clear")
                .contains("setStatusText(").contains("PATCH_REFUSAL_IMPORTANCE");
        assertThat(body).as("the returned Message is bound, not dropped on the floor")
                .contains("clear(PATCH_REFUSAL_LINGER_MS)");
        assertThat(body).as("and the refusal still cannot break an aim if the platform is absent")
                .contains("catch (RuntimeException | LinkageError");
    }
}
