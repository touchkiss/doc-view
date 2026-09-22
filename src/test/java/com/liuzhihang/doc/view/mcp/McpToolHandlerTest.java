package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.dto.DocView;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class McpToolHandlerTest {

    @Test
    public void advertisesExactlyTheRequiredTypedInputFields() {
        McpSchema.Tool tool = McpToolHandler.tool();

        assertEquals("upload_yapi_api_doc", tool.name());
        assertEquals(List.of("projectPath", "reference"), tool.inputSchema().get("required"));
        assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) tool.inputSchema().get("properties"))
                .get("projectPath")).get("type"));
        assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) tool.inputSchema().get("properties"))
                .get("reference")).get("type"));
    }

    @Test
    public void returnsStructuredInvalidArgumentForMissingArguments() {
        McpResult.UploadBatch result = handler().handle(Map.of());

        assertTrue(result.failed().stream().anyMatch(item -> "INVALID_ARGUMENT".equals(item.errorCode())));
        assertTrue(result.created().isEmpty());
        assertTrue(result.updated().isEmpty());
        assertTrue(result.skipped().isEmpty());
    }

    @Test
    public void returnsStructuredProjectNotOpenFailure() {
        McpToolHandler handler = handler(projectPath -> {
            throw new McpException(McpException.Code.PROJECT_NOT_OPEN, "Project is not open");
        }, (project, reference) -> List.of(), (project, targets) -> generated(), (project, documents) -> List.of());

        McpResult.UploadBatch result = handler.handle(arguments("/missing", "sample.OrderController"));

        assertEquals("PROJECT_NOT_OPEN", result.failed().get(0).errorCode());
    }

    @Test
    public void expandsAClassReferenceBeforeUploadingEveryResolvedMethod() {
        List<McpToolHandler.UploadTarget> capturedTargets = new ArrayList<>();
        McpToolHandler handler = handler(projectPath -> null,
                (project, reference) -> List.of(target("sample.OrderController#create"), target("sample.OrderController#update")),
                (project, targets) -> {
                    capturedTargets.addAll(targets);
                    return generated(documents(2));
                },
                (project, documents) -> List.of(McpResult.Item.created("GET /orders", "http://yapi/1"),
                        McpResult.Item.updated("PUT /orders/1", "http://yapi/2")));

        McpResult.UploadBatch result = handler.handle(arguments("/workspace", "sample.OrderController"));

        assertEquals(List.of("sample.OrderController#create", "sample.OrderController#update"), capturedTargets.stream()
                .map(McpToolHandler.UploadTarget::reference).toList());
        assertEquals(1, result.created().size());
        assertEquals(1, result.updated().size());
        assertTrue(result.failed().isEmpty());
    }

    @Test
    public void uploadsOnlyTheRequestedMethodReference() {
        McpToolHandler handler = handler(projectPath -> null,
                (project, reference) -> List.of(target("sample.OrderController#find")),
                (project, targets) -> generated(documents(1)),
                (project, documents) -> List.of(McpResult.Item.created("GET /orders/1", "http://yapi/1")));

        McpResult.UploadBatch result = handler.handle(arguments("/workspace", "sample.OrderController#find"));

        assertEquals("sample.OrderController#find", result.reference());
        assertEquals(1, result.created().size());
        assertTrue(result.updated().isEmpty());
    }

    @Test
    public void preservesDocumentGenerationFailuresAlongsideSuccessfulUploads() {
        McpToolHandler handler = handler(projectPath -> null,
                (project, reference) -> List.of(target("sample.OrderController#healthy"), target("sample.OrderController#broken")),
                (project, targets) -> new McpToolHandler.GeneratedDocs(List.of(documents(1)), List.of(),
                        List.of(McpResult.Item.failed("sample.OrderController#broken", "DOC_GENERATION_FAILED", "bad annotation"))),
                (project, documents) -> List.of(McpResult.Item.created("GET /healthy", "http://yapi/1")));

        McpResult.UploadBatch result = handler.handle(arguments("/workspace", "sample.OrderController"));

        assertEquals(1, result.created().size());
        assertEquals("DOC_GENERATION_FAILED", result.failed().get(0).errorCode());
    }

    @Test
    public void neverSerializesYApiTokensInStructuredBusinessFailures() {
        McpToolHandler handler = handler(projectPath -> {
            throw new McpException(McpException.Code.YAPI_NOT_CONFIGURED, "token=secret-yapi-token");
        }, (project, reference) -> List.of(), (project, targets) -> generated(), (project, documents) -> List.of());

        McpResult.UploadBatch result = handler.handle(arguments("/workspace", "sample.OrderController"));

        assertFalse(result.structuredContent().toString().contains("secret-yapi-token"));
        assertEquals("YAPI_NOT_CONFIGURED", result.failed().get(0).errorCode());
    }

    @Test
    public void returnsBusinessFailuresAsStructuredToolContent() {
        McpToolHandler handler = handler();

        McpSchema.CallToolResult result = handler.handle(new McpSchema.CallToolRequest(
                McpToolHandler.TOOL_NAME, Map.of(), null));

        assertTrue(result.isError());
        assertTrue(result.structuredContent() instanceof Map<?, ?>);
        assertTrue(((Map<?, ?>) result.structuredContent()).containsKey("failed"));
    }

    private static McpToolHandler handler() {
        return handler(projectPath -> null, (project, reference) -> List.of(),
                (project, targets) -> generated(), (project, documents) -> List.of());
    }

    private static McpToolHandler handler(McpToolHandler.ProjectPathResolver projectResolver,
                                          McpToolHandler.UploadReferenceResolver referenceResolver,
                                          McpToolHandler.DocumentGenerator documentGenerator,
                                          McpToolHandler.UploadExecutor uploadExecutor) {
        return new McpToolHandler(projectResolver, referenceResolver, documentGenerator, uploadExecutor);
    }

    private static McpToolHandler.UploadTarget target(String reference) {
        return new McpToolHandler.UploadTarget(reference, null, null);
    }

    private static McpToolHandler.GeneratedDocs generated(DocView... documents) {
        return new McpToolHandler.GeneratedDocs(List.of(documents), List.of(), List.of());
    }

    private static DocView[] documents(int count) {
        DocView[] documents = new DocView[count];
        for (int index = 0; index < count; index++) {
            documents[index] = new DocView();
        }
        return documents;
    }

    private static Map<String, Object> arguments(String projectPath, String reference) {
        return Map.of("projectPath", projectPath, "reference", reference);
    }
}
