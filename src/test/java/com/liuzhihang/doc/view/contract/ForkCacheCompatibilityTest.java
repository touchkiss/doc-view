package com.liuzhihang.doc.view.contract;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.service.DtoSchemaCacheService;

public final class ForkCacheCompatibilityTest extends LightJavaCodeInsightFixtureTestCase {
    public void testSnakeCaseChangeInvalidatesCachedSchemaAndSyntheticFlagSurvivesCopy() {
        Settings settings = Settings.getInstance(getProject());
        var previous = settings.getFieldNameCaseType();
        DtoSchemaCacheService cache = DtoSchemaCacheService.getInstance(getProject());
        try {
            settings.setFieldNameCaseType(false);
            var key = cache.key("fork-response", "java.util.List<example.Order>");
            Body root = new Body();
            Body wrapper = new Body();
            wrapper.setName("element");
            wrapper.setSyntheticContainerNode(true);
            root.getChildList().add(wrapper);
            cache.putBody(key, root);
            Body cached = cache.getBody(key);
            assertNotNull(cached);
            assertTrue(cached.getChildList().getFirst().isSyntheticContainerNode());
            assertNull(cached.getChildList().getFirst().getPsiElement());
            settings.setFieldNameCaseType(true);
            assertNull("snake_case changes field names and must invalidate cached schemas", cache.getBody(key));
        } finally {
            settings.setFieldNameCaseType(previous);
            cache.clear();
        }
    }
}
