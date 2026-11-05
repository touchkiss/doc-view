package com.liuzhihang.doc.view.contract;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.service.DtoSchemaCacheService;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.testsupport.AnnotationStubs;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;

import java.util.Set;

/** Parameter-specific metadata must not leak between endpoints sharing a request-body type. */
public final class ForkRequestBodyCacheTest extends LightJavaCodeInsightFixtureTestCase {
    private Set<String> originalRequiredAnnotations;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        AnnotationStubs.installSpringWeb(myFixture);
        myFixture.addClass("package org.springframework.web.bind.annotation; public @interface PostMapping { String[] value() default {}; }");
        myFixture.addClass("package org.springframework.web.bind.annotation; public @interface RequestBody { boolean required() default true; }");
        myFixture.addClass("package fork.requestcache; public @interface MustSupply {}");
        Settings settings = Settings.getInstance(getProject());
        originalRequiredAnnotations = settings.getRequiredFieldAnnotation();
        settings.setRequiredFieldAnnotation(Set.of("fork.requestcache.MustSupply"));
        DtoSchemaCacheService.getInstance(getProject()).clear();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Settings.getInstance(getProject()).setRequiredFieldAnnotation(originalRequiredAnnotations);
            DtoSchemaCacheService.getInstance(getProject()).clear();
        } finally {
            super.tearDown();
        }
    }

    public void testStringBodiesRetainTheirOwnParameterNameAndRequiredFlagAcrossCacheHits() {
        assertEndpointsRemainIndependent("String", "firstText", "secondText");
    }

    public void testPrimitiveBodiesRetainTheirOwnParameterNameAndRequiredFlagAcrossCacheHits() {
        assertEndpointsRemainIndependent("int", "firstNumber", "secondNumber");
    }

    private void assertEndpointsRemainIndependent(String type, String firstName, String secondName) {
        // All PSI is installed before generation, so no source modification invalidates the cache
        // between these endpoint calls and hides a type-only-key collision.
        PsiClass controller = myFixture.addClass("""
                package fork.requestcache;
                import org.springframework.web.bind.annotation.*;
                @RestController
                public class CacheController {
                    @PostMapping("/first")
                    public void first(@RequestBody @MustSupply %s %s) {}
                    @PostMapping("/second")
                    public void second(@RequestBody(required=false) %s %s) {}
                }
                """.formatted(type, firstName, type, secondName));
        PsiMethod first = controller.findMethodsByName("first", false)[0];
        PsiMethod second = controller.findMethodsByName("second", false)[0];
        SpringDocViewServiceImpl service = new SpringDocViewServiceImpl();

        // Prove the parser's cold result for each endpoint before testing cached service results.
        assertBody(SpringPsiUtils.buildBody(first.getParameterList().getParameters()[0]), firstName, true);
        assertBody(SpringPsiUtils.buildBody(second.getParameterList().getParameters()[0]), secondName, false);

        assertBody(service.buildClassMethodDoc(controller, first).getReqBody(), firstName, true);
        assertBody(service.buildClassMethodDoc(controller, second).getReqBody(), secondName, false);
        assertBody(service.buildClassMethodDoc(controller, first).getReqBody(), firstName, true);
        assertBody(service.buildClassMethodDoc(controller, second).getReqBody(), secondName, false);
    }

    private static void assertBody(Body root, String expectedName, boolean expectedRequired) {
        assertEquals(1, root.getChildList().size());
        Body field = root.getChildList().getFirst();
        assertEquals(expectedName, field.getName());
        assertEquals(Boolean.valueOf(expectedRequired), field.getRequired());
    }
}
