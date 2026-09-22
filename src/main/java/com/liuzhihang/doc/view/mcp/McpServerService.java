package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the local, loopback-only MCP Streamable HTTP server lifecycle. */
public final class McpServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerService.class);

    private final McpTransportFactory transportFactory;
    private McpTransport transport;
    private URI endpoint;

    public McpServerService() {
        this(() -> new SdkMcpTransport(new McpToolHandler()));
    }

    McpServerService(McpTransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }

    public synchronized void start() {
        if (transport != null) {
            return;
        }

        McpTransport candidate = null;
        try {
            candidate = transportFactory.create();
            URI candidateEndpoint = validateEndpoint(candidate.start());
            transport = candidate;
            endpoint = candidateEndpoint;
            LOG.info("Local MCP endpoint: " + endpoint);
        } catch (Exception exception) {
            closeQuietly(candidate);
            LOG.warn("Unable to start the local MCP server", exception);
        }
    }

    public synchronized void stop() {
        McpTransport current = transport;
        transport = null;
        endpoint = null;
        closeQuietly(current);
    }

    public synchronized URI getEndpoint() {
        return endpoint;
    }

    public synchronized boolean isRunning() {
        return transport != null;
    }

    @Override
    public void dispose() {
        stop();
    }

    private static void closeQuietly(McpTransport candidate) {
        if (candidate == null) {
            return;
        }
        try {
            candidate.close();
        } catch (Exception exception) {
            LOG.warn("Unable to stop the local MCP server", exception);
        }
    }

    private static URI validateEndpoint(URI candidateEndpoint) {
        Objects.requireNonNull(candidateEndpoint, "MCP transport did not provide an endpoint");
        if (!"http".equals(candidateEndpoint.getScheme())
                || !"127.0.0.1".equals(candidateEndpoint.getHost())
                || candidateEndpoint.getPort() < 1
                || !SdkMcpTransport.MCP_PATH.equals(candidateEndpoint.getPath())) {
            throw new IllegalArgumentException("MCP transport must provide a loopback /mcp endpoint");
        }
        return candidateEndpoint;
    }

    @FunctionalInterface
    interface McpTransportFactory {
        McpTransport create() throws Exception;
    }

    interface McpTransport extends AutoCloseable {
        URI start() throws Exception;

        @Override
        void close() throws Exception;
    }

    private static final class SdkMcpTransport implements McpTransport, McpStatelessServerTransport {

        private static final String MCP_PATH = "/mcp";
        private static final String APPLICATION_JSON = "application/json";
        private static final String TEXT_EVENT_STREAM = "text/event-stream";

        private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        private final McpToolHandler toolHandler;
        private HttpServer httpServer;
        private ExecutorService executor;
        private McpStatelessServerHandler handler;
        private McpStatelessSyncServer mcpServer;

        private SdkMcpTransport(McpToolHandler toolHandler) {
            this.toolHandler = toolHandler;
        }

        @Override
        public URI start() throws IOException {
            mcpServer = McpServer.sync(this)
                    .serverInfo("doc-view", "1.4.99")
                    .jsonMapper(jsonMapper)
                    // The schema remains discoverable; the handler returns structured business input errors.
                    .validateToolInputs(false)
                    .toolCall(McpToolHandler.tool(), (context, request) -> toolHandler.handle(request))
                    .build();

            httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "doc-view-mcp-server");
                thread.setDaemon(true);
                return thread;
            });
            httpServer.setExecutor(executor);
            httpServer.createContext(MCP_PATH, this::handleExchange);
            httpServer.start();
            return URI.create("http://127.0.0.1:" + httpServer.getAddress().getPort() + MCP_PATH);
        }

        @Override
        public void setMcpHandler(McpStatelessServerHandler handler) {
            this.handler = handler;
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> {
                if (httpServer != null) {
                    httpServer.stop(0);
                    httpServer = null;
                }
                if (executor != null) {
                    executor.shutdownNow();
                    executor = null;
                }
            });
        }

        @Override
        public void close() {
            if (mcpServer != null) {
                mcpServer.closeGracefully();
                mcpServer = null;
            } else {
                closeGracefully().block();
            }
            handler = null;
        }

        private void handleExchange(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (!MCP_PATH.equals(exchange.getRequestURI().getPath())) {
                    sendText(exchange, 404, "Not found.");
                    return;
                }
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    sendText(exchange, 405, "Only POST is supported.");
                    return;
                }
                int headerError = validateRequestHeaders(exchange);
                if (headerError != 0) {
                    sendText(exchange, headerError, "Invalid MCP HTTP headers.");
                    return;
                }
                if (handler == null) {
                    sendText(exchange, 503, "MCP server is starting.");
                    return;
                }

                Map<?, ?> message;
                JsonRpcMessageType messageType;
                try {
                    message = jsonMapper.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
                    messageType = classifyMessage(message);
                } catch (Exception exception) {
                    sendText(exchange, 400, "Invalid JSON-RPC message.");
                    return;
                }

                if (messageType == JsonRpcMessageType.REQUEST) {
                    McpSchema.JSONRPCRequest request;
                    try {
                        request = jsonMapper.convertValue(message, McpSchema.JSONRPCRequest.class);
                    } catch (Exception exception) {
                        sendText(exchange, 400, "Invalid JSON-RPC message.");
                        return;
                    }
                    McpSchema.JSONRPCResponse response = handler
                            .handleRequest(McpTransportContext.EMPTY, request)
                            .block();
                    sendJson(exchange, 200, jsonMapper.writeValueAsBytes(response));
                    return;
                }

                if (messageType != JsonRpcMessageType.NOTIFICATION) {
                    sendText(exchange, 400, "Invalid JSON-RPC message.");
                    return;
                }

                McpSchema.JSONRPCNotification notification;
                try {
                    notification = jsonMapper.convertValue(message, McpSchema.JSONRPCNotification.class);
                } catch (Exception exception) {
                    sendText(exchange, 400, "Invalid JSON-RPC message.");
                    return;
                }
                handler.handleNotification(McpTransportContext.EMPTY, notification).block();
                exchange.sendResponseHeaders(202, -1);
            } catch (Exception exception) {
                LOG.warn("Unable to process MCP request", exception);
                sendText(exchange, 500, "Unable to process MCP request.");
            }
        }

        private int validateRequestHeaders(HttpExchange exchange) {
            if (!isApplicationJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                return 415;
            }
            if (!acceptsMcpResponses(exchange.getRequestHeaders().get("Accept"))) {
                return 406;
            }

            int port = httpServer.getAddress().getPort();
            List<String> hostHeaders = exchange.getRequestHeaders().get("Host");
            if (hostHeaders == null || hostHeaders.size() != 1 || !isLoopbackAuthority(hostHeaders.get(0), port)) {
                return 400;
            }

            String origin = exchange.getRequestHeaders().getFirst("Origin");
            return origin == null || isLoopbackOrigin(origin, port) ? 0 : 403;
        }

        private static boolean isApplicationJson(String contentType) {
            return contentType != null && APPLICATION_JSON.equalsIgnoreCase(mediaType(contentType));
        }

        private static boolean acceptsMcpResponses(List<String> acceptHeaders) {
            if (acceptHeaders == null || acceptHeaders.isEmpty()) {
                return false;
            }

            boolean acceptsJson = false;
            boolean acceptsEventStream = false;
            for (String acceptHeader : acceptHeaders) {
                if (acceptHeader == null || acceptHeader.isBlank()) {
                    return false;
                }
                for (String acceptedType : acceptHeader.split(",", -1)) {
                    AcceptMediaRange mediaRange = parseAcceptMediaRange(acceptedType);
                    if (mediaRange == null) {
                        return false;
                    }
                    if (APPLICATION_JSON.equalsIgnoreCase(mediaRange.type)) {
                        acceptsJson |= mediaRange.hasPositiveQuality;
                    } else if (TEXT_EVENT_STREAM.equalsIgnoreCase(mediaRange.type)) {
                        acceptsEventStream |= mediaRange.hasPositiveQuality;
                    }
                }
            }
            return acceptsJson && acceptsEventStream;
        }

        private static AcceptMediaRange parseAcceptMediaRange(String value) {
            String[] parts = value.split(";", -1);
            String type = parts[0].trim();
            if (!isMediaRange(type)) {
                return null;
            }

            boolean hasQuality = false;
            boolean positiveQuality = true;
            for (int index = 1; index < parts.length; index++) {
                String parameter = parts[index].trim();
                int separator = parameter.indexOf('=');
                if (separator < 1) {
                    return null;
                }
                String name = parameter.substring(0, separator).trim();
                String parameterValue = parameter.substring(separator + 1).trim();
                if ("q".equalsIgnoreCase(name)) {
                    if (hasQuality || !isQualityValue(parameterValue)) {
                        return null;
                    }
                    hasQuality = true;
                    positiveQuality = !isZeroQualityValue(parameterValue);
                }
            }
            return new AcceptMediaRange(type, positiveQuality);
        }

        private static boolean isMediaRange(String type) {
            int separator = type.indexOf('/');
            return separator > 0 && separator < type.length() - 1 && type.indexOf(' ') < 0;
        }

        private static boolean isQualityValue(String value) {
            return value.matches("(?:0(?:\\.\\d{0,3})?|1(?:\\.0{0,3})?)");
        }

        private static boolean isZeroQualityValue(String value) {
            return value.matches("0(?:\\.0{0,3})?");
        }

        private static String mediaType(String value) {
            int separator = value.indexOf(';');
            return (separator < 0 ? value : value.substring(0, separator)).trim();
        }

        private static boolean isLoopbackAuthority(String authority, int port) {
            try {
                URI uri = URI.create("http://" + authority);
                return uri.getUserInfo() == null
                        && uri.getPath().isEmpty()
                        && uri.getQuery() == null
                        && uri.getFragment() == null
                        && uri.getPort() == port
                        && isLoopbackHost(uri.getHost());
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static boolean isLoopbackOrigin(String origin, int port) {
            try {
                URI uri = URI.create(origin);
                return "http".equalsIgnoreCase(uri.getScheme())
                        && uri.getRawAuthority() != null
                        && uri.getUserInfo() == null
                        && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                        && uri.getQuery() == null
                        && uri.getFragment() == null
                        && uri.getPort() == port
                        && isLoopbackHost(uri.getHost());
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static boolean isLoopbackHost(String host) {
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "[::1]".equals(host);
        }

        private static JsonRpcMessageType classifyMessage(Map<?, ?> message) {
            if (!"2.0".equals(message.get("jsonrpc"))) {
                return JsonRpcMessageType.INVALID;
            }
            boolean hasResult = message.containsKey("result");
            boolean hasError = message.containsKey("error");
            Object method = message.get("method");
            if (method == null && (hasResult || hasError)) {
                return JsonRpcMessageType.RESPONSE;
            }
            if (hasResult || hasError) {
                return JsonRpcMessageType.INVALID;
            }
            if (!(method instanceof String) || ((String) method).trim().isEmpty()) {
                return JsonRpcMessageType.INVALID;
            }
            if (!message.containsKey("id")) {
                return JsonRpcMessageType.NOTIFICATION;
            }
            Object id = message.get("id");
            return id == null || id instanceof String || id instanceof Number
                    ? JsonRpcMessageType.REQUEST
                    : JsonRpcMessageType.INVALID;
        }

        private enum JsonRpcMessageType {
            REQUEST,
            NOTIFICATION,
            RESPONSE,
            INVALID
        }

        private void sendText(HttpExchange exchange, int status, String body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            sendBody(exchange, status, body.getBytes(StandardCharsets.UTF_8));
        }

        private void sendJson(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", APPLICATION_JSON);
            sendBody(exchange, status, body);
        }

        private void sendBody(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        private static final class AcceptMediaRange {
            private final String type;
            private final boolean hasPositiveQuality;

            private AcceptMediaRange(String type, boolean hasPositiveQuality) {
                this.type = type;
                this.hasPositiveQuality = hasPositiveQuality;
            }
        }
    }
}
