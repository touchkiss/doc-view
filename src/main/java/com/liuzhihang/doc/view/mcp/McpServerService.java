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
        this(SdkMcpTransport::new);
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

        private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        private HttpServer httpServer;
        private ExecutorService executor;
        private McpStatelessServerHandler handler;
        private McpStatelessSyncServer mcpServer;

        @Override
        public URI start() throws IOException {
            mcpServer = McpServer.sync(this)
                    .serverInfo("doc-view", "1.4.99")
                    .jsonMapper(jsonMapper)
                    .toolCall(McpSchema.Tool.builder()
                                    .name("doc_view_status")
                                    .description("Reports that the Doc View MCP server is available.")
                                    .inputSchema(Map.of("type", "object"))
                                    .build(),
                            (context, request) -> McpSchema.CallToolResult.builder()
                                    .addTextContent("Doc View MCP server is running.")
                                    .build())
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
                    send(exchange, 404, "Not found.");
                    return;
                }
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    send(exchange, 405, "Only POST is supported.");
                    return;
                }
                if (handler == null) {
                    send(exchange, 503, "MCP server is starting.");
                    return;
                }

                Map<?, ?> message = jsonMapper.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
                if (message.containsKey("id")) {
                    McpSchema.JSONRPCRequest request = jsonMapper.convertValue(message,
                            McpSchema.JSONRPCRequest.class);
                    McpSchema.JSONRPCResponse response = handler
                            .handleRequest(McpTransportContext.EMPTY, request)
                            .block();
                    send(exchange, 200, jsonMapper.writeValueAsBytes(response));
                    return;
                }

                McpSchema.JSONRPCNotification notification = jsonMapper.convertValue(message,
                        McpSchema.JSONRPCNotification.class);
                handler.handleNotification(McpTransportContext.EMPTY, notification).block();
                exchange.sendResponseHeaders(202, -1);
            } catch (IllegalArgumentException exception) {
                send(exchange, 400, "Invalid JSON-RPC message.");
            } catch (Exception exception) {
                LOG.warn("Unable to process MCP request", exception);
                send(exchange, 500, "Unable to process MCP request.");
            }
        }

        private void send(HttpExchange exchange, int status, String body) throws IOException {
            send(exchange, status, body.getBytes(StandardCharsets.UTF_8));
        }

        private void send(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", APPLICATION_JSON);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
