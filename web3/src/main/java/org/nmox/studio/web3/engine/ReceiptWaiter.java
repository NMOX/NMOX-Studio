package org.nmox.studio.web3.engine;

import java.util.Locale;

/**
 * The pure polling-decision model behind "wait for the receipt": the
 * UI loop calls {@code eth_getTransactionReceipt}, hands each answer
 * (or null while pending) to {@link #onReceipt}, and obeys the returned
 * {@link Decision} — sleep {@code delayMillis} and poll again, or stop
 * on a terminal state with a human message ready for the status bar.
 *
 * <p>Backoff: the first wait is {@value #FIRST_DELAY_MILLIS} ms,
 * doubling to a {@value #MAX_DELAY_MILLIS} ms ceiling, within a total
 * budget of {@value #DEFAULT_BUDGET_MILLIS} ms — after which the
 * verdict is {@link State#TIMED_OUT}, not an exception: a devnet that
 * stalls is a fact to report, not a crash.
 *
 * <p>Not thread-safe; one waiter per transaction, used from one thread.
 */
public final class ReceiptWaiter {

    /** Total waiting budget: 30 seconds. */
    public static final long DEFAULT_BUDGET_MILLIS = 30_000;

    static final long FIRST_DELAY_MILLIS = 500;
    static final long MAX_DELAY_MILLIS = 2_000;

    /** Where the wait stands after a poll. */
    public enum State {
        /** No receipt yet and budget remains — sleep and poll again. */
        WAITING,
        /** Mined with status 1. */
        SUCCESS,
        /** Mined with status 0 — the transaction reverted. */
        REVERTED,
        /** The budget ran out with no receipt. */
        TIMED_OUT
    }

    /**
     * One poll's verdict.
     *
     * @param state       where the wait stands
     * @param message     status-bar-ready ("Mined in block 12 · gas used
     *                    21,000"); terminal states always carry one
     * @param delayMillis how long to sleep before the next poll; 0 for
     *                    terminal states
     */
    public record Decision(State state, String message, long delayMillis) {

        /** True when the loop should stop. */
        public boolean terminal() {
            return state != State.WAITING;
        }
    }

    private final long budgetMillis;
    private long waitedMillis;
    private long nextDelayMillis = FIRST_DELAY_MILLIS;
    private int polls;

    public ReceiptWaiter() {
        this(DEFAULT_BUDGET_MILLIS);
    }

    public ReceiptWaiter(long budgetMillis) {
        if (budgetMillis <= 0) {
            throw new IllegalArgumentException("The waiting budget must be positive.");
        }
        this.budgetMillis = budgetMillis;
    }

    /**
     * Records one poll result and decides what happens next.
     *
     * @param receipt the node's answer; null while the tx is pending
     */
    public Decision onReceipt(JsonRpcClient.Receipt receipt) {
        polls++;
        if (receipt != null) {
            if (receipt.success()) {
                return new Decision(State.SUCCESS,
                        "Mined in block " + receipt.blockNumber() + " · gas used "
                        + String.format(Locale.ROOT, "%,d", receipt.gasUsed()), 0);
            }
            return new Decision(State.REVERTED,
                    "Reverted in block " + receipt.blockNumber() + ".", 0);
        }
        if (waitedMillis >= budgetMillis) {
            return new Decision(State.TIMED_OUT,
                    "No receipt after " + (budgetMillis / 1000)
                    + " s — the transaction may still be pending on the node.", 0);
        }
        long delay = Math.min(nextDelayMillis, budgetMillis - waitedMillis);
        waitedMillis += delay;
        nextDelayMillis = Math.min(nextDelayMillis * 2, MAX_DELAY_MILLIS);
        return new Decision(State.WAITING,
                "Waiting for the receipt… (poll " + polls + ")", delay);
    }

    /** How many polls this waiter has judged. */
    public int polls() {
        return polls;
    }
}
