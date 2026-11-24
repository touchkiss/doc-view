package com.liuzhihang.doc.view.mcp;

import com.liuzhihang.doc.view.integration.YApiUploadOrchestrator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.liuzhihang.doc.view.service.impl.SpringDocViewServiceImpl;
import com.liuzhihang.doc.view.testsupport.AnnotationStubs;
import com.intellij.util.xmlb.XmlSerializer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Public MCP upload flow must use the same persisted project token as the settings page. */
public final class McpProjectCredentialTest extends LightJavaCodeInsightFixtureTestCase {
    public void testPublicUploadWorkflowReadsProjectConfigurationWithoutCredentialService() throws Exception {
        AnnotationStubs.installSpringWeb(myFixture);
        var owner = myFixture.addClass("""
                package credentials;
                @org.springframework.web.bind.annotation.RestController public class XmlCredentialController {
                    @org.springframework.web.bind.annotation.GetMapping("/credentials") public void endpoint() {}
                }
                """);
        String originalSource = owner.getContainingFile().getText();
        var document = ApplicationManager.getApplication().runReadAction((Computable<com.liuzhihang.doc.view.dto.DocView>) () ->
                new SpringDocViewServiceImpl().buildClassMethodDoc(owner, owner.getMethods()[0]));
        YApiSettings configured = YApiSettings.getInstance(getProject());
        var originalSettings = XmlSerializer.serialize(configured.getState());
        String token = "config-only-&+/=汉字";
        configured.setUrl("http://config-yapi.example");
        configured.setProjectId(299L);
        configured.setToken(token);
        AtomicInteger calls = new AtomicInteger();
        YApiFacadeService facade = new YApiFacadeService() {
            private void checkToken(String received) {
                assertFalse("Network operations remain outside PSI read access", ApplicationManager.getApplication().isReadAccessAllowed());
                assertEquals(token, received);
                calls.incrementAndGet();
            }
            @Override public List<YApiCat> getCatMenu(String url, Long projectId, String received) {
                checkToken(received);
                YApiCat category = new YApiCat(); category.setId(17L); category.setName("XmlCredentialController");
                return List.of(category);
            }
            @Override public Optional<Long> findInterfaceId(String url, Long projectId, String received,
                    Long catId, String method, String path) {
                checkToken(received);
                assertEquals(299L, projectId.longValue()); assertEquals("GET", method); assertEquals("/credentials", path);
                return Optional.of(4396L);
            }
            @Override public void save(YapiSave save) { checkToken(save.getToken()); assertEquals("4396", save.getId()); }
            @Override public YApiCat addCat(YApiCat category) { throw new AssertionError("Existing category must be reused"); }
        };
        try {
            var results = ApplicationManager.getApplication().executeOnPooledThread(() ->
                    new YApiUploadOrchestrator(facade).upload(getProject(), List.of(document))).get(10, TimeUnit.SECONDS);
            assertEquals("updated", results.getFirst().getStatus());
            assertEquals("http://config-yapi.example/project/299/interface/api/4396", results.getFirst().getYapiUrl());
            assertEquals(3, calls.get());
            assertEquals(token, configured.getToken());
            assertEquals(originalSource, owner.getContainingFile().getText());
        } finally {
            configured.loadState(XmlSerializer.deserialize(originalSettings, YApiSettings.class));
        }
    }
}
