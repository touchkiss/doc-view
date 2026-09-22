package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImpl;
import com.liuzhihang.doc.view.service.DocViewService;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Handles the MCP tool that uploads generated API documentation to YApi. */
public final class McpToolHandler {

    public static final String TOOL_NAME = "upload_yapi_api_doc";

    private final ProjectPathResolver projectResolver;
    private final UploadReferenceResolver referenceResolver;
    private final DocumentGenerator documentGenerator;
    private final UploadExecutor uploadExecutor;

    public McpToolHandler() {
        this(new McpProjectResolver()::resolve,
                (project, reference) -> new ReferenceResolver().resolveForUpload(project, reference).stream()
                        .map(resolved -> new UploadTarget(reference(resolved), resolved.getPsiClass(),
                                resolved.getPsiMethod().orElse(null)))
                        .toList(),
                McpToolHandler::generateDocuments,
                McpToolHandler::uploadDocuments);
    }

    McpToolHandler(ProjectPathResolver projectResolver, UploadReferenceResolver referenceResolver,
                   DocumentGenerator documentGenerator, UploadExecutor uploadExecutor) {
        this.projectResolver = Objects.requireNonNull(projectResolver, "projectResolver");
        this.referenceResolver = Objects.requireNonNull(referenceResolver, "referenceResolver");
        this.documentGenerator = Objects.requireNonNull(documentGenerator, "documentGenerator");
        this.uploadExecutor = Objects.requireNonNull(uploadExecutor, "uploadExecutor");
    }

    public static McpSchema.Tool tool() {
        Map<String, Object> stringField = Map.of("type", "string");
        return McpSchema.Tool.builder(TOOL_NAME, Map.of(
                        "type", "object",
                        "properties", Map.of("projectPath", stringField, "reference", stringField),
                        "required", List.of("projectPath", "reference"),
                        "additionalProperties", false))
                .description("Uploads Java controller API documentation to the configured YApi project.")
                .build();
    }

    public McpSchema.CallToolResult handle(McpSchema.CallToolRequest request) {
        McpResult.UploadBatch result = handle(request == null ? null : request.arguments());
        return McpSchema.CallToolResult.builder()
                .addTextContent(result.isError() ? "YApi upload completed with failures." : "YApi upload completed.")
                .structuredContent(result.structuredContent())
                .isError(result.isError())
                .build();
    }

    McpResult.UploadBatch handle(Map<String, Object> arguments) {
        String projectPath = stringArgument(arguments, "projectPath");
        String reference = stringArgument(arguments, "reference");
        if (projectPath == null || reference == null) {
            return McpResult.UploadBatch.failure(projectPath, reference, McpException.Code.INVALID_ARGUMENT,
                    "projectPath and reference must be non-blank strings");
        }

        try {
            Project project = projectResolver.resolve(projectPath);
            List<UploadTarget> targets = referenceResolver.resolve(project, reference);
            GeneratedDocs generated = documentGenerator.generate(project, targets);
            List<McpResult.Item> uploaded = generated.documents().isEmpty()
                    ? List.of() : uploadExecutor.upload(project, generated.documents());
            return batch(projectPath, reference, generated, uploaded);
        } catch (McpException exception) {
            return McpResult.UploadBatch.failure(projectPath, reference, exception.getCode(), exception.getMessage());
        } catch (Exception exception) {
            return McpResult.UploadBatch.failure(projectPath, reference, McpException.Code.YAPI_REQUEST_FAILED,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private static McpResult.UploadBatch batch(String projectPath, String reference, GeneratedDocs generated,
                                               List<McpResult.Item> uploaded) {
        List<McpResult.Item> created = new ArrayList<>();
        List<McpResult.Item> updated = new ArrayList<>();
        List<McpResult.Item> skipped = new ArrayList<>(generated.skipped());
        List<McpResult.Item> failed = new ArrayList<>(generated.failed());
        for (McpResult.Item item : uploaded) {
            switch (item.status()) {
                case CREATED -> created.add(item);
                case UPDATED -> updated.add(item);
                case SKIPPED -> skipped.add(item);
                case FAILED -> failed.add(item);
            }
        }
        return McpResult.UploadBatch.of(projectPath, reference, created, updated, skipped, failed);
    }

    private static GeneratedDocs generateDocuments(Project project, List<UploadTarget> targets) {
        List<DocView> documents = new ArrayList<>();
        List<McpResult.Item> skipped = new ArrayList<>();
        List<McpResult.Item> failed = new ArrayList<>();
        for (UploadTarget target : targets) {
            try {
                List<DocView> generated = ReadAction.compute(() -> DocViewService
                        .getInstance(project, target.psiClass())
                        .buildDoc(target.psiClass(), target.psiMethod()));
                if (generated.isEmpty()) {
                    skipped.add(McpResult.Item.skipped(target.reference(), "No documentation was generated."));
                } else {
                    documents.addAll(generated);
                }
            } catch (Exception exception) {
                failed.add(McpResult.Item.failed(target.reference(), McpException.Code.DOC_GENERATION_FAILED.name(),
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
        }
        return new GeneratedDocs(documents, skipped, failed);
    }

    private static List<McpResult.Item> uploadDocuments(Project project, List<DocView> documents) {
        YApiFacadeServiceImpl facade = ApplicationManager.getApplication().getService(YApiFacadeServiceImpl.class);
        List<YApiUploadOrchestrator.UploadItemResult> results = new YApiUploadOrchestrator(facade).upload(project, documents);
        return results.stream().map(McpToolHandler::resultItem).toList();
    }

    private static McpResult.Item resultItem(YApiUploadOrchestrator.UploadItemResult result) {
        if ("updated".equals(result.getStatus())) {
            return McpResult.Item.updated(result.getReference(), result.getYapiUrl());
        }
        if ("created".equals(result.getStatus())) {
            return McpResult.Item.created(result.getReference(), result.getYapiUrl());
        }
        return McpResult.Item.failed(result.getReference(), result.getErrorCode(), result.getMessage());
    }

    private static String reference(ReferenceResolver.ResolvedReference resolved) {
        String className = resolved.getPsiClass().getQualifiedName();
        return resolved.getPsiMethod().map(method -> className + "#" + method.getName()).orElse(className);
    }

    private static String stringArgument(Map<String, Object> arguments, String name) {
        if (arguments == null || !(arguments.get(name) instanceof String value) || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @FunctionalInterface
    interface ProjectPathResolver {
        Project resolve(String projectPath);
    }

    @FunctionalInterface
    interface UploadReferenceResolver {
        List<UploadTarget> resolve(Project project, String reference);
    }

    @FunctionalInterface
    interface DocumentGenerator {
        GeneratedDocs generate(Project project, List<UploadTarget> targets);
    }

    @FunctionalInterface
    interface UploadExecutor {
        List<McpResult.Item> upload(Project project, List<DocView> documents);
    }

    record UploadTarget(String reference, PsiClass psiClass, PsiMethod psiMethod) {
    }

    record GeneratedDocs(List<DocView> documents, List<McpResult.Item> skipped, List<McpResult.Item> failed) {
        GeneratedDocs {
            documents = List.copyOf(documents);
            skipped = List.copyOf(skipped);
            failed = List.copyOf(failed);
        }
    }
}
