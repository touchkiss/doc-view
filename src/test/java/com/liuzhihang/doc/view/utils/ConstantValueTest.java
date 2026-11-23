package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.*;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.Param;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exercises documentation output with real Java PSI, including class-file-only dependencies. */
public class ConstantValueTest extends LightJavaCodeInsightFixtureTestCase {
    private Boolean previousFieldNameCaseType;

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var settings = Settings.getInstance(getProject());
        previousFieldNameCaseType = settings.getFieldNameCaseType();
        settings.setFieldNameCaseType(false);
        myFixture.addClass("""
                package com.fasterxml.jackson.annotation;
                public @interface JsonProperty { String value() default ""; }
                """);
        myFixture.addClass("""
                package sourceconstants;
                public class OrderConstants { public static final String REQUEST_ID = "request_id"; }
                """);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Settings.getInstance(getProject()).setFieldNameCaseType(previousFieldNameCaseType);
        } finally {
            super.tearDown();
        }
    }

    private PsiField field(String declaration) {
        return javaClass("class Response { static final int SUCCESS = 10000; " + declaration + " }")
                .findFieldByName("code", false);
    }

    private PsiClass javaClass(String source) {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("Response.java", source);
        return file.getClasses()[0];
    }

    public void testResolvesConstantInNumericJsonExample() {
        PsiField field = field("private int code = SUCCESS;");
        assertEquals(10000, SpringPsiUtils.getDefaultValue(field, field.getType()));
    }

    public void testResolvesConstantInFieldExample() {
        assertEquals("10000", DocViewUtils.fieldExample(field("private int code = SUCCESS;")));
    }

    private String annotatedSource(String declaration) {
        return "import com.fasterxml.jackson.annotation.JsonProperty; "
                + "import sourceconstants.OrderConstants; " + declaration;
    }

    public void testResolvesJsonPropertyConstantForField() {
        PsiClass cls = javaClass(annotatedSource(
                "class Response { @JsonProperty(OrderConstants.REQUEST_ID) String requestId; }"));
        var field = cls.getFields()[0];
        var annotation = field.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty");
        assertNotNull("The test fixture must resolve the JsonProperty declaration", annotation);
        var value = (PsiReferenceExpression) annotation.findDeclaredAttributeValue("value");
        assertNotNull("The test fixture must resolve its source constant", value.resolve());
        assertEquals("request_id", PsiConstantUtils.constantValue(value));
        assertEquals("request_id", DocViewUtils.fieldName(field, false));
    }

    public void testResolvesJsonPropertyConstantForRecord() {
        PsiClass cls = javaClass(annotatedSource(
                "record Response(@JsonProperty(OrderConstants.REQUEST_ID) String requestId) {}"));
        assertEquals("request_id", DocViewUtils.fieldName(cls.getRecordComponents()[0], false));
    }

    public void testResolvesConstantsFromCompiledClassesWithoutSources() throws Exception {
        var directory = FileUtil.createTempDirectory("doc-view-constants", null);
        Library library = null;
        try {
            var sources = Files.createDirectory(directory.toPath().resolve("sources"));
            var classes = Files.createDirectory(directory.toPath().resolve("classes"));
            var enumSource = sources.resolve("EcommerceErrorCodeGrpcEnum.java");
            Files.writeString(enumSource, "package generated; public enum EcommerceErrorCodeGrpcEnum { "
                    + "ECOMMERCE_SUCCESS; public static final int ECOMMERCE_SUCCESS_VALUE = 10000; }");
            var namesSource = sources.resolve("OrderConstants.java");
            Files.writeString(namesSource, "package generated; public class OrderConstants { "
                    + "public static final String REQUEST_ID = \"request_id\"; }");
            var compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull("A JDK is required to compile the binary fixture", compiler);
            assertEquals(0, compiler.run(null, null, null,
                    "-proc:none", "-d", classes.toString(), enumSource.toString(), namesSource.toString()));
            var root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(classes);
            assertNotNull(root);
            library = PsiTestUtil.newLibrary("constant-value-binaries").classesRoot(root).addTo(getModule());
            IndexingTestUtil.waitUntilIndexesAreReady(getProject());
            PsiClass cls = javaClass("import com.fasterxml.jackson.annotation.JsonProperty; "
                    + "import generated.EcommerceErrorCodeGrpcEnum; "
                    + "import static generated.OrderConstants.REQUEST_ID; "
                    + "class Response { private int code = EcommerceErrorCodeGrpcEnum.ECOMMERCE_SUCCESS_VALUE; "
                    + "@JsonProperty(REQUEST_ID) private String requestId; }");
            PsiField code = cls.findFieldByName("code", false);
            PsiElement target = ((PsiReferenceExpression) code.getInitializer()).resolve();
            assertTrue("The fixture must resolve a binary class, not a source file", target instanceof PsiCompiledElement);
            assertEquals(10000, SpringPsiUtils.getDefaultValue(code, code.getType()));
            assertEquals("10000", DocViewUtils.fieldExample(code));
            assertEquals("request_id", DocViewUtils.fieldName(cls.findFieldByName("requestId", false), false));
            Body body = new Body();
            ParamPsiUtils.buildBodyParam(cls, code, null, body, new HashMap<>(), false);
            assertEquals("10000", body.getChildList().getFirst().getExample());
            Map<String, Object> json = ParamPsiUtils.getFieldsAndDefaultValue(cls, null);
            assertEquals(10000, json.get("code"));
            assertTrue(json.containsKey("request_id"));
            assertFalse(json.containsKey("REQUEST_ID"));
        } finally {
            if (library != null) {
                PsiTestUtil.removeLibrary(getModule(), library);
            }
            FileUtil.delete(directory);
        }
    }

    public void testEvaluatesArithmeticAndPreservesPrimitiveTypes() {
        PsiField value = field("long code = SUCCESS + 2;");
        assertEquals(10002L, SpringPsiUtils.getDefaultValue(value, value.getType()));
        value = field("boolean code = SUCCESS == 10000;");
        assertEquals(true, SpringPsiUtils.getDefaultValue(value, value.getType()));
        value = field("char code = 'x';");
        assertEquals('x', SpringPsiUtils.getDefaultValue(value, value.getType()));
    }

    public void testDecodesStringLiteralsWithoutRemovingEmbeddedQuotes() {
        PsiField value = field("String code = \"\\\"quoted\\\"\";");
        assertEquals("\"quoted\"", SpringPsiUtils.getDefaultValue(value, value.getType()));
        assertEquals("\"quoted\"", DocViewUtils.fieldExample(value));
    }

    public void testFallsBackForUnresolvedExpressionsAndKeepsCommentExamplePriority() {
        assertEquals("Missing.VALUE", DocViewUtils.fieldExample(field("int code = Missing.VALUE;")));
        assertEquals("123", DocViewUtils.fieldExample(field("/** @value 123 */ int code = SUCCESS;")));
    }

    public void testUsesFieldNameForEmptyOrUnresolvedJsonProperty() {
        for (String expression : new String[]{"\"\"", "Missing.VALUE"}) {
            PsiClass cls = javaClass(annotatedSource(
                    "class Response { @JsonProperty(" + expression + ") String requestId; }"));
            assertEquals("requestId", DocViewUtils.fieldName(cls.getFields()[0], false));
        }
    }

    public void testKeepsNonFiniteConstantsSerializableInJsonExamples() {
        PsiClass cls = javaClass("class Response { double code = 1.0 / 0.0; }");
        Map<String, Object> json = ParamPsiUtils.getFieldsAndDefaultValue(cls, null);
        assertEquals("1.0 / 0.0", json.get("code"));
        assertTrue(GsonFormatUtil.gsonFormat(json).contains("1.0 / 0.0"));
    }

    public void testExpandsGetBeanParametersUsingConfiguredFieldNames() {
        Settings.getInstance(getProject()).setFieldNameCaseType(true);
        PsiClass controller = javaClassNamed(springSource(
                "class Request { String userId; int pageSize; } "
                        + "class Controller { void list(Request request) {} }"), "Controller");

        List<Param> params = SpringPsiUtils.buildFormParam(controller.findMethodsByName("list", false)[0]);

        assertEquals(2, params.size());
        assertEquals("user_id", params.get(0).getName());
        assertEquals("page_size", params.get(1).getName());
    }

    public void testExpandsRecordParametersUsingCamelCaseWhenConfigured() {
        Settings.getInstance(getProject()).setFieldNameCaseType(false);
        PsiClass controller = javaClassNamed(springSource(
                "record Request(String userId, int pageSize) {} "
                        + "class Controller { void list(Request request) {} }"), "Controller");

        List<Param> params = SpringPsiUtils.buildFormParam(controller.findMethodsByName("list", false)[0]);

        assertEquals(2, params.size());
        assertEquals("userId", params.get(0).getName());
        assertEquals("pageSize", params.get(1).getName());
    }

    public void testKeepsExplicitRequestParamNameForBeanField() {
        PsiClass controller = javaClassNamed(springSource(
                "class Request { @RequestParam(name = \"user_id\") String userId; } "
                        + "class Controller { void list(Request request) {} }"), "Controller");

        List<Param> params = SpringPsiUtils.buildFormParam(controller.findMethodsByName("list", false)[0]);

        assertEquals(1, params.size());
        assertEquals("user_id", params.get(0).getName());
    }

    private PsiClass javaClassNamed(String source, String name) {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText(name + ".java", source);
        for (PsiClass psiClass : file.getClasses()) {
            if (name.equals(psiClass.getName())) {
                return psiClass;
            }
        }
        fail("Missing class " + name);
        return null;
    }

    private String springSource(String declaration) {
        return "package org.springframework.web.bind.annotation; "
                + "@interface RequestParam { String name() default \"\"; String value() default \"\"; "
                + "boolean required() default true; String defaultValue() default \"\"; } "
                + declaration;
    }
}
