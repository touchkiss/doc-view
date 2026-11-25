package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImpl;
import com.liuzhihang.doc.view.integration.YApiUploadOrchestrator;
import com.liuzhihang.doc.view.service.DocViewService;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Handles the MCP tool that uploads generated API documentation to YApi. */
public final class McpToolHandler {
    private static final String PROJECT_PATH = "projectPath";
    private static final String REFERENCE = "reference";


    public static final String TOOL_NAME = "upload_yapi_api_doc";

    private final ProjectPathResolver projectResolver;
    private final UploadReferenceResolver referenceResolver;
    private final DocumentGenerator documentGenerator;
    private final UploadExecutor uploadExecutor;

    public McpToolHandler() {
        this(new McpProjectResolver()::resolve,
                McpToolHandler::resolveDefaultTargets,
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
                        "properties", Map.of(PROJECT_PATH, stringField, REFERENCE, stringField),
                        "required", List.of(PROJECT_PATH, REFERENCE),
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
        String projectPath = stringArgument(arguments, PROJECT_PATH);
        String reference = stringArgument(arguments, REFERENCE);
        if (projectPath == null || reference == null) {
            return McpResult.UploadBatch.failure(projectPath, reference, McpException.Code.INVALID_ARGUMENT,
                    "projectPath and reference must be non-blank strings");
        }

        final Project project;
        try {
            project = projectResolver.resolve(projectPath);
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception exception) {
            return failure(projectPath, reference, exception, McpException.Code.PROJECT_NOT_OPEN);
        }

        final List<UploadTarget> targets;
        try {
            targets = referenceResolver.resolve(project, reference);
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception exception) {
            return failure(projectPath, reference, exception, McpException.Code.REFERENCE_NOT_FOUND);
        }

        final GeneratedDocs generated;
        try {
            generated = documentGenerator.generate(project, targets);
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception exception) {
            return failure(projectPath, reference, exception, McpException.Code.DOC_GENERATION_FAILED);
        }

        final List<McpResult.Item> uploaded;
        try {
            uploaded = generated.documents().isEmpty()
                    ? List.of() : uploadExecutor.upload(project, generated.documents());
        } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception exception) {
            return failure(projectPath, reference, exception, McpException.Code.YAPI_REQUEST_FAILED);
        }
        return batch(projectPath, reference, generated, uploaded);
    }

    private static McpResult.UploadBatch failure(String projectPath, String reference, Exception exception,
                                                 McpException.Code defaultCode) {
        McpException.Code code = exception instanceof McpException mcpException
                ? mcpException.getCode() : defaultCode;
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return McpResult.UploadBatch.failure(projectPath, reference, code, message);
    }

    private static McpResult.UploadBatch batch(String projectPath, String reference, GeneratedDocs generated,
                                               /**
         * Uploads documents.
         * @param project current project
         * @param documents documents to upload
         * @return per-document results
         */
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
                List<DocView> generated = ReadAction.computeBlocking(() -> freezeForUpload(DocViewService
                        .getInstance(project, target.psiClass())
                        .buildDoc(target.psiClass(), target.psiMethod())));
                if (generated.isEmpty()) {
                    skipped.add(McpResult.Item.skipped(target.reference(), "No documentation was generated."));
                } else {
                    documents.addAll(generated);
                }
            } catch (ProcessCanceledException canceled) {
            throw canceled;
        } catch (Exception exception) {
                failed.add(McpResult.Item.failed(target.reference(), McpException.Code.DOC_GENERATION_FAILED.name(),
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
        }
        return new GeneratedDocs(documents, skipped, failed);
    }

    private static List<UploadTarget> resolveDefaultTargets(Project project, String reference) {
        return new ReferenceResolver().resolveForUpload(project, reference).stream()
                .map(resolved -> materializeUploadTarget(resolved.getPsiClass(),
                        resolved.getPsiMethod().orElse(null), computation -> ReadAction.computeBlocking(computation::get)))
                .toList();
    }

    static UploadTarget materializeUploadTarget(PsiClass psiClass, PsiMethod psiMethod,
                                                 UploadTargetReadAction readAction) {
        return readAction.compute(() -> {
            String className = psiClass.getQualifiedName();
            String reference = psiMethod == null ? className : className + "#" + psiMethod.getName();
            return new UploadTarget(reference, psiClass, psiMethod);
        });
    }

    private static List<DocView> freezeForUpload(List<DocView> documents) {
        for (DocView document : documents) {
            if (!"Dubbo".equals(document.getMethod()) || document.getPsiMethod() == null) {
                continue;
            }
            document.setPath("/Dubbo/" + document.getPsiMethod().getName());
            document.setMethod("POST");
            document.setPsiMethod(null);
        }
        return documents;
    }

    private static List<McpResult.Item> uploadDocuments(Project project, List<DocView> documents) {
        YApiFacadeServiceImpl facade = ApplicationManager.getApplication().getService(YApiFacadeServiceImpl.class);
        try {
            return new YApiUploadOrchestrator(facade).upload(project, documents).stream()
                    .map(McpToolHandler::resultItem).toList();
        } catch (YApiUploadOrchestrator.MissingConfigurationException exception) {
            throw new McpException(McpException.Code.YAPI_NOT_CONFIGURED, exception.getMessage());
        }
    }

    static McpResult.Item resultItem(YApiUploadOrchestrator.UploadItemResult result) {
        if ("updated".equals(result.getStatus())) {
            return new McpResult.Item(McpResult.Status.UPDATED, result.getReference(), result.getYapiUrl(), null, result.getMessage());
        }
        if ("created".equals(result.getStatus())) {
            return new McpResult.Item(McpResult.Status.CREATED, result.getReference(), result.getYapiUrl(), null, result.getMessage());
        }
        return McpResult.Item.failed(result.getReference(), result.getErrorCode(), result.getMessage());
    }

    private static String stringArgument(Map<String, Object> arguments, String name) {
        if (arguments == null || !(arguments.get(name) instanceof String value) || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @FunctionalInterface
    interface ProjectPathResolver {
        /**
         * Resolves an open project.
         * @param projectPath project path
         * @return open project
         */
        Project resolve(String projectPath);
    }

    @FunctionalInterface
    interface UploadReferenceResolver {
        /**
         * Resolves the requested controller or method.
         * @param project current project
         * @param reference class or method reference
         * @return resolved targets
         */
        List<UploadTarget> resolve(Project project, String reference);
    }

    @FunctionalInterface
    interface DocumentGenerator {
        /**
         * Generates documents and records skipped or failed targets.
         * @param project current project
         * @param targets resolved targets
         * @return generated documents and per-target errors
         */
        GeneratedDocs generate(Project project, List<UploadTarget> targets);
    }

    @FunctionalInterface
    interface UploadExecutor {
        List<McpResult.Item> upload(Project project, List<DocView> documents);
    }

    @FunctionalInterface
    interface UploadTargetReadAction {
        /**
         * Captures an upload target inside a read action.
         * @param computation target computation
         * @return captured target
         */
        UploadTarget compute(Supplier<UploadTarget> computation);
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
