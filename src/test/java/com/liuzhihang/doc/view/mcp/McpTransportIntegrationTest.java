package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.dto.DocView;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class McpTransportIntegrationTest {

    @Test
    public void sdkClientInitializesAndReceivesTheOnlyToolAndStructuredResults() throws Exception {
        try (RunningServer server = RunningServer.start();
             McpSyncClient client = client(server.endpoint())) {
            McpSchema.InitializeResult initialized = client.initialize();

            assertNotNull(initialized);
            assertTrue(client.isInitialized());
            assertEquals(List.of(McpToolHandler.TOOL_NAME), client.listTools().tools().stream()
                    .map(McpSchema.Tool::name).toList());

            McpSchema.CallToolResult invalidArguments = call(client, Map.of());
            assertTrue(Boolean.TRUE.equals(invalidArguments.isError()));
            assertEquals("INVALID_ARGUMENT", errorCode(invalidArguments));

            McpSchema.CallToolResult projectNotOpen = call(client, Map.of(
                    "projectPath", "/not-open", "reference", "sample.OrderController"));
            assertTrue(Boolean.TRUE.equals(projectNotOpen.isError()));
            assertEquals("PROJECT_NOT_OPEN", errorCode(projectNotOpen));

            McpSchema.CallToolResult success = call(client, Map.of(
                    "projectPath", "/open", "reference", "sample.OrderController#list"));
            assertFalse(Boolean.TRUE.equals(success.isError()));
            assertEquals("sample.OrderController#list", createdReference(success));
            assertTrue(server.handledOffEdt());
        }
    }

    @Test
    public void concurrentSdkCallsRemainIndependentAndStopReleasesTheTransportExecutor() throws Exception {
        try (RunningServer server = RunningServer.start();
             McpSyncClient first = client(server.endpoint());
             McpSyncClient second = client(server.endpoint())) {
            first.initialize();
            second.initialize();
            ExecutorService callers = Executors.newFixedThreadPool(2);
            try {
                Future<McpSchema.CallToolResult> firstCall = callers.submit(() -> call(first, Map.of(
                        "projectPath", "/open", "reference", "sample.OrderController#first")));
                Future<McpSchema.CallToolResult> secondCall = callers.submit(() -> call(second, Map.of(
                        "projectPath", "/open", "reference", "sample.OrderController#second")));

                assertEquals("sample.OrderController#first", createdReference(firstCall.get()));
                assertEquals("sample.OrderController#second", createdReference(secondCall.get()));
                assertEquals(List.of(McpToolHandler.TOOL_NAME), first.listTools().tools().stream()
                        .map(McpSchema.Tool::name).toList());
            } finally {
                callers.shutdownNow();
            }

            URI endpoint = server.endpoint();
            ExecutorService executor = server.executor();
            server.stop();

            assertTrue(executor.isShutdown());
            assertFalse(server.service().isRunning());
            try {
                first.listTools();
                fail("a stopped MCP listener must be unreachable");
            } catch (RuntimeException expected) {
                assertNotNull(expected.getCause());
            }
        }
    }

    private static McpSchema.CallToolResult call(McpSyncClient client, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(McpToolHandler.TOOL_NAME, arguments, null));
    }

    private static String errorCode(McpSchema.CallToolResult result) {
        Map<?, ?> structured = (Map<?, ?>) result.structuredContent();
        Map<?, ?> failed = (Map<?, ?>) ((List<?>) structured.get("failed")).get(0);
        return (String) failed.get("errorCode");
    }

    private static String createdReference(McpSchema.CallToolResult result) {
        Map<?, ?> structured = (Map<?, ?>) result.structuredContent();
        Map<?, ?> created = (Map<?, ?>) ((List<?>) structured.get("created")).get(0);
        return (String) created.get("reference");
    }

    private static McpSyncClient client(URI endpoint) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                        endpoint.getScheme() + "://" + endpoint.getAuthority())
                .endpoint(endpoint.getPath())
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static final class RunningServer implements AutoCloseable {

        private final McpServerService service;
        private final Object transport;
        private final URI endpoint;
        private final AtomicBoolean handledOffEdt;

        private RunningServer(McpServerService service, Object transport, URI endpoint, AtomicBoolean handledOffEdt) {
            this.service = service;
            this.transport = transport;
            this.endpoint = endpoint;
            this.handledOffEdt = handledOffEdt;
        }

        private static RunningServer start() throws Exception {
            AtomicBoolean handledOffEdt = new AtomicBoolean();
            Project project = (Project) Proxy.newProxyInstance(McpTransportIntegrationTest.class.getClassLoader(),
                    new Class<?>[]{Project.class}, (proxy, method, arguments) -> null);
            McpToolHandler handler = new McpToolHandler(projectPath -> {
                if (!"/open".equals(projectPath)) {
                    throw new McpException(McpException.Code.PROJECT_NOT_OPEN, "Project is not open");
                }
                return project;
            }, (ignored, reference) -> List.of(new McpToolHandler.UploadTarget(reference, null, null)),
                    (ignored, targets) -> targets.stream().map(target -> {
                        DocView document = new DocView();
                        document.setName(target.reference());
                        return document;
                    }).collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                            documents -> new McpToolHandler.GeneratedDocs(documents, List.of(), List.of()))),
                    (ignored, documents) -> {
                        handledOffEdt.set(!SwingUtilities.isEventDispatchThread());
                        return documents.stream().map(document -> McpResult.Item.created(document.getName(),
                                "http://yapi.test/" + document.getName())).toList();
                    });
            Object transport = sdkTransport(handler);
            McpServerService service = new McpServerService(() -> (McpServerService.McpTransport) transport);
            service.start();
            return new RunningServer(service, transport, service.getEndpoint(), handledOffEdt);
        }

        private static Object sdkTransport(McpToolHandler handler) throws Exception {
            Class<?> type = Class.forName("com.liuzhihang.doc.view.mcp.McpServerService$SdkMcpTransport");
            Constructor<?> constructor = type.getDeclaredConstructor(McpToolHandler.class);
            constructor.setAccessible(true);
            return constructor.newInstance(handler);
        }

        private URI endpoint() {
            return endpoint;
        }

        private McpServerService service() {
            return service;
        }

        private boolean handledOffEdt() {
            return handledOffEdt.get();
        }

        private ExecutorService executor() throws Exception {
            Field field = transport.getClass().getDeclaredField("executor");
            field.setAccessible(true);
            return (ExecutorService) field.get(transport);
        }

        private void stop() {
            service.stop();
        }

        @Override
        public void close() {
            service.stop();
        }
    }
}
