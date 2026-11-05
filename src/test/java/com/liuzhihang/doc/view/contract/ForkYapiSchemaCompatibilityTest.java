package com.liuzhihang.doc.view.contract;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.liuzhihang.doc.view.service.DtoSchemaCacheService;
import com.liuzhihang.doc.view.service.impl.YapiSaveFactory;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** The real parser, cache snapshot and YApi conversion must agree on synthetic container nodes. */
public final class ForkYapiSchemaCompatibilityTest extends LightJavaCodeInsightFixtureTestCase {
    private boolean originalSnakeCase;
    private Set<String> originalRequired;

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        originalSnakeCase = Settings.getInstance(getProject()).getFieldNameCaseType();
        Settings.getInstance(getProject()).setFieldNameCaseType(false);
        originalRequired = Settings.getInstance(getProject()).getRequiredFieldAnnotation();
        Set<String> required = new HashSet<>(originalRequired);
        required.add("jakarta.validation.constraints.NotNull");
        Settings.getInstance(getProject()).setRequiredFieldAnnotation(required);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Settings.getInstance(getProject()).setFieldNameCaseType(originalSnakeCase);
            Settings.getInstance(getProject()).setRequiredFieldAnnotation(originalRequired);
        } finally {
            super.tearDown();
        }
    }

    public void testPojoListUnwrapsSyntheticElementButKeepsRealElementKeyAndValueFields() {
        myFixture.addClass("package jakarta.validation.constraints; public @interface NotNull {}");
        myFixture.addClass("""
                package fork.yapi;
                public class Item {
                    @jakarta.validation.constraints.NotNull
                    private String element = "one";
                    private int key = 7;
                    private boolean value = true;
                }
                """);
        PsiClass root = myFixture.addClass("package fork.yapi; public class Payload { private java.util.List<Item> items; }");
        for (boolean snapshot : List.of(false, true)) {
            JsonObject schema = schema(root, snapshot);
            JsonObject items = property(schema, "items");
            assertEquals("array", items.get("type").getAsString());
            JsonObject item = items.getAsJsonObject("items");
            assertEquals("object", item.get("type").getAsString());
            assertEquals(3, item.getAsJsonObject("properties").size());
            assertEquals("string", property(item, "element").get("type").getAsString());
            assertEquals("one", property(item, "element").get("default").getAsString());
            assertEquals("number", property(item, "key").get("type").getAsString());
            assertEquals("7", property(item, "key").get("default").getAsString());
            assertEquals("boolean", property(item, "value").get("type").getAsString());
            assertTrue(item.getAsJsonArray("required").toString().contains("element"));
        }
    }

    public void testRecordListUnwrapsSyntheticElementAndRetainsRequiredComponent() {
        myFixture.addClass("package jakarta.validation.constraints; public @interface NotNull {}");
        myFixture.addClass("package fork.yapi; public record Item(@jakarta.validation.constraints.NotNull String element, long value) {}");
        PsiClass root = myFixture.addClass("package fork.yapi; public record Payload(java.util.List<Item> records) {}");
        for (boolean snapshot : List.of(false, true)) {
            JsonObject item = property(schema(root, snapshot), "records").getAsJsonObject("items");
            assertEquals("object", item.get("type").getAsString());
            assertEquals("string", property(item, "element").get("type").getAsString());
            assertEquals("number", property(item, "value").get("type").getAsString());
            assertTrue(item.getAsJsonArray("required").toString().contains("element"));
        }
    }

    public void testMapValuesAreDynamicSchemasWithoutSyntheticKeyValueProperties() {
        myFixture.addClass("package fork.yapi; public class Item { private String key; private long value; }");
        PsiClass root = myFixture.addClass("""
                package fork.yapi;
                public class Payload {
                    private java.util.Map<String, Item> mapping;
                    private java.util.Map<String, Integer> numbers;
                }
                """);
        for (boolean snapshot : List.of(false, true)) {
            JsonObject map = property(schema(root, snapshot), "mapping");
            assertEquals("object", map.get("type").getAsString());
            assertFalse(map.has("properties") && map.getAsJsonObject("properties").has("key"));
            JsonObject value = map.getAsJsonObject("additionalProperties");
            assertEquals("object", value.get("type").getAsString());
            assertEquals("string", property(value, "key").get("type").getAsString());
            assertEquals("number", property(value, "value").get("type").getAsString());
            JsonObject numbers = property(schema(root, snapshot), "numbers");
            assertEquals("number", numbers.getAsJsonObject("additionalProperties").get("type").getAsString());
        }
    }

    public void testContentUsingRetainsStringArrayRatherThanNestedObject() {
        myFixture.addClass("""
                package com.fasterxml.jackson.databind.annotation;
                public @interface JsonDeserialize { Class<?> contentUsing() default Void.class; }
                """);
        myFixture.addClass("package com.fasterxml.jackson.databind.deser.std; public class StringDeserializer {}");
        PsiClass root = myFixture.addClass("""
                package fork.yapi;
                public class Payload {
                    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                        contentUsing=com.fasterxml.jackson.databind.deser.std.StringDeserializer.class)
                    private java.util.List<Long> ids;
                }
                """);
        for (boolean snapshot : List.of(false, true)) {
            JsonObject ids = property(schema(root, snapshot), "ids");
            assertEquals("array", ids.get("type").getAsString());
            assertEquals("string", ids.getAsJsonObject("items").get("type").getAsString());
            assertEquals("0", ids.getAsJsonObject("items").get("default").getAsString());
            assertFalse(ids.getAsJsonObject("items").has("properties"));
        }
    }

    private JsonObject schema(PsiClass root, boolean snapshot) {
        Body body = ParamPsiUtils.buildRespBody(PsiTypesUtil.getClassType(root));
        if (snapshot) {
            body = DtoSchemaCacheService.copyBody(body, null, false);
            assertNull(body.getChildList().get(0).getPsiElement());
        }
        DocView document = new DocView();
        document.setName("Schema compatibility");
        document.setDesc("Schema compatibility");
        document.setMethod("POST");
        document.setPath("/schema");
        document.setContentType(ContentTypeEnum.JSON);
        document.setHeaderList(List.of());
        document.setReqParamList(List.of());
        document.setReqBody(body);
        document.setRespBody(body);
        YApiSettings settings = new YApiSettings();
        settings.setUrl("http://localhost");
        settings.setProjectId(1L);
        YApiCat category = new YApiCat();
        category.setId(1L);
        YapiSave save = new YapiSaveFactory().create(settings, category, document);
        assertEquals(save.getResBody(), save.getReqBodyOther());
        return JsonParser.parseString(save.getResBody()).getAsJsonObject();
    }

    private JsonObject property(JsonObject ownerSchema, String name) {
        assertNotNull("Expected properties in " + ownerSchema, ownerSchema.getAsJsonObject("properties"));
        JsonObject property = ownerSchema.getAsJsonObject("properties").getAsJsonObject(name);
        assertNotNull("Missing property " + name + " in " + ownerSchema, property);
        return property;
    }
}
