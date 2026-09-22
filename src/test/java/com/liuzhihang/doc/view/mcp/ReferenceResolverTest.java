package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.service.DocViewService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReferenceResolverTest {

    @Test
    public void expandsAClassReferenceToEverySupportedMethod() {
        PsiMethod list = methodNamed("list");
        PsiMethod detail = methodNamed("detail");
        PsiClass controller = controllerWith(list, detail, methodNamed("helper"));
        AtomicInteger readActions = new AtomicInteger();
        ReferenceResolver resolver = resolverFor(controller, Set.of("list", "detail"), readActions);

        ReferenceResolver.ResolvedReference reference = resolver.resolve(project(), "com.example.OrderController");
        List<ReferenceResolver.ResolvedReference> uploadReferences = resolver.resolveForUpload(
                project(), "com.example.OrderController");

        assertSame(controller, reference.getPsiClass());
        assertFalse(reference.getPsiMethod().isPresent());
        assertEquals(List.of(list, detail), uploadReferences.stream()
                .map(item -> item.getPsiMethod().orElseThrow())
                .toList());
        assertEquals(2, readActions.get());
    }

    @Test
    public void resolvesOneSupportedMethodReference() {
        PsiMethod list = methodNamed("list");
        PsiClass controller = controllerWith(list, methodNamed("helper"));
        ReferenceResolver resolver = resolverFor(controller, Set.of("list"), new AtomicInteger());

        ReferenceResolver.ResolvedReference reference = resolver.resolve(project(), "com.example.OrderController#list");
        List<ReferenceResolver.ResolvedReference> uploadReferences = resolver.resolveForUpload(
                project(), "com.example.OrderController#list");

        assertSame(controller, reference.getPsiClass());
        assertSame(list, reference.getPsiMethod().orElseThrow());
        assertEquals(1, uploadReferences.size());
        assertSame(list, uploadReferences.get(0).getPsiMethod().orElseThrow());
    }

    @Test
    public void rejectsAMissingClassReference() {
        ReferenceResolver resolver = resolverFor(null, Set.of(), new AtomicInteger());

        assertFailure(McpException.Code.REFERENCE_NOT_FOUND,
                () -> resolver.resolve(project(), "com.example.MissingController"));
    }

    @Test
    public void rejectsAMissingMethodReference() {
        ReferenceResolver resolver = resolverFor(controllerWith(methodNamed("list")), Set.of("list"), new AtomicInteger());

        assertFailure(McpException.Code.REFERENCE_NOT_FOUND,
                () -> resolver.resolve(project(), "com.example.OrderController#missing"));
    }

    @Test
    public void rejectsOverloadedMethodReferencesInsteadOfChoosingOne() {
        ReferenceResolver resolver = resolverFor(controllerWith(methodNamed("overloaded"), methodNamed("overloaded")),
                Set.of("overloaded"), new AtomicInteger());

        assertFailure(McpException.Code.REFERENCE_AMBIGUOUS,
                () -> resolver.resolve(project(), "com.example.OrderController#overloaded"));
    }

    @Test
    public void keepsEverythingAfterTheFirstSeparatorInTheMethodName() {
        ReferenceResolver resolver = resolverFor(controllerWith(methodNamed("list")), Set.of("list"), new AtomicInteger());

        assertFailure(McpException.Code.REFERENCE_NOT_FOUND,
                () -> resolver.resolve(project(), "com.example.OrderController#list#ignored"));
    }

    @Test
    public void rejectsAReferenceWithoutAClassSegment() {
        ReferenceResolver resolver = resolverFor(controllerWith(methodNamed("list")), Set.of("list"), new AtomicInteger());

        assertFailure(McpException.Code.INVALID_ARGUMENT,
                () -> resolver.resolve(project(), "#list"));
    }

    @Test
    public void rejectsAReferenceWithoutAMethodSegment() {
        ReferenceResolver resolver = resolverFor(controllerWith(methodNamed("list")), Set.of("list"), new AtomicInteger());

        assertFailure(McpException.Code.INVALID_ARGUMENT,
                () -> resolver.resolve(project(), "com.example.OrderController#"));
    }

    @Test
    public void rejectsAClassThatHasNoSupportedMethods() {
        ReferenceResolver resolver = resolverFor(controllerWith(methodNamed("helper")), Set.of(), new AtomicInteger());

        assertFailure(McpException.Code.UNSUPPORTED_CONTROLLER,
                () -> resolver.resolveForUpload(project(), "com.example.OrderController"));
    }

    @Test
    public void runsResolveSupportFilteringInsideTheReadActionSeam() {
        AtomicBoolean inReadAction = new AtomicBoolean();
        AtomicInteger checkMethodCalls = new AtomicInteger();
        PsiMethod list = methodNamed("list");
        PsiClass controller = controllerWith(list);
        ReferenceResolver resolver = resolverThatAssertsReadActionBoundaries(
                inReadAction, checkMethodCalls, controller);

        ReferenceResolver.ResolvedReference reference = resolver.resolve(project(), "com.example.OrderController");

        assertSame(controller, reference.getPsiClass());
        assertFalse(reference.getPsiMethod().isPresent());
        assertEquals(1, checkMethodCalls.get());
        assertFalse(inReadAction.get());
    }

    @Test
    public void runsResolveForUploadSupportFilteringInsideTheReadActionSeam() {
        AtomicBoolean inReadAction = new AtomicBoolean();
        AtomicInteger checkMethodCalls = new AtomicInteger();
        PsiMethod list = methodNamed("list");
        PsiClass controller = controllerWith(list);
        ReferenceResolver resolver = resolverThatAssertsReadActionBoundaries(
                inReadAction, checkMethodCalls, controller);

        List<ReferenceResolver.ResolvedReference> references = resolver.resolveForUpload(
                project(), "com.example.OrderController");

        assertEquals(1, references.size());
        assertSame(list, references.get(0).getPsiMethod().orElseThrow());
        assertEquals(1, checkMethodCalls.get());
        assertFalse(inReadAction.get());
    }

    private static ReferenceResolver resolverThatAssertsReadActionBoundaries(AtomicBoolean inReadAction,
                                                                               AtomicInteger checkMethodCalls,
                                                                               PsiClass controller) {
        return new ReferenceResolver(
                (project, fqcn) -> {
                    assertTrue("class lookup must execute under the read action", inReadAction.get());
                    return controller;
                },
                (project, psiClass) -> new DocViewService() {
                    @Override
                    public boolean checkMethod(@NotNull PsiMethod targetMethod) {
                        assertTrue("checkMethod must execute under the read action", inReadAction.get());
                        checkMethodCalls.incrementAndGet();
                        return true;
                    }

                    @Override
                    public List<DocView> buildClassDoc(@NotNull PsiClass psiClass) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public @NotNull DocView buildClassMethodDoc(PsiClass psiClass, @NotNull PsiMethod psiMethod) {
                        throw new UnsupportedOperationException();
                    }
                },
                new ReferenceResolver.ReadActionComputer() {
                    @Override
                    public <T> T compute(ThrowableComputable<T, RuntimeException> computation) {
                        assertFalse("read actions must not be nested for one resolution", inReadAction.get());
                        inReadAction.set(true);
                        try {
                            return computation.compute();
                        } finally {
                            inReadAction.set(false);
                        }
                    }
                });
    }

    private static ReferenceResolver resolverFor(PsiClass controller, Set<String> supportedMethodNames,
                                                 AtomicInteger readActions) {
        DocViewService docViewService = docViewServiceFor(supportedMethodNames);
        return new ReferenceResolver(
                (project, fqcn) -> "com.example.OrderController".equals(fqcn) ? controller : null,
                (project, psiClass) -> docViewService,
                new ReferenceResolver.ReadActionComputer() {
                    @Override
                    public <T> T compute(ThrowableComputable<T, RuntimeException> computation) {
                        readActions.incrementAndGet();
                        return computation.compute();
                    }
                });
    }

    private static DocViewService docViewServiceFor(Set<String> supportedMethodNames) {
        return new DocViewService() {
            @Override
            public boolean checkMethod(@NotNull PsiMethod targetMethod) {
                return supportedMethodNames.contains(targetMethod.getName());
            }

            @Override
            public List<DocView> buildClassDoc(@NotNull PsiClass psiClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public @NotNull DocView buildClassMethodDoc(PsiClass psiClass, @NotNull PsiMethod psiMethod) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Project project() {
        return (Project) Proxy.newProxyInstance(
                ReferenceResolverTest.class.getClassLoader(), new Class<?>[]{Project.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType()));
    }

    private static PsiClass controllerWith(PsiMethod... methods) {
        return (PsiClass) Proxy.newProxyInstance(
                ReferenceResolverTest.class.getClassLoader(), new Class<?>[]{PsiClass.class},
                (proxy, method, arguments) -> {
                    if ("getMethods".equals(method.getName())) {
                        return methods;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PsiMethod methodNamed(String name) {
        return (PsiMethod) Proxy.newProxyInstance(
                ReferenceResolverTest.class.getClassLoader(), new Class<?>[]{PsiMethod.class},
                (proxy, method, arguments) -> {
                    if ("getName".equals(method.getName())) {
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

    private static void assertFailure(McpException.Code expectedCode, Runnable action) {
        try {
            action.run();
            fail("Expected " + expectedCode);
        } catch (McpException exception) {
            assertEquals(expectedCode, exception.getCode());
        }
    }
}
