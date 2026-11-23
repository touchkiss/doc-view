package com.liuzhihang.doc.view.utils;

import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.JsonWireType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ParamPsiUtilsCollectionContentTest {

    @Test
    public void appliesStringContentOverrideWithoutCreatingObjectChild() {
        Body body = new Body();
        body.setType("List<Long>");
        body.setCollection(true);

        ParamPsiUtils.applyCollectionContentOverride(
                body,
                JsonWireType.overridden("String", "0", "0")
        );

        assertEquals("List<String>", body.getType());
        assertEquals("0", body.getExample());
        assertTrue(body.isCollection());
        assertTrue(body.getChildList().isEmpty());
    }

    @Test
    public void leavesCollectionUntouchedWhenContentTypeIsNotOverridden() {
        Body body = new Body();
        body.setType("List<Long>");
        body.setCollection(true);

        ParamPsiUtils.applyCollectionContentOverride(
                body,
                JsonWireType.ofJavaType("Long")
        );

        assertEquals("List<Long>", body.getType());
        assertNull(body.getExample());
        assertTrue(body.getChildList().isEmpty());
    }

    @Test
    public void recognizesJacksonNoneWithPsiAndBinaryNestedClassNames() {
        assertTrue(JacksonPsiUtils.isNoneClassName(
                "com.fasterxml.jackson.databind.JsonDeserializer.None"));
        assertTrue(JacksonPsiUtils.isNoneClassName(
                "com.fasterxml.jackson.databind.JsonSerializer$None"));
        assertFalse(JacksonPsiUtils.isNoneClassName(
                "com.example.StringToLongDeserializer"));
    }
}
