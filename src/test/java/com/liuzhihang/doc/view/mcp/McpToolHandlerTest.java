package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.dto.DocView;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public void mapsUnexpectedFailuresToTheirOwningStage() {
        McpToolHandler projectFailure = handler(projectPath -> {
            throw new IllegalStateException("project unavailable");
        }, (project, reference) -> List.of(), (project, targets) -> generated(), (project, documents) -> List.of());
        McpToolHandler referenceFailure = handler(projectPath -> null, (project, reference) -> {
            throw new IllegalStateException("reference unavailable");
        }, (project, targets) -> generated(), (project, documents) -> List.of());
        McpToolHandler documentFailure = handler(projectPath -> null, (project, reference) -> List.of(),
                (project, targets) -> {
                    throw new IllegalStateException("document unavailable");
                }, (project, documents) -> List.of());
        McpToolHandler uploadFailure = handler(projectPath -> null, (project, reference) -> List.of(),
                (project, targets) -> generated(documents(1)), (project, documents) -> {
                    throw new IllegalStateException("upload unavailable");
                });

        assertEquals("PROJECT_NOT_OPEN", errorCode(projectFailure));
        assertEquals("REFERENCE_NOT_FOUND", errorCode(referenceFailure));
        assertEquals("DOC_GENERATION_FAILED", errorCode(documentFailure));
        assertEquals("YAPI_REQUEST_FAILED", errorCode(uploadFailure));
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
    public void redactsKnownTokensAndCommonAuthorizationCredentialsFromStructuredResults() {
        String configuredToken = "configured-secret-token";
        String message = "configured=" + configuredToken
                + ", token=equals-secret, token: colon-secret, \"token\":\"json-secret\""
                + ", Authorization: Bearer bearer-secret, X-Api-Key: x-api-secret, API-Key: api-secret";

        McpResult.UploadBatch result = McpResult.UploadBatch.failure("/workspace", "sample.Controller",
                McpException.Code.YAPI_REQUEST_FAILED, message, configuredToken);
        String structuredResult = result.structuredContent().toString();

        for (String secret : List.of(configuredToken, "equals-secret", "colon-secret", "json-secret",
                "bearer-secret", "x-api-secret", "api-secret")) {
            assertFalse("structured result leaked " + secret, structuredResult.contains(secret));
        }
    }

    @Test
    public void materializesReferenceMetadataInsideTheProvidedReadAction() {
        AtomicBoolean inReadAction = new AtomicBoolean();
        PsiClass psiClass = psiClassNamed("sample.OrderController", inReadAction);
        PsiMethod psiMethod = psiMethodNamed("find", inReadAction);

        McpToolHandler.UploadTarget target = McpToolHandler.materializeUploadTarget(psiClass, psiMethod,
                computation -> {
                    inReadAction.set(true);
                    try {
                        return computation.get();
                    } finally {
                        inReadAction.set(false);
                    }
                });

        assertEquals("sample.OrderController#find", target.reference());
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

    private static String errorCode(McpToolHandler handler) {
        return handler.handle(arguments("/workspace", "sample.Controller")).failed().get(0).errorCode();
    }

    private static PsiClass psiClassNamed(String qualifiedName, AtomicBoolean inReadAction) {
        return (PsiClass) java.lang.reflect.Proxy.newProxyInstance(McpToolHandlerTest.class.getClassLoader(),
                new Class<?>[]{PsiClass.class}, (proxy, method, arguments) -> {
                    if ("getQualifiedName".equals(method.getName())) {
                        assertTrue("PsiClass metadata must be read in a read action", inReadAction.get());
                        return qualifiedName;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PsiMethod psiMethodNamed(String name, AtomicBoolean inReadAction) {
        return (PsiMethod) java.lang.reflect.Proxy.newProxyInstance(McpToolHandlerTest.class.getClassLoader(),
                new Class<?>[]{PsiMethod.class}, (proxy, method, arguments) -> {
                    if ("getName".equals(method.getName())) {
                        assertTrue("PsiMethod metadata must be read in a read action", inReadAction.get());
                        return name;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
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
