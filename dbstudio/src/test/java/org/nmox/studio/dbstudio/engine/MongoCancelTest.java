package org.nmox.studio.dbstudio.engine;

import com.mongodb.MongoInterruptedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real cancel for MongoDB (debt ledger 10): the interrupt seam, and
 * the backend's execute path reporting a cancel instead of a driver
 * message. No server — the "blocked command" is a thread sleeping the
 * way a socket read blocks.
 */
class MongoCancelTest {

    @Test
    @DisplayName("cancel interrupts the thread running the command and records the request")
    void cancelInterruptsTheRunningThread() throws Exception {
        MongoCancel cancel = new MongoCancel();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean cleanAfterEnd = new AtomicBoolean();
        Thread runner = new Thread(() -> {
            cancel.begin();
            try {
                started.countDown();
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt(); // the driver leaves the flag set too
            } finally {
                cancel.end();
                cleanAfterEnd.set(!Thread.currentThread().isInterrupted());
            }
        }, "mongo-cancel-test");
        runner.setDaemon(true);
        runner.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        cancel.cancel();
        runner.join(5_000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(interrupted).isTrue();
        assertThat(cancel.requested()).isTrue();
        assertThat(cleanAfterEnd).as("end() swallows the interrupt it delivered").isTrue();
    }

    @Test
    @DisplayName("cancel when idle interrupts nobody and records nothing")
    void cancelWhenIdleIsANoOp() {
        MongoCancel cancel = new MongoCancel();
        cancel.cancel();
        assertThat(cancel.requested()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        cancel.begin();
        cancel.end();
        cancel.cancel(); // a late click after the run finished
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("request() records without interrupting; interruptRunner() interrupts only after a request")
    void requestAndInterruptAreSeparate() {
        MongoCancel cancel = new MongoCancel();
        assertThat(cancel.request()).as("idle: nothing to cancel").isFalse();

        cancel.begin();
        cancel.interruptRunner(); // no request yet
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        assertThat(cancel.request()).isTrue();
        assertThat(Thread.currentThread().isInterrupted())
                .as("the server kill runs between request and interrupt").isFalse();
        cancel.interruptRunner();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        cancel.end();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("begin() clears the previous run's request, so the next statement runs")
    void beginClearsTheOldRequest() {
        MongoCancel cancel = new MongoCancel();
        cancel.begin();
        cancel.cancel();
        cancel.end();
        assertThat(cancel.requested()).isTrue();

        cancel.begin();
        assertThat(cancel.requested()).isFalse();
        cancel.end();
        assertThat(Thread.interrupted()).isFalse();
    }

    @Test
    @DisplayName("stopInterrupting lets the cancelled thread make one more call (the release)")
    void stopInterruptingSwallowsAndDisarms() {
        MongoCancel cancel = new MongoCancel();
        cancel.begin();
        cancel.cancel(); // interrupts this very thread
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        cancel.stopInterrupting();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        cancel.cancel(); // no longer interruptible
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        assertThat(cancel.requested()).as("still a cancelled run").isTrue();
        cancel.end();
    }

    @Test
    @DisplayName("a command interrupted by cancel reports 'Cancelled', not the driver's message")
    void interruptedCommandReportsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        MongoCursorPager.Transport blocked = command -> {
            cancelled.set(true); // Cancel pressed mid-command
            throw new MongoInterruptedException("Interrupted waiting for data", null);
        };

        QueryResult result = MongoBackend.execute(new Document("find", "users"), "{find}",
                50, blocked, cancelled::get, System.nanoTime());

        assertThat(result.isError()).isTrue();
        assertThat(result.error()).startsWith("Cancelled");
        assertThat(result.statement()).isEqualTo("{find}");
    }

    @Test
    @DisplayName("a cancel landing while pages are followed reports 'Cancelled' and releases the cursor")
    void cancelDuringPagingReportsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Document> killed = new AtomicReference<>();
        MongoCursorPager.Transport server = new MongoCursorPager.Transport() {
            @Override
            public Document run(Document command) {
                cancelled.set(true);
                return Document.parse("{\"cursor\": {\"id\": 9, \"ns\": \"db.users\","
                        + " \"firstBatch\": [{\"n\": 1}]}, \"ok\": 1}");
            }

            @Override
            public Document release(Document killCursors) {
                killed.set(killCursors);
                return new Document("ok", 1);
            }
        };

        QueryResult result = MongoBackend.execute(new Document("find", "users"), "{find}",
                50, server, cancelled::get, System.nanoTime());

        assertThat(result.error()).startsWith("Cancelled");
        assertThat(killed.get()).isNotNull();
        assertThat(killed.get().getString("killCursors")).isEqualTo("users");
    }

    @Test
    @DisplayName("a cancel requested before the command is sent sends nothing")
    void cancelBeforeSendSendsNothing() {
        AtomicBoolean sent = new AtomicBoolean();
        QueryResult result = MongoBackend.execute(new Document("ping", 1), "{ping}", 50,
                command -> {
                    sent.set(true);
                    return new Document("ok", 1);
                }, () -> true, System.nanoTime());

        assertThat(result.error()).startsWith("Cancelled");
        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("without a cancel, a failure is the driver's own message and paging maps to a grid")
    void failureAndSuccessWithoutCancel() {
        QueryResult failed = MongoBackend.execute(new Document("find", "users"), "{find}", 50,
                command -> {
                    throw new IllegalStateException("not authorized on appdb");
                }, () -> false, System.nanoTime());
        assertThat(failed.error()).isEqualTo("not authorized on appdb");

        QueryResult paged = MongoBackend.execute(new Document("find", "users"), "{find}", 2,
                command -> command.containsKey("getMore")
                        ? Document.parse("{\"cursor\": {\"id\": 0, \"ns\": \"db.users\","
                                + " \"nextBatch\": [{\"n\": 2}, {\"n\": 3}]}, \"ok\": 1}")
                        : Document.parse("{\"cursor\": {\"id\": 7, \"ns\": \"db.users\","
                                + " \"firstBatch\": [{\"n\": 1}]}, \"ok\": 1}"),
                () -> false, System.nanoTime());
        assertThat(paged.isResultSet()).isTrue();
        assertThat(paged.rows()).hasSize(2);
        assertThat(paged.truncated()).isTrue();
    }
}
