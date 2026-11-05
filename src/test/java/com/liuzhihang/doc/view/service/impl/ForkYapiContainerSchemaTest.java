package com.liuzhihang.doc.view.service.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Direct regressions for the DTO-only factory; PSI/cache integration is covered separately. */
public class ForkYapiContainerSchemaTest {
    @Test
    public void onlySyntheticArrayLayerIsRemoved() {
        Body element = body("element", "String");
        element.setExample("value");
        element.setRequired(true);
        Body wrapper = body("element", "Item");
        wrapper.setCollection(true);
        wrapper.setSyntheticContainerNode(true);
        wrapper.setChildList(List.of(element, body("key", "int"), body("value", "boolean")));
        Body list = body("items", "List<Item>");
        list.setCollection(true);
        list.setChildList(List.of(wrapper));
        JsonObject array = property(schema(list), "items");
        assertEquals("array", array.get("type").getAsString());
        JsonObject item = array.getAsJsonObject("items");
        assertEquals("object", item.get("type").getAsString());
        assertEquals(3, item.getAsJsonObject("properties").size());
        assertEquals("string", property(item, "element").get("type").getAsString());
        assertEquals("value", property(item, "element").get("default").getAsString());
        assertTrue(item.getAsJsonArray("required").toString().contains("element"));
    }

    @Test
    public void mapSyntheticLayersBecomeDynamicValueSchemaAndRealNamesRemain() {
        Body key = body("key", "String");
        key.setMap(true);
        key.setSyntheticContainerNode(true);
        Body value = body("value", "Item");
        value.setMap(true);
        value.setSyntheticContainerNode(true);
        value.setChildList(List.of(body("key", "String"), body("value", "long")));
        Body map = body("mapping", "Map<String, Item>");
        map.setChildList(List.of(key, value));
        JsonObject schema = property(schema(map), "mapping");
        assertEquals("object", schema.get("type").getAsString());
        assertFalse(schema.has("properties") && schema.getAsJsonObject("properties").has("key"));
        JsonObject item = schema.getAsJsonObject("additionalProperties");
        assertNotNull(item);
        assertEquals("string", property(item, "key").get("type").getAsString());
        assertEquals("number", property(item, "value").get("type").getAsString());
    }

    @Test
    public void scalarContentOverrideKeepsElementDefaultAndArrayType() {
        Body list = body("ids", "List<String>");
        list.setCollection(true);
        list.setExample("0");
        JsonObject array = property(schema(list), "ids");
        assertEquals("array", array.get("type").getAsString());
        assertEquals("string", array.getAsJsonObject("items").get("type").getAsString());
        assertEquals("0", array.getAsJsonObject("items").get("default").getAsString());
    }

    private JsonObject schema(Body field) {
        DocView document = new DocView();
        document.setName("Schema");
        document.setDesc("Schema");
        document.setMethod("POST");
        document.setPath("/schema");
        document.setContentType(ContentTypeEnum.JSON);
        document.setHeaderList(List.of());
        document.setReqParamList(List.of());
        document.getReqBody().setChildList(List.of(field));
        document.getRespBody().setChildList(List.of(field));
        YApiSettings settings = new YApiSettings();
        settings.setUrl("http://localhost");
        settings.setProjectId(1L);
        YApiCat category = new YApiCat();
        category.setId(1L);
        return JsonParser.parseString(new YapiSaveFactory().create(settings, category, document).getResBody()).getAsJsonObject();
    }

    private static Body body(String name, String type) {
        Body body = new Body();
        body.setName(name);
        body.setType(type);
        body.setRequired(false);
        body.setDesc(name);
        return body;
    }

    private static JsonObject property(JsonObject schema, String name) {
        return schema.getAsJsonObject("properties").getAsJsonObject(name);
    }
}
