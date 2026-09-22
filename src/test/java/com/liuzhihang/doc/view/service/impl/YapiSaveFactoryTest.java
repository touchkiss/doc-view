package com.liuzhihang.doc.view.service.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YapiSaveFactoryTest {

    @Test
    public void mapsHttpDocumentToCompleteYApiPayload() {
        YapiSave save = new YapiSaveFactory().create(settings(), category(), httpDocument());

        assertEquals("http://yapi.example", save.getYapiUrl());
        assertEquals(Long.valueOf(299), save.getProjectId());
        assertEquals(Long.valueOf(1376), save.getCatId());
        assertEquals("POST", save.getMethod());
        assertEquals("/orders", save.getPath());
        assertEquals("json", save.getReqBodyType());
        assertTrue(save.isReqBodyIsJsonSchema());
        assertEquals("X-Trace", save.getReqHeaders().get(0).getName());
        assertEquals("1", save.getReqHeaders().get(0).getRequired());
        assertEquals("page", save.getReqQuery().get(0).getName());
        assertEquals("0", save.getReqQuery().get(0).getRequired());
        assertTrue(save.getMarkdown().contains("创建订单"));
        assertTrue(save.getDesc().contains("<p>创建订单</p>"));

        JsonObject requestSchema = JsonParser.parseString(save.getReqBodyOther()).getAsJsonObject();
        assertEquals("string", requestSchema.getAsJsonObject("properties")
                .getAsJsonObject("name").get("type").getAsString());
        JsonObject responseSchema = JsonParser.parseString(save.getResBody()).getAsJsonObject();
        assertEquals("number", responseSchema.getAsJsonObject("properties")
                .getAsJsonObject("id").get("type").getAsString());
    }

    private static YApiSettings settings() {
        YApiSettings settings = new YApiSettings();
        settings.setUrl("http://yapi.example");
        settings.setProjectId(299L);
        settings.setToken("token-value");
        return settings;
    }

    private static YApiCat category() {
        YApiCat category = new YApiCat();
        category.setId(1376L);
        return category;
    }

    private static DocView httpDocument() {
        Header header = new Header();
        header.setName("X-Trace");
        header.setValue("trace-id");
        header.setDesc("追踪标识");
        header.setRequired(true);
        Param query = new Param();
        query.setName("page");
        query.setType("Integer");
        query.setExample("1");
        query.setDesc("页码");
        query.setRequired(false);
        DocView document = new DocView();
        document.setDocTitle("订单");
        document.setName("创建订单");
        document.setDesc("创建订单");
        document.setMethod("POST");
        document.setPath("/orders");
        document.setContentType(ContentTypeEnum.JSON);
        document.setHeaderList(List.of(header));
        document.setReqParamList(List.of(query));
        document.setReqBodyExample("{name: 'book'}");
        document.setRespExample("{id: 1}");
        document.getReqBody().setChildList(List.of(body("name", "String", true)));
        document.getRespBody().setChildList(List.of(body("id", "Long", true)));
        return document;
    }

    private static Body body(String name, String type, boolean required) {
        Body body = new Body();
        body.setName(name);
        body.setType(type);
        body.setRequired(required);
        body.setDesc(name + " 描述");
        body.setExample("sample");
        return body;
    }
}
