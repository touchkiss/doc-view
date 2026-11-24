package com.liuzhihang.doc.view.contract;

import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.render.Json5ExampleRenderer;
import com.liuzhihang.doc.view.render.MarkdownTables;
import com.liuzhihang.doc.view.service.impl.YApiServiceImpl;
import com.liuzhihang.doc.view.service.impl.YapiSaveFactory;
import junit.framework.TestCase;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CollectionDependencyContractTest extends TestCase {

    public void testRenderingAndYApiDoNotLinkCommonsCollections() throws Exception {
        for (Class<?> type : List.of(DocViewData.class, MarkdownTables.class, Json5ExampleRenderer.class,
                YApiServiceImpl.class, YapiSaveFactory.class)) {
            try (InputStream stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                assertNotNull(stream);
                String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(type.getName() + " still links Commons Collections",
                        bytecode.contains("org/apache/commons/collections/"));
            }
        }
    }

    public void testNullAndEmptyMarkdownParametersRemainEmpty() {
        for (boolean separate : List.of(false, true)) {
            assertEquals("", DocViewData.bodyMarkdown(null, separate));
            assertEquals("", DocViewData.bodyMarkdown(List.of(), separate));
        }
        assertEquals("", MarkdownTables.paramMarkdown(null));
        assertEquals("", MarkdownTables.paramMarkdown(List.of()));
    }

    public void testNullAndEmptyYApiParametersRemainEmpty() throws Exception {
        YapiSaveFactory service = new YapiSaveFactory();
        for (String name : List.of("buildReqQuery", "buildReqHeaders")) {
            Method method = YapiSaveFactory.class.getDeclaredMethod(name, List.class);
            method.setAccessible(true);
            assertEquals(List.of(), method.invoke(service, new Object[]{null}));
            assertEquals(List.of(), method.invoke(service, List.of()));
        }
    }
}
