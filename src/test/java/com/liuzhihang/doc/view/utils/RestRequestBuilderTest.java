package com.liuzhihang.doc.view.utils;

import com.liuzhihang.doc.view.dto.*;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class RestRequestBuilderTest {
    @Test public void json5IncludesDescriptionsAndJsonRemainsUncommented() {
        DocView doc = new DocView("创建用户");
        doc.setMethod("POST"); doc.setPath("/users/{id}"); doc.setContentType(ContentTypeEnum.JSON);
        Param id = new Param(); id.setName("id"); id.setExample("42");
        Param q = new Param(); q.setName("q"); q.setExample("abc"); doc.setReqParamList(List.of(id, q));
        Header header = new Header(); header.setName("Authorization"); header.setValue("Bearer token"); doc.setHeaderList(List.of(header));
        doc.setReqBodyExample("{\"name\":\"Alice\"}");
        Body field = new Body(); field.setName("name"); field.setDesc("姓名"); doc.getReqBody().setChildList(List.of(field));
        String json = RestRequestBuilder.build(doc, false);
        String json5 = RestRequestBuilder.build(doc, true);
        assertTrue(json.contains("POST {{host}}/users/42?q=abc"));
        assertTrue(json.contains("Authorization: Bearer token"));
        assertTrue(json.contains("Content-Type: application/json"));
        assertFalse(json.contains("// 姓名"));
        assertTrue(json5.contains("// 姓名"));
    }
    @Test public void formParametersAreBodyForPostAndQueryForGet() {
        DocView doc = new DocView("查询"); doc.setPath("/search"); doc.setMethod("POST"); doc.setContentType(ContentTypeEnum.FORM);
        Param p = new Param(); p.setName("term"); p.setExample("word"); doc.setReqParamList(List.of(p));
        assertTrue(RestRequestBuilder.build(doc, false).contains("\n\nterm=word"));
        doc.setMethod("GET"); assertTrue(RestRequestBuilder.build(doc, false).contains("GET {{host}}/search?term=word"));
    }
}
