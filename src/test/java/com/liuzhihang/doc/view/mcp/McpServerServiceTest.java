package com.liuzhihang.doc.view.mcp;

import org.junit.Test;

import java.net.URI;
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
}
