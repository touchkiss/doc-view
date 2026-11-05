package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** A continuously active socket must still obey cancellation and the complete-request deadline. */
public final class ForkHttpCancellationTest extends LightPlatformTestCase {
    private HttpServer server;
    private ExecutorService executor;
    private CountDownLatch firstChunk;
    private CountDownLatch finishResponse;
    private String url;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        firstChunk = new CountDownLatch(1);
        finishResponse = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/slow-chunks", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                do {
                    output.write('x');
                    output.flush();
                    firstChunk.countDown();
                } while (!finishResponse.await(25, TimeUnit.MILLISECONDS));
            } catch (IOException disconnected) {
                // Expected: cancellation/deadline aborts the active request.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow-chunks?token=FAKE_TEST_SECRET";
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            finishResponse.countDown();
            if (server != null) server.stop(0);
            if (executor != null) executor.shutdownNow();
        } finally {
            super.tearDown();
        }
    }

    public void testCancellationAbortsContinuouslyStreamingResponse() throws Exception {
        EmptyProgressIndicator indicator = new EmptyProgressIndicator();
        AtomicReference<Thread> requestThread = new AtomicReference<>();
        Future<Boolean> result = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            requestThread.set(Thread.currentThread());
            try {
                ProgressManager.getInstance().runProcess((Computable<Boolean>) () -> {
                    try {
                        HttpUtils.get(url);
                        return false;
                    } catch (ProcessCanceledException expected) {
                        throw expected;
                    } catch (Exception exception) {
                        throw new AssertionError("Cancellation must retain its control-flow exception", exception);
                    }
                }, indicator);
                return false;
            } catch (ProcessCanceledException expected) {
                return true;
            }
        });
        try {
            assertTrue("The server must be streaming before cancellation", firstChunk.await(5, TimeUnit.SECONDS));
            assertTrue("Cancel only after the client is inside EntityUtils body reading, beyond the header check",
                    awaitBodyRead(requestThread.get()));
            indicator.cancel();
            assertTrue("Cancellation must interrupt an active response without waiting for socket idleness",
                    result.get(3, TimeUnit.SECONDS));
        } finally {
            finishResponse.countDown();
            result.cancel(true);
        }
    }

    private static boolean awaitBodyRead(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            for (StackTraceElement frame : thread.getStackTrace()) {
                if ("org.apache.http.util.EntityUtils".equals(frame.getClassName())
                        && "toString".equals(frame.getMethodName())) return true;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return false;
    }

    public void testCompleteRequestDeadlineBoundsContinuouslyStreamingResponse() throws Exception {
        long start = System.nanoTime();
        Future<Exception> result = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                HttpUtils.get(url);
                throw new AssertionError("An unfinished chunked response must reach the total request deadline");
            } catch (Exception exception) {
                return exception;
            }
        });
        try {
            assertTrue(firstChunk.await(5, TimeUnit.SECONDS));
            Exception failure = result.get(36, TimeUnit.SECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("The full request deadline is 30 seconds, even while bytes keep arriving",
                    elapsed >= 29_000 && elapsed < 36_000);
            assertFalse("Deadline is a network failure rather than user cancellation", failure instanceof ProcessCanceledException);
            assertFalse(failure.toString().contains("FAKE_TEST_SECRET"));
            assertNull("Transport exceptions must not preserve a sensitive cause", failure.getCause());
        } finally {
            finishResponse.countDown();
            result.cancel(true);
        }
    }
}
