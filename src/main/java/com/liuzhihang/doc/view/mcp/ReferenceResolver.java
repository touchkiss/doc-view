package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.liuzhihang.doc.view.exception.DocViewException;
import com.liuzhihang.doc.view.service.DocViewService;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves fully-qualified Java class and method references from an already-open project. */
public final class ReferenceResolver {

    private final ClassLookup classLookup;
    private final DocViewServiceProvider docViewServiceProvider;
    private final ReadActionComputer readActionComputer;

    public ReferenceResolver() {
        this((project, fqcn) -> JavaPsiFacade.getInstance(project)
                        .findClass(fqcn, GlobalSearchScope.projectScope(project)),
                (project, psiClass) -> DocViewService.getInstance(project, psiClass),
                ReadAction::compute);
    }

    ReferenceResolver(ClassLookup classLookup, DocViewServiceProvider docViewServiceProvider,
                      ReadActionComputer readActionComputer) {
        this.classLookup = Objects.requireNonNull(classLookup, "classLookup");
        this.docViewServiceProvider = Objects.requireNonNull(docViewServiceProvider, "docViewServiceProvider");
        this.readActionComputer = Objects.requireNonNull(readActionComputer, "readActionComputer");
    }

    public ResolvedReference resolve(Project project, String reference) {
        return readActionComputer.compute(() -> resolveInReadAction(project, reference));
    }

    public List<ResolvedReference> resolveForUpload(Project project, String reference) {
        return readActionComputer.compute(() -> resolveForUploadInReadAction(project, reference));
    }

    private ResolvedReference resolveInReadAction(Project project, String reference) {
        ParsedReference parsedReference = parse(reference);
        PsiClass psiClass = findClass(project, parsedReference.className());
        if (parsedReference.methodName() == null) {
            requireSupportedMethods(project, psiClass);
            return new ResolvedReference(psiClass, null);
        }

        PsiMethod method = findSingleMethod(psiClass, parsedReference.methodName());
        requireSupportedMethod(project, psiClass, method);
        return new ResolvedReference(psiClass, method);
    }

    private List<ResolvedReference> resolveForUploadInReadAction(Project project, String reference) {
        ParsedReference parsedReference = parse(reference);
        PsiClass psiClass = findClass(project, parsedReference.className());
        if (parsedReference.methodName() != null) {
            PsiMethod method = findSingleMethod(psiClass, parsedReference.methodName());
            requireSupportedMethod(project, psiClass, method);
            return List.of(new ResolvedReference(psiClass, method));
        }

        return requireSupportedMethods(project, psiClass).stream()
                .map(method -> new ResolvedReference(psiClass, method))
                .toList();
    }

    private PsiClass findClass(Project project, String className) {
        PsiClass psiClass = classLookup.findClass(project, className);
        if (psiClass == null) {
            throw new McpException(McpException.Code.REFERENCE_NOT_FOUND,
                    "Java class was not found: " + className);
        }
        return psiClass;
    }

    private static PsiMethod findSingleMethod(PsiClass psiClass, String methodName) {
        List<PsiMethod> methods = Arrays.stream(psiClass.getMethods())
                .filter(method -> methodName.equals(method.getName()))
                .toList();
        if (methods.isEmpty()) {
            throw new McpException(McpException.Code.REFERENCE_NOT_FOUND,
                    "Java method was not found: " + methodName);
        }
        if (methods.size() > 1) {
            throw new McpException(McpException.Code.REFERENCE_AMBIGUOUS,
                    "Java method reference is overloaded: " + methodName);
        }
        return methods.get(0);
    }

    private List<PsiMethod> requireSupportedMethods(Project project, PsiClass psiClass) {
        DocViewService service = serviceFor(project, psiClass);
        List<PsiMethod> methods = Arrays.stream(psiClass.getMethods())
                .filter(service::checkMethod)
                .toList();
        if (methods.isEmpty()) {
            throw unsupported(psiClass);
        }
        return methods;
    }

    private void requireSupportedMethod(Project project, PsiClass psiClass, PsiMethod method) {
        if (!serviceFor(project, psiClass).checkMethod(method)) {
            throw unsupported(psiClass);
        }
    }

    private DocViewService serviceFor(Project project, PsiClass psiClass) {
        try {
            return docViewServiceProvider.get(project, psiClass);
        } catch (DocViewException exception) {
            throw unsupported(psiClass);
        }
    }

    private static McpException unsupported(PsiClass psiClass) {
        return new McpException(McpException.Code.UNSUPPORTED_CONTROLLER,
                "Java class has no supported controller methods: " + psiClass.getQualifiedName());
    }

    private static ParsedReference parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new McpException(McpException.Code.INVALID_ARGUMENT, "reference must not be blank");
        }
        String trimmedReference = reference.trim();
        int separator = trimmedReference.indexOf('#');
        if (separator < 0) {
            return new ParsedReference(requirePart(trimmedReference, "class name"), null);
        }
        return new ParsedReference(requirePart(trimmedReference.substring(0, separator), "class name"),
                requirePart(trimmedReference.substring(separator + 1), "method name"));
    }

    private static String requirePart(String value, String name) {
        if (value.isBlank()) {
            throw new McpException(McpException.Code.INVALID_ARGUMENT, name + " must not be blank");
        }
        return value;
    }

    @FunctionalInterface
    interface ClassLookup {
        PsiClass findClass(Project project, String fqcn);
    }

    @FunctionalInterface
    interface DocViewServiceProvider {
        DocViewService get(Project project, PsiClass psiClass);
    }

    @FunctionalInterface
    interface ReadActionComputer {
        <T> T compute(ThrowableComputable<T, RuntimeException> computation);
    }

    private record ParsedReference(String className, String methodName) {
    }

    public static final class ResolvedReference {

        private final PsiClass psiClass;
        private final PsiMethod psiMethod;

        private ResolvedReference(PsiClass psiClass, PsiMethod psiMethod) {
            this.psiClass = Objects.requireNonNull(psiClass, "psiClass");
            this.psiMethod = psiMethod;
        }

        public PsiClass getPsiClass() {
            return psiClass;
        }

        public Optional<PsiMethod> getPsiMethod() {
            return Optional.ofNullable(psiMethod);
        }
    }
}
