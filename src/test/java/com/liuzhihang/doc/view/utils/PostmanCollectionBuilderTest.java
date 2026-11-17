package com.liuzhihang.doc.view.utils;

import com.google.gson.*;
import com.liuzhihang.doc.view.dto.*;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PostmanCollectionBuilderTest {
    @Test public void createsImportableCollectionWithCommentedJsonAndRequestMetadata() {
        DocView doc = new DocView("创建用户"); doc.setMethod("post"); doc.setPath("/users/{id}"); doc.setContentType(ContentTypeEnum.JSON);
        Param id = new Param(); id.setName("id"); id.setExample("42");
        Param query = new Param(); query.setName("search"); query.setExample("a & b"); query.setDesc("搜索条件"); doc.setReqParamList(List.of(id, query));
        Header header = new Header(); header.setName("Authorization"); header.setValue("Bearer token"); header.setDesc("令牌"); doc.setHeaderList(List.of(header));
        doc.setReqBodyExample("{\"name\":\"Alice\"}"); Body field = new Body(); field.setName("name"); field.setDesc("姓名"); doc.getReqBody().setChildList(List.of(field));
        JsonObject collection = JsonParser.parseString(PostmanCollectionBuilder.build(doc)).getAsJsonObject();
        assertEquals("https://schema.getpostman.com/json/collection/v2.1.0/collection.json", collection.getAsJsonObject("info").get("schema").getAsString());
        JsonObject request = collection.getAsJsonArray("item").get(0).getAsJsonObject().getAsJsonObject("request");
        assertEquals("POST", request.get("method").getAsString());
        assertTrue(request.getAsJsonObject("url").get("raw").getAsString().contains("/users/42"));
        assertEquals("a & b", request.getAsJsonObject("url").getAsJsonArray("query").get(0).getAsJsonObject().get("value").getAsString());
        assertEquals("令牌", request.getAsJsonArray("header").get(0).getAsJsonObject().get("description").getAsString());
        assertTrue(request.getAsJsonObject("body").get("raw").getAsString().contains("// 姓名"));
        assertEquals("json", request.getAsJsonObject("body").getAsJsonObject("options").getAsJsonObject("raw").get("language").getAsString());
    }
    @Test public void formAndGetAreRepresentedAsStructuredParameters() {
        DocView doc = new DocView("查询"); doc.setPath("/search"); doc.setMethod("POST"); doc.setContentType(ContentTypeEnum.FORM);
        Param p = new Param(); p.setName("q"); p.setExample("hello & goodbye"); doc.setReqParamList(List.of(p));
        JsonObject req = JsonParser.parseString(PostmanCollectionBuilder.build(doc)).getAsJsonObject().getAsJsonArray("item").get(0).getAsJsonObject().getAsJsonObject("request");
        assertEquals("hello & goodbye", req.getAsJsonObject("body").getAsJsonArray("urlencoded").get(0).getAsJsonObject().get("value").getAsString());
        doc.setMethod("GET"); req = JsonParser.parseString(PostmanCollectionBuilder.build(doc)).getAsJsonObject().getAsJsonArray("item").get(0).getAsJsonObject().getAsJsonObject("request");
        assertFalse(req.has("body")); assertEquals("q", req.getAsJsonObject("url").getAsJsonArray("query").get(0).getAsJsonObject().get("key").getAsString());
    }
}
