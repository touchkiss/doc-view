package com.liuzhihang.doc.view.service.impl;

import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class YApiInterfaceUrlResolverTest {

    @Test
    public void buildsDetailUrlFromFoundInterfaceId() {
        YapiSave save = save("POST", "/Dubbo/getOrder");
        StubFacade facade = new StubFacade(Optional.of(4396L), null);

        String url = YApiInterfaceUrlResolver.resolve(facade, save);

        assertEquals("http://yapi.example/project/299/interface/api/4396", url);
        assertEquals("POST", facade.method);
        assertEquals("/Dubbo/getOrder", facade.path);
    }

    @Test
    public void fallsBackToCategoryUrlWhenInterfaceIsNotFound() {
        String url = YApiInterfaceUrlResolver.resolve(
                new StubFacade(Optional.empty(), null), save("GET", "/orders"));
        assertEquals("http://yapi.example/project/299/interface/api/cat_1376", url);
    }

    @Test
    public void fallsBackToCategoryUrlWhenLookupFails() {
        String url = YApiInterfaceUrlResolver.resolve(
                new StubFacade(Optional.empty(), new Exception("network")), save("GET", "/orders"));
        assertEquals("http://yapi.example/project/299/interface/api/cat_1376", url);
    }

    private static YapiSave save(String method, String path) {
        YapiSave save = new YapiSave();
        save.setYapiUrl("http://yapi.example");
        save.setProjectId(299L);
        save.setToken("token");
        save.setCatId(1376L);
        save.setMethod(method);
        save.setPath(path);
        return save;
    }

    private static final class StubFacade implements YApiFacadeService {
        private final Optional<Long> result;
        private final Exception failure;
        private String method;
        private String path;

        private StubFacade(Optional<Long> result, Exception failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        public Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                              Long catId, String method, String path) throws Exception {
            this.method = method;
            this.path = path;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public void save(YapiSave dto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<YApiCat> getCatMenu(String url, Long projectId, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public YApiCat addCat(YApiCat cat) {
            throw new UnsupportedOperationException();
        }
    }
}
