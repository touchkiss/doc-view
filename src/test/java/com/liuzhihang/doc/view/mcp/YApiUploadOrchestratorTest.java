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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        facade.lookupFailures.put("GET /unavailable", new Exception("network"));

        YApiUploadOrchestrator.UploadItemResult result = upload(facade, document("GET", "/unavailable")).get(0);

        assertEquals("failed", result.getStatus());
        assertEquals("YAPI_REQUEST_FAILED", result.getErrorCode());
        assertEquals(0, facade.saved.size());
    }

    @Test
    public void continuesAfterDocumentPreparationFails() {
        FakeFacade facade = new FakeFacade();

        List<YApiUploadOrchestrator.UploadItemResult> results = upload(facade,
                document("Dubbo", "/ignored"), document("GET", "/healthy"));

        assertEquals("failed", results.get(0).getStatus());
        assertEquals("created", results.get(1).getStatus());
        assertEquals(List.of("/healthy"), facade.savedPaths);
    }

    private static List<YApiUploadOrchestrator.UploadItemResult> upload(FakeFacade facade, DocView... documents) {
        return new YApiUploadOrchestrator(facade, project -> settings(),
                (settings, category, document) -> save(settings, category, document))
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
        private final Map<String, Exception> saveFailures = new HashMap<>();
        private final List<YapiSave> saved = new ArrayList<>();
        private final List<String> savedPaths = new ArrayList<>();

        @Override
        public void save(YapiSave save) throws Exception {
            saved.add(save);
            savedPaths.add(save.getPath());
            Exception failure = saveFailures.get(save.getPath());
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public List<YApiCat> getCatMenu(String yapiUrl, Long projectId, String token) {
            return List.of();
        }

        @Override
        public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                              Long catId, String method, String path) throws Exception {
            String key = method + " " + path;
            Exception failure = lookupFailures.get(key);
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(interfaceIds.get(key));
        }

        @Override
        public YApiCat addCat(YApiCat cat) {
            cat.setId(1376L);
            return cat;
        }
    }
}
