package com.liuzhihang.doc.view.mcp;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class McpServerServiceTest {

    @Test
    public void startsOnceAndReleasesTheInjectedLoopbackTransport() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        McpServerService.McpTransportFactory transportFactory = () -> new McpServerService.McpTransport() {
            @Override
            public URI start() {
                starts.incrementAndGet();
                return URI.create("http://127.0.0.1:43123/mcp");
            }

            @Override
            public void close() {
                stops.incrementAndGet();
            }
        };
        McpServerService service = new McpServerService(transportFactory);

        assertFalse(service.isRunning());

        service.start();

        assertTrue(service.isRunning());
        assertEquals(URI.create("http://127.0.0.1:43123/mcp"), service.getEndpoint());

        service.start();

        assertEquals(1, starts.get());

        service.stop();

        assertFalse(service.isRunning());
        assertEquals(1, stops.get());
        assertNull(service.getEndpoint());
    }

    @Test
    public void concurrentStartsCreateAndCloseOnlyOneTransport() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        CountDownLatch enteredStart = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        McpServerService service = new McpServerService(() -> new McpServerService.McpTransport() {
            @Override
            public URI start() throws InterruptedException {
                starts.incrementAndGet();
                enteredStart.countDown();
                releaseStart.await();
                return URI.create("http://127.0.0.1:43124/mcp");
            }

            @Override
            public void close() {
                stops.incrementAndGet();
            }
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = callers.submit(service::start);
            assertTrue(enteredStart.await(5, TimeUnit.SECONDS));
            Future<?> second = callers.submit(service::start);
            releaseStart.countDown();
            first.get();
            second.get();
        } finally {
            callers.shutdownNow();
        }

        assertEquals(1, starts.get());
        assertTrue(service.isRunning());

        service.stop();

        assertEquals(1, stops.get());
        assertFalse(service.isRunning());
    }

    @Test
    public void remainsStoppedWhenTransportDoesNotProvideAnEndpoint() {
        AtomicInteger stops = new AtomicInteger();
        McpServerService.McpTransportFactory transportFactory = () -> new McpServerService.McpTransport() {
            @Override
            public URI start() {
                return null;
            }

            @Override
            public void close() {
                stops.incrementAndGet();
            }
        };
        McpServerService service = new McpServerService(transportFactory);

        service.start();

        assertFalse(service.isRunning());
        assertNull(service.getEndpoint());
        assertEquals(1, stops.get());
    }

    @Test
    public void realListenerValidatesMcpHttpRequestsAndReleasesItsPort() throws Exception {
        McpServerService service = new McpServerService();
        service.start();
        URI endpoint = service.getEndpoint();

        try {
            assertTrue(service.isRunning());
            assertEquals(405, request(endpoint, "GET", endpoint.getAuthority(), null, null, null, "").status);
            assertEquals(415, request(endpoint, "POST", endpoint.getAuthority(), null,
                    "application/json", null, initializeRequest()).status);
            assertEquals(406, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    null, null, initializeRequest()).status);
            assertEquals(406, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json", null, initializeRequest()).status);
            assertEquals(406, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "text/event-stream", null, initializeRequest()).status);
            assertEquals(406, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json;q=0, text/event-stream", null, initializeRequest()).status);
            assertEquals(406, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json, text/event-stream;q=0", null, initializeRequest()).status);
            assertEquals(406, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json;q=invalid, text/event-stream", null, initializeRequest()).status);
            assertEquals(200, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json;q=0.5, text/event-stream;q=1", null, initializeRequest()).status);
            assertEquals(200, requestWithAcceptHeaders(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    List.of("application/json", "text/event-stream"), null, initializeRequest()).status);
            assertEquals(400, request(endpoint, "POST", "example.test:" + endpoint.getPort(), "application/json",
                    "application/json, text/event-stream", null, initializeRequest()).status);
            assertEquals(403, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json, text/event-stream", "http://example.test:" + endpoint.getPort(), initializeRequest()).status);
            assertEquals(400, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json, text/event-stream", null, "not json").status);
            assertEquals(400, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json, text/event-stream", null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}").status);
            assertEquals(200, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json, text/event-stream", "http://127.0.0.1:" + endpoint.getPort(), initializeRequest()).status);
            assertEquals(202, request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json, text/event-stream", null, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").status);
            assertEquals("text/plain; charset=utf-8", request(endpoint, "POST", endpoint.getAuthority(), "application/json",
                    "application/json", null, initializeRequest()).contentType);
        } finally {
            service.stop();
        }

        assertFalse(service.isRunning());
        try (ServerSocket socket = new ServerSocket(endpoint.getPort(), 1, InetAddress.getByName("127.0.0.1"))) {
            assertEquals(endpoint.getPort(), socket.getLocalPort());
        }
    }

    private static String initializeRequest() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
    }

    private static HttpResponse request(URI endpoint, String method, String host, String contentType,
                                        String accept, String origin, String body) throws IOException {
        return requestWithAcceptHeaders(endpoint, method, host, contentType,
                accept == null ? List.of() : List.of(accept), origin, body);
    }

    private static HttpResponse requestWithAcceptHeaders(URI endpoint, String method, String host, String contentType,
                                                         List<String> acceptHeaders, String origin, String body) throws IOException {
        List<String> headers = new ArrayList<>();
        headers.add("Host: " + host);
        headers.add("Connection: close");
        if (contentType != null) {
            headers.add("Content-Type: " + contentType);
        }
        for (String accept : acceptHeaders) {
            headers.add("Accept: " + accept);
        }
        if (origin != null) {
            headers.add("Origin: " + origin);
        }

        byte[] requestBody = body.getBytes(StandardCharsets.UTF_8);
        headers.add("Content-Length: " + requestBody.length);
        try (Socket socket = new Socket("127.0.0.1", endpoint.getPort());
             OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(method + " /mcp HTTP/1.1\r\n");
            for (String header : headers) {
                writer.write(header + "\r\n");
            }
            writer.write("\r\n");
            writer.write(body);
            writer.flush();

            String statusLine = reader.readLine();
            String responseContentType = null;
            String header;
            while (!(header = reader.readLine()).isEmpty()) {
                if (header.regionMatches(true, 0, "Content-Type:", 0, "Content-Type:".length())) {
                    responseContentType = header.substring("Content-Type:".length()).trim();
                }
            }
            return new HttpResponse(Integer.parseInt(statusLine.split(" ")[1]), responseContentType);
        }
    }

    private static final class HttpResponse {
        private final int status;
        private final String contentType;

        private HttpResponse(int status, String contentType) {
            this.status = status;
            this.contentType = contentType;
        }
    }
}
