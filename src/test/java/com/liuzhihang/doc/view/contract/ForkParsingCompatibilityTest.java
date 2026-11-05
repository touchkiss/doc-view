package com.liuzhihang.doc.view.contract;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.UrlRewriteRule;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.utils.CustomPsiCommentUtils;
import com.liuzhihang.doc.view.utils.DocViewUtils;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;

import java.util.List;
import java.util.Map;

/** Cross-contract coverage: upstream correctness fixes must retain fork parser extensions. */
public final class ForkParsingCompatibilityTest extends LightJavaCodeInsightFixtureTestCase {
    private boolean originalSnakeCase;
    private List<UrlRewriteRule> originalRules;

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings settings = Settings.getInstance(getProject());
        originalSnakeCase = settings.getFieldNameCaseType();
        originalRules = settings.getUrlRewriteRules();
        settings.setFieldNameCaseType(false);
        settings.setUrlRewriteRules(List.of());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Settings.getInstance(getProject()).setFieldNameCaseType(originalSnakeCase);
            Settings.getInstance(getProject()).setUrlRewriteRules(originalRules);
        } finally {
            super.tearDown();
        }
    }

    public void testRecordContentUsingAndConstantJsonNameSurviveSchemaTraversal() {
        installJackson();
        PsiClass record = myFixture.addClass("""
                package fork.parser;
                import com.fasterxml.jackson.annotation.JsonProperty;
                import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
                import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
                /**
                 * @param userIds 用户标识
                 *                多行说明
                 */
                public record Payload(
                    @JsonProperty(Names.IDS)
                    @JsonDeserialize(contentUsing=StringDeserializer.class) java.util.List<Long> userIds) {}
                """);
        myFixture.addClass("package fork.parser; class Names { static final String IDS = \"user_ids\"; }");
        Body body = ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(record));
        assertEquals(1, body.getChildList().size());
        Body ids = body.getChildList().get(0);
        assertEquals("user_ids", ids.getName());
        assertTrue(ids.isCollection());
        assertEquals("List<String>", ids.getType());
        assertEquals("0", ids.getExample());
        assertTrue(ids.getDesc().contains("用户标识"));
        assertTrue(ids.getDesc().contains("多行说明"));
        assertTrue("Scalar contentUsing must not manufacture an empty-name object", ids.getChildList().isEmpty());
        Map<String, Object> example = ParamPsiUtils.getFieldsAndDefaultValueForRecord(record, null);
        assertEquals(List.of("0"), example.get("user_ids"));
    }

    public void testFieldWireTypeDefaultValueAndVersionsRemainTogether() {
        installJackson();
        PsiClass dto = myFixture.addClass("""
                package fork.parser;
                import com.fasterxml.jackson.databind.annotation.JsonSerialize;
                import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
                public class Metadata {
                    /** Identifier.
                     * @since 2.1
                     * @version v3
                     */
                    @JsonSerialize(using=ToStringSerializer.class)
                    private long id = 15L;
                    private int code = 100;
                }
                """);
        Body body = ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(dto));
        Body id = child(body, "id");
        assertEquals("String", id.getType());
        assertEquals("0", id.getExample());
        assertEquals("2.1", id.getSince());
        assertEquals("v3", id.getVersion());
        assertEquals("100", child(body, "code").getExample());
        assertEquals("0", ParamPsiUtils.getFieldsAndDefaultValue(dto, null).get("id"));
    }

    public void testSharedNestedRecordIsNotDroppedOnSiblingBranch() {
        PsiClass leaf = myFixture.addClass("package fork.parser; public record Leaf(String value) {}");
        PsiClass root = myFixture.addClass("package fork.parser; public record Pair(Leaf left, Leaf right) {}");
        Body body = ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(root));
        assertEquals(2, body.getChildList().size());
        assertEquals("value", child(body, "left").getChildList().get(0).getName());
        assertEquals("value", child(body, "right").getChildList().get(0).getName());
        assertNotNull(leaf);
    }

    public void testCollectionProjectionMarksOnlySyntheticLayer() {
        myFixture.addClass("package fork.parser; public class Item { private String element; }");
        PsiClass root = myFixture.addClass("package fork.parser; public record Batch(java.util.List<Item> values) {}");
        Body body = ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(root));
        Body values = child(body, "values");
        assertTrue(values.isCollection());
        assertFalse(values.isSyntheticContainerNode());
        Body wrapper = values.getChildList().get(0);
        assertEquals("element", wrapper.getName());
        assertTrue(wrapper.isSyntheticContainerNode());
        assertFalse(child(wrapper, "element").isSyntheticContainerNode());
    }

    public void testDepthLimitAlsoBoundsRecordTraversal() {
        myFixture.addClass("package fork.parser; public record Leaf(String value) {}");
        PsiClass root = myFixture.addClass("package fork.parser; public record Root(Leaf child) {}");
        Body result = new Body();
        ParamPsiUtils.buildBodyList(root, null, result, ParamPsiUtils.DtoParsingContext.root(1));
        assertEquals(1, result.getChildList().size());
        assertTrue(result.getChildList().get(0).getChildList().isEmpty());
    }

    public void testRouteArrayAndAliasStillApplyForkUrlRewrite() {
        for (String name : List.of("RequestMapping", "GetMapping")) {
            myFixture.addClass("package org.springframework.web.bind.annotation; public @interface " + name
                    + " { String[] value() default {}; String[] path() default {}; }");
        }
        Settings.getInstance(getProject()).setUrlRewriteRules(List.of(
                new UrlRewriteRule(true, "^/internal/(.*)$", "/public/$1")));
        PsiClass controller = myFixture.addClass("""
                package fork.parser;
                import org.springframework.web.bind.annotation.*;
                @RequestMapping(path={"/internal", "/ignored"})
                public class Controller {
                    static final String PATH = "/users";
                    @GetMapping(path={PATH, "/ignored"}) public void users() {}
                }
                """);
        DocView doc = new SpringDocViewServiceImpl().buildClassMethodDoc(controller, controller.getMethods()[0]);
        assertEquals("/public/users", doc.getPath());
    }

    public void testExactMultilineJavadocAndConstantBindingWithOpenApiExample() {
        myFixture.addClass("""
                package org.springframework.web.bind.annotation;
                public @interface RequestParam { String name() default ""; String value() default "";
                    String defaultValue() default "\\n\\t\\t\\n\\t\\t\\n\\uE000\\uE001\\uE002";
                    boolean required() default true; }
                """);
        myFixture.addClass("""
                package io.swagger.v3.oas.annotations;
                public @interface Parameter { String name() default ""; String description() default "";
                    String example() default ""; boolean required() default false; }
                """);
        PsiClass controller = myFixture.addClass("""
                package fork.parser;
                import org.springframework.web.bind.annotation.RequestParam;
                import io.swagger.v3.oas.annotations.Parameter;
                public class Controller {
                    static final String NAME = "user_id";
                    /**
                     * @param userIdLong longer parameter
                     * @param userId correct description
                     *               continued text
                     */
                    public void find(@RequestParam(NAME) @Parameter(example="42") String userId, String userIdLong) {}
                }
                """);
        PsiMethod method = controller.getMethods()[0];
        assertEquals("correct description continued text", CustomPsiCommentUtils.paramDocComment(
                method.getDocComment(), method.getParameterList().getParameters()[0]));
        Param first = SpringPsiUtils.buildFormParam(method).get(0);
        assertEquals("user_id", first.getName());
        assertEquals("42", first.getExample());
        assertEquals("correct description continued text", first.getDesc());
    }

    public void testNoConstraintHasEmptyDescriptionWhileJakartaConstraintTextRemains() {
        myFixture.addClass("package jakarta.validation.constraints; public @interface Min { long value(); }");
        PsiClass dto = myFixture.addClass("""
                package fork.parser;
                public class ValidationDto {
                    private String plain;
                    /** Count. */
                    @jakarta.validation.constraints.Min(3) private int count;
                }
                """);
        assertEquals("", SpringPsiUtils.getValidatedValue(dto.findFieldByName("plain", false)));
        assertEquals("", DocViewUtils.fieldDesc(dto.findFieldByName("plain", false)));
        String description = DocViewUtils.fieldDesc(dto.findFieldByName("count", false));
        assertTrue(description, description.contains("Count."));
        assertTrue(description, description.contains("最小值:3;"));
    }

    private Body child(Body parent, String name) {
        return parent.getChildList().stream().filter(body -> name.equals(body.getName())).findFirst().orElseThrow();
    }

    private void installJackson() {
        myFixture.addClass("package com.fasterxml.jackson.annotation; public @interface JsonProperty { String value(); }");
        for (String name : List.of("JsonSerialize", "JsonDeserialize")) {
            myFixture.addClass("package com.fasterxml.jackson.databind.annotation; public @interface " + name
                    + " { Class<?> using() default Void.class; Class<?> contentUsing() default Void.class; }");
        }
        myFixture.addClass("package com.fasterxml.jackson.databind.ser.std; public class ToStringSerializerBase {}");
        myFixture.addClass("package com.fasterxml.jackson.databind.ser.std; public class ToStringSerializer extends ToStringSerializerBase {}");
        myFixture.addClass("package com.fasterxml.jackson.databind.deser.std; public class StringDeserializer {}");
    }
}
