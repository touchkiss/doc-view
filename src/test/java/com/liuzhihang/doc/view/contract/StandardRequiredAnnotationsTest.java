package com.liuzhihang.doc.view.contract;

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.service.DtoSchemaCacheService;
import com.liuzhihang.doc.view.service.impl.DubboDocViewServiceImpl;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.service.impl.YapiSaveFactory;
import com.liuzhihang.doc.view.testsupport.AnnotationStubs;
import com.liuzhihang.doc.view.utils.PojoUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads existing validation annotations; never adds annotations or source tags. */
public final class StandardRequiredAnnotationsTest extends LightJavaCodeInsightFixtureTestCase {
    private Boolean originalSnakeCase;
    private static final List<String> REQUIRED = List.of("jakartaNull", "jakartaEmpty", "jakartaBlank",
            "javaxNull", "javaxEmpty", "javaxBlank");

    @Override protected LightProjectDescriptor getProjectDescriptor() { return JAVA_21; }

    @Override protected void setUp() throws Exception {
        super.setUp();
        originalSnakeCase = Settings.getInstance(getProject()).getFieldNameCaseType();
        Settings.getInstance(getProject()).setFieldNameCaseType(false);
        AnnotationStubs.installSpringWeb(myFixture);
        for (String namespace : List.of("jakarta", "javax")) {
            for (String name : List.of("NotNull", "NotEmpty", "NotBlank")) {
                myFixture.addClass("package " + namespace + ".validation.constraints; "
                        + "@java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.FIELD, "
                        + "java.lang.annotation.ElementType.ANNOTATION_TYPE, java.lang.annotation.ElementType.CONSTRUCTOR, "
                        + "java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.TYPE_USE}) "
                        + "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface " + name + " {}");
            }
        }
        myFixture.addClass("package jakarta.validation.constraints; public @interface Size { int min() default 0; int max() default 2147483647; }");
        myFixture.addClass("package javax.validation.constraints; public @interface Min { long value(); }");
        myFixture.addClass("package jakarta.validation; public @interface Valid {}");
    }

    @Override protected void tearDown() throws Exception {
        try { Settings.getInstance(getProject()).setFieldNameCaseType(originalSnakeCase); }
        finally { super.tearDown(); }
    }

    public void testPojoFieldsAndPsiFreeMarkdownUseBothValidationNamespaces() {
        PsiClass type = myFixture.addClass("""
                package standardrequired;
                public class RequiredDto {
                    @jakarta.validation.constraints.NotNull String jakartaNull;
                    @jakarta.validation.constraints.NotEmpty java.util.List<String> jakartaEmpty;
                    @jakarta.validation.constraints.NotBlank String jakartaBlank;
                    @javax.validation.constraints.NotNull String javaxNull;
                    @javax.validation.constraints.NotEmpty String[] javaxEmpty;
                    @javax.validation.constraints.NotBlank String javaxBlank;
                    @jakarta.validation.constraints.Size(min=1) String sized;
                    @javax.validation.constraints.Min(1) Integer positive;
                    @jakarta.validation.Valid Object nested;
                    /** @DocView.Required */ String legacy;
                    String optional;
                }
                """);
        String before = type.getContainingFile().getText();
        Body body = PojoUtils.buildBody(type);
        assertRequirements(body);
        Body cached = DtoSchemaCacheService.copyBody(body, null, false);
        assertRequirements(cached);
        String markdown = DocViewData.bodyMarkdown(DocViewData.buildBodyDataList(getProject(), cached.getChildList()), false);
        assertTrue(markdown, markdown.contains("|jakartaNull|String|Y|"));
        assertTrue(markdown, markdown.contains("|javaxBlank|String|Y|"));
        assertEquals(before, type.getContainingFile().getText());
    }

    public void testConstrainedArraysPreserveContainerAndObjectFieldMetadata() {
        PsiClass type = myFixture.addClass("""
                package standardrequired;
                public class RequiredArraysDto {
                    @jakarta.validation.constraints.NotEmpty RequiredChild[] objects;
                    @javax.validation.constraints.NotEmpty int[] numbers;
                    @javax.validation.constraints.NotEmpty RequiredChild[][] matrix;
                }
                class RequiredChild { @jakarta.validation.constraints.NotNull String id; }
                """);
        String original = type.getContainingFile().getText();
        var fields = PojoUtils.buildBody(type).getChildList().stream().collect(Collectors.toMap(Body::getName, row -> row));
        for (String name : List.of("objects", "numbers", "matrix")) {
            assertTrue(name, fields.get(name).getRequired());
            assertTrue(name, fields.get(name).isCollection());
        }
        assertEquals("RequiredChild[]", fields.get("objects").getType());
        assertEquals("int[]", fields.get("numbers").getType());
        assertTrue(fields.get("numbers").getChildList().isEmpty());
        assertEquals("RequiredChild[][]", fields.get("matrix").getType());
        for (String name : List.of("objects", "matrix")) {
            assertTrue(fields.get(name).getChildList().stream().anyMatch(row -> "id".equals(row.getName()) && row.getRequired()));
        }
        assertEquals(original, type.getContainingFile().getText());
    }

