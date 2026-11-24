package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.liuzhihang.doc.view.config.ApplicationSettings;

import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;

/** Owns the local, loopback-only MCP Streamable HTTP server lifecycle. */
public final class McpServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerService.class);

    private final McpTransportFactory transportFactory;
    private final Supplier<ApplicationSettings> settingsSupplier;
    private McpTransport transport;
    private URI endpoint;

    public McpServerService() {
        this(() -> new SdkMcpTransport(new McpToolHandler(), configuredPort()),
                ApplicationSettings::getInstance);
    }

    McpServerService(McpTransportFactory transportFactory) {
        this(transportFactory, null);
    }

    private McpServerService(McpTransportFactory transportFactory, Supplier<ApplicationSettings> settingsSupplier) {
        this.transportFactory = transportFactory;
        this.settingsSupplier = settingsSupplier;
    }

    public synchronized void start() {
        if (transport != null) {
            return;
        }

        if (settingsSupplier != null) {
            ApplicationSettings settings = settingsSupplier.get();
            if (settings != null && !Boolean.TRUE.equals(settings.getMcpServerEnabled())) {
                LOG.info("Local MCP server is disabled by settings");
                return;
            }
            if (settings != null && !isValidPort(settings.getMcpServerPort())) {
                LOG.warn("Unable to start the local MCP server: invalid port " + settings.getMcpServerPort());
                return;
            }
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

    public synchronized void restart() {
        stop();
        start();
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

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    private static int configuredPort() {
        ApplicationSettings settings = ApplicationSettings.getInstance();
        return settings == null ? ApplicationSettings.DEFAULT_MCP_SERVER_PORT : settings.getMcpServerPort();
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
}
