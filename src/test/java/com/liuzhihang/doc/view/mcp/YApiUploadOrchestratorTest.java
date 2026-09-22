package com.liuzhihang.doc.view.mcp;

import com.intellij.openapi.project.Project;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YApiUploadOrchestratorTest {

    @Test
    public void returnsCreatedResultForNewInterface() {
        FakeFacade facade = new FakeFacade();

        YApiUploadOrchestrator.UploadItemResult result = upload(facade, document("GET", "/orders")).get(0);

        assertEquals("GET /orders", result.getReference());
        assertEquals("created", result.getStatus());
        assertEquals("http://yapi.example/project/299/interface/api/cat_1376", result.getYapiUrl());
        assertNull(result.getErrorCode());
        assertNull(result.getMessage());
        assertNull(facade.saved.get(0).getId());
    }

    @Test
    public void returnsUpdatedResultAndSendsExistingId() {
        FakeFacade facade = new FakeFacade();
        facade.interfaceIds.put("PUT /orders/1", 4396L);

        YApiUploadOrchestrator.UploadItemResult result = upload(facade, document("PUT", "/orders/1")).get(0);

        assertEquals("updated", result.getStatus());
        assertEquals("http://yapi.example/project/299/interface/api/4396", result.getYapiUrl());
        assertEquals("4396", facade.saved.get(0).getId());
    }

    @Test
    public void continuesAfterOneMethodFails() {
        FakeFacade facade = new FakeFacade();
        facade.saveFailures.put("/broken", new Exception("save failed"));

        List<YApiUploadOrchestrator.UploadItemResult> results = upload(facade,
                document("POST", "/broken"), document("GET", "/healthy"));

        assertEquals("failed", results.get(0).getStatus());
        assertEquals("YAPI_REQUEST_FAILED", results.get(0).getErrorCode());
        assertEquals("created", results.get(1).getStatus());
        assertEquals(List.of("/broken", "/healthy"), facade.savedPaths);
    }

    @Test
    public void representsEveryMethodFailureAndPreservesInputOrder() {
        FakeFacade facade = new FakeFacade();
        facade.saveFailures.put("/first", new Exception("first failed"));
        facade.saveFailures.put("/second", new Exception("second failed"));

        List<YApiUploadOrchestrator.UploadItemResult> results = upload(facade,
                document("POST", "/first"), document("DELETE", "/second"));

        assertEquals(List.of("POST /first", "DELETE /second"), results.stream()
                .map(YApiUploadOrchestrator.UploadItemResult::getReference).toList());
        assertEquals(List.of("failed", "failed"), results.stream()
                .map(YApiUploadOrchestrator.UploadItemResult::getStatus).toList());
        assertEquals(List.of("YAPI_REQUEST_FAILED", "YAPI_REQUEST_FAILED"), results.stream()
                .map(YApiUploadOrchestrator.UploadItemResult::getErrorCode).toList());
    }

    @Test
    public void doesNotSaveWhenExistingInterfaceLookupFails() {
        FakeFacade facade = new FakeFacade();
        facade.lookupFailures.put("GET /unavailable", new Exception("network token-value unavailable"));

        YApiUploadOrchestrator.UploadItemResult result = upload(facade, document("GET", "/unavailable")).get(0);

        assertEquals("failed", result.getStatus());
        assertEquals("YAPI_REQUEST_FAILED", result.getErrorCode());
        assertTrue(result.getMessage().contains("network"));
        assertFalse(result.getMessage().contains("token-value"));
        assertEquals(0, facade.saved.size());
    }

    @Test
    public void returnsCreatedResultWhenDetailUrlLookupFailsAfterSave() {
        FakeFacade facade = new FakeFacade();
        facade.detailLookupFailures.put("GET /orders", new Exception("network token-value unavailable"));

        YApiUploadOrchestrator.UploadItemResult result = upload(facade, document("GET", "/orders")).get(0);

        assertEquals("created", result.getStatus());
        assertEquals("http://yapi.example/project/299/interface/api/cat_1376", result.getYapiUrl());
        assertNull(result.getErrorCode());
        assertNull(result.getMessage());
        assertEquals(1, facade.saved.size());
    }

    @Test
    public void continuesAfterDocumentPreparationFails() {
        FakeFacade facade = new FakeFacade();

        List<YApiUploadOrchestrator.UploadItemResult> results = upload(facade,
                document("Dubbo", "/ignored"), document("GET", "/healthy"));

        assertEquals("failed", results.get(0).getStatus());
        assertEquals("DOC_GENERATION_FAILED", results.get(0).getErrorCode());
        assertTrue(results.get(0).getMessage().contains("missing method"));
        assertEquals("created", results.get(1).getStatus());
        assertEquals(List.of("/healthy"), facade.savedPaths);
    }

    @Test
    public void preservesInvalidYApiResponseClassification() {
        FakeFacade facade = new FakeFacade();
        facade.saveFailures.put("/invalid-response",
                new McpException(McpException.Code.YAPI_RESPONSE_INVALID, "响应 JSON 无效"));

        YApiUploadOrchestrator.UploadItemResult result = upload(facade,
                document("GET", "/invalid-response")).get(0);

        assertEquals("failed", result.getStatus());
        assertEquals("YAPI_RESPONSE_INVALID", result.getErrorCode());
        assertEquals("响应 JSON 无效", result.getMessage());
    }

    @Test
    public void createsSaveDtoInsideReadActionBeforeFacadeCalls() {
        AtomicBoolean inReadAction = new AtomicBoolean();
        List<String> calls = new ArrayList<>();
        FakeFacade facade = new FakeFacade();
        facade.onFacadeCall = call -> {
            assertFalse("YApi facade call must not run in a read action", inReadAction.get());
            calls.add(call);
        };

        YApiUploadOrchestrator.UploadItemResult result = new YApiUploadOrchestrator(facade, project -> settings(),
                (settings, category, document) -> {
                    assertTrue("SaveMapper must run in a read action", inReadAction.get());
                    calls.add("mapper");
                    return save(settings, category, document);
                }, computation -> {
                    inReadAction.set(true);
                    try {
                        return computation.get();
                    } finally {
                        inReadAction.set(false);
                    }
                }).upload(null, List.of(document("GET", "/orders"))).get(0);

        assertEquals("created", result.getStatus());
        assertEquals("mapper", calls.get(0));
        assertEquals(List.of("mapper", "getCatMenu", "addCat", "findInterfaceId", "save", "findInterfaceId"), calls);
    }

    private static List<YApiUploadOrchestrator.UploadItemResult> upload(FakeFacade facade, DocView... documents) {
        return new YApiUploadOrchestrator(facade, project -> settings(),
                (settings, category, document) -> save(settings, category, document),
                computation -> computation.get())
                .upload(null, List.of(documents));
    }

    private static YApiSettings settings() {
        YApiSettings settings = new YApiSettings();
        settings.setUrl("http://yapi.example");
        settings.setProjectId(299L);
        settings.setToken("token-value");
        return settings;
    }

    private static DocView document(String method, String path) {
        DocView document = new DocView();
        document.setDocTitle("Orders");
        document.setName(path);
        document.setDesc("description");
        document.setMethod(method);
        document.setPath(path);
        document.setContentType(ContentTypeEnum.JSON);
        document.setHeaderList(List.of());
        document.setReqParamList(List.of());
        document.setReqBodyExample("{}");
        document.setRespExample("{}");
        return document;
    }

    private static YapiSave save(YApiSettings settings, YApiCat category, DocView document) {
        if ("Dubbo".equals(document.getMethod())) {
            throw new IllegalArgumentException("missing method");
        }
        YapiSave save = new YapiSave();
        save.setYapiUrl(settings.getUrl());
        save.setToken(settings.getToken());
        save.setProjectId(settings.getProjectId());
        save.setCatId(category.getId());
        save.setMethod(document.getMethod());
        save.setPath(document.getPath());
        return save;
    }

    private static final class FakeFacade implements YApiFacadeService {
        private final Map<String, Long> interfaceIds = new HashMap<>();
        private final Map<String, Exception> lookupFailures = new HashMap<>();
        private final Map<String, Exception> detailLookupFailures = new HashMap<>();
        private final Map<String, Exception> saveFailures = new HashMap<>();
        private final List<YapiSave> saved = new ArrayList<>();
        private final List<String> savedPaths = new ArrayList<>();
        private final Map<String, Integer> lookupCounts = new HashMap<>();
        private Consumer<String> onFacadeCall = call -> { };

        @Override
        public void save(YapiSave save) throws Exception {
            onFacadeCall.accept("save");
            saved.add(save);
            savedPaths.add(save.getPath());
            Exception failure = saveFailures.get(save.getPath());
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public List<YApiCat> getCatMenu(String yapiUrl, Long projectId, String token) {
            onFacadeCall.accept("getCatMenu");
            return List.of();
        }

        @Override
        public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                              Long catId, String method, String path) throws Exception {
            onFacadeCall.accept("findInterfaceId");
            String key = method + " " + path;
            int lookupCount = lookupCounts.merge(key, 1, Integer::sum);
            Exception failure = lookupFailures.get(key);
            if (failure != null) {
                throw failure;
            }
            if (lookupCount > 1) {
                Exception detailFailure = detailLookupFailures.get(key);
                if (detailFailure != null) {
                    throw detailFailure;
                }
            }
            return Optional.ofNullable(interfaceIds.get(key));
        }

        @Override
        public YApiCat addCat(YApiCat cat) {
            onFacadeCall.accept("addCat");
            cat.setId(1376L);
            return cat;
        }
    }
}