    public void testConstrainedArrayJson5KeepsNumericValuesAndDimensions() {
        PsiClass type = myFixture.addClass("""
                package standardrequired;
                public class ArrayExamplesDto {
                    @jakarta.validation.constraints.NotEmpty int[] numbers = {1, 2};
                    @javax.validation.constraints.NotEmpty boolean[][] flags = {{true}};
                    @javax.validation.constraints.NotEmpty ArrayChild[][] matrix;
                }
                class ArrayChild { @jakarta.validation.constraints.NotNull String id; }
                """);
        DocView doc = new DocView("arrays");
        doc.setPsiClass(type);
        doc.setType(com.liuzhihang.doc.view.enums.FrameworkEnum.NONE_POJO);
        doc.setContentType(com.liuzhihang.doc.view.enums.ContentTypeEnum.JSON);
        doc.setRespBody(PojoUtils.buildBody(type));
        String json5 = new DocViewData(getProject(), doc).getResponseJson5();
        var json = com.google.gson.JsonParser.parseString(json5).getAsJsonObject();
        assertTrue("Primitive array items must be numeric, not Java initializer strings: " + json5,
                json.getAsJsonArray("numbers").get(0).getAsJsonPrimitive().isNumber());
        assertTrue("Multidimensional primitive arrays must retain both levels: " + json5,
                json.getAsJsonArray("flags").get(0).isJsonArray());
        assertTrue(json.getAsJsonArray("flags").get(0).getAsJsonArray().get(0).getAsJsonPrimitive().isBoolean());
        assertTrue("Multidimensional object arrays must retain both levels: " + json5,
                json.getAsJsonArray("matrix").get(0).isJsonArray());
        assertTrue(json.getAsJsonArray("matrix").get(0).getAsJsonArray().get(0).getAsJsonObject().has("id"));
    }

    public void testRecordComponentsUseRealValidationAnnotationTargets() {
        PsiClass type = myFixture.addClass("""
                package standardrequired;
                public record RequiredRecord(
                    @jakarta.validation.constraints.NotNull String jakartaNull,
                    @jakarta.validation.constraints.NotEmpty java.util.List<String> jakartaEmpty,
                    @jakarta.validation.constraints.NotBlank String jakartaBlank,
                    @javax.validation.constraints.NotNull String javaxNull,
                    @javax.validation.constraints.NotEmpty String[] javaxEmpty,
                    @javax.validation.constraints.NotBlank String javaxBlank,
                    String optional) {}
                """);
        assertRequirements(PojoUtils.buildBody(type));
    }

    public void testSpringParametersReachMarkdownAndYapiRequiredFlags() {
        PsiClass type = myFixture.addClass("""
                package standardrequired;
                @org.springframework.web.bind.annotation.RestController public class RequiredController {
                    @org.springframework.web.bind.annotation.GetMapping("/required")
                    public void query(
                        @jakarta.validation.constraints.NotNull String jakartaNull,
                        @jakarta.validation.constraints.NotEmpty java.util.List<String> jakartaEmpty,
                        @jakarta.validation.constraints.NotBlank String jakartaBlank,
                        @javax.validation.constraints.NotNull String javaxNull,
                        @javax.validation.constraints.NotEmpty java.util.List<String> javaxEmpty,
                        @javax.validation.constraints.NotBlank String javaxBlank,
                        String optional) {}
                }
                """);
        String before = type.getContainingFile().getText();
        DocView doc = new SpringDocViewServiceImpl().buildClassMethodDoc(type, type.getMethods()[0]);
        var flags = doc.getReqParamList().stream().collect(Collectors.toMap(p -> p.getName(), p -> p.getRequired()));
        for (String name : REQUIRED) assertEquals(name, Boolean.TRUE, flags.get(name));
        assertEquals(Boolean.FALSE, flags.get("optional"));
        var save = new YapiSaveFactory().create(settings(), category(), doc);
        for (var query : save.getReqQuery()) assertEquals(query.getName(), query.getName().equals("optional") ? "0" : "1", query.getRequired());
        assertEquals(before, type.getContainingFile().getText());
    }

    public void testDubboParametersUseValidationConstraintsInsteadOfBeingAlwaysOptional() {
        PsiClass type = myFixture.addClass("""
                package standardrequired;
                public interface RequiredRpc {
                    void query(
                        @jakarta.validation.constraints.NotNull String jakartaNull,
                        @jakarta.validation.constraints.NotEmpty java.util.List<String> jakartaEmpty,
                        @jakarta.validation.constraints.NotBlank String jakartaBlank,
                        @javax.validation.constraints.NotNull String javaxNull,
                        @javax.validation.constraints.NotEmpty java.util.List<String> javaxEmpty,
                        @javax.validation.constraints.NotBlank String javaxBlank,
                        String optional);
                }
                """);
        String before = type.getContainingFile().getText();
        DocView doc = new DubboDocViewServiceImpl().buildClassMethodDoc(type, type.getMethods()[0]);
        assertRequirements(doc.getReqBody());
        assertEquals(before, type.getContainingFile().getText());
    }

    private void assertRequirements(Body root) {
        Map<String, Boolean> flags = root.getChildList().stream().collect(Collectors.toMap(Body::getName, Body::getRequired));
        for (String name : REQUIRED) assertEquals(name, Boolean.TRUE, flags.get(name));
        assertEquals(Boolean.FALSE, flags.get("optional"));
        root.getChildList().stream().filter(row -> row.getName().equals("javaxEmpty") && row.getType().endsWith("[]"))
                .forEach(row -> {
                    assertTrue("Arrays retain their container shape", row.isCollection());
                    assertTrue("Scalar array contents must not expand String implementation fields", row.getChildList().isEmpty());
                });
        for (String nullable : List.of("sized", "positive", "nested", "legacy")) {
            if (flags.containsKey(nullable)) assertEquals(nullable, Boolean.FALSE, flags.get(nullable));
        }
    }

    private static YApiSettings settings() {
        YApiSettings settings = new YApiSettings();
        settings.setUrl("http://yapi.example"); settings.setProjectId(299L); settings.setToken("test-token");
        return settings;
    }
    private static YApiCat category() { YApiCat category = new YApiCat(); category.setId(1L); return category; }
}
