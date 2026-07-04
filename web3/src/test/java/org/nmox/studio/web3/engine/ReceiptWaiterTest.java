package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The receipt-wait decision model: terminal verdicts with human
 * messages, doubling backoff under a hard budget, and a timeout that is
 * a reported fact, not an exception.
 */
class ReceiptWaiterTest {

    private static JsonRpcClient.Receipt receipt(boolean success, long block, long gas) {
        return new JsonRpcClient.Receipt("0xtx", success, "", block, gas, List.of());
    }

    @Test
    @DisplayName("a successful receipt is terminal with block and formatted gas")
    void success() {
        ReceiptWaiter.Decision decision = new ReceiptWaiter()
                .onReceipt(receipt(true, 7, 21_000));

        assertThat(decision.state()).isEqualTo(ReceiptWaiter.State.SUCCESS);
        assertThat(decision.terminal()).isTrue();
        assertThat(decision.message()).isEqualTo("Mined in block 7 · gas used 21,000");
        assertThat(decision.delayMillis()).isZero();
    }

    @Test
    @DisplayName("a status-0 receipt is REVERTED with the block named")
    void reverted() {
        ReceiptWaiter.Decision decision = new ReceiptWaiter()
                .onReceipt(receipt(false, 9, 30_000));

        assertThat(decision.state()).isEqualTo(ReceiptWaiter.State.REVERTED);
        assertThat(decision.message()).isEqualTo("Reverted in block 9.");
    }

    @Test
    @DisplayName("pending receipts back off 500 → 1000 → 2000, capped at 2000")
    void backoffDoublesToCap() {
        ReceiptWaiter waiter = new ReceiptWaiter();

        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(500);
        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(1000);
        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(2000);
        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(2000);
        assertThat(waiter.polls()).isEqualTo(4);
    }

    @Test
    @DisplayName("waiting decisions say WAITING and count their polls")
    void waitingMessage() {
        ReceiptWaiter waiter = new ReceiptWaiter();

        ReceiptWaiter.Decision decision = waiter.onReceipt(null);

        assertThat(decision.state()).isEqualTo(ReceiptWaiter.State.WAITING);
        assertThat(decision.terminal()).isFalse();
        assertThat(decision.message()).contains("poll 1");
    }

    @Test
    @DisplayName("the budget exhausts into TIMED_OUT with the honest still-pending message")
    void timesOutAfterBudget() {
        ReceiptWaiter waiter = new ReceiptWaiter(3_000);

        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(500);
        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(1000);
        // capped to the remaining budget, not the doubled 2000
        assertThat(waiter.onReceipt(null).delayMillis()).isEqualTo(1500);

        ReceiptWaiter.Decision last = waiter.onReceipt(null);
        assertThat(last.state()).isEqualTo(ReceiptWaiter.State.TIMED_OUT);
        assertThat(last.message()).isEqualTo(
                "No receipt after 3 s — the transaction may still be pending on the node.");
        assertThat(last.delayMillis()).isZero();
    }

    @Test
    @DisplayName("the default budget reports 30 s in the timeout message")
    void defaultBudgetMessage() {
        ReceiptWaiter waiter = new ReceiptWaiter();
        ReceiptWaiter.Decision decision;
        do {
            decision = waiter.onReceipt(null);
        } while (!decision.terminal());

        assertThat(decision.state()).isEqualTo(ReceiptWaiter.State.TIMED_OUT);
        assertThat(decision.message()).contains("after 30 s");
    }

    @Test
    @DisplayName("a receipt arriving after waits still wins over the clock")
    void lateReceiptStillTerminal() {
        ReceiptWaiter waiter = new ReceiptWaiter(1_000);
        waiter.onReceipt(null);
        waiter.onReceipt(null);

        ReceiptWaiter.Decision decision = waiter.onReceipt(receipt(true, 12, 100));

        assertThat(decision.state()).isEqualTo(ReceiptWaiter.State.SUCCESS);
    }

    @Test
    @DisplayName("a non-positive budget is refused with a human message")
    void budgetMustBePositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReceiptWaiter(0))
                .withMessage("The waiting budget must be positive.");
    }
}
