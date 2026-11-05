package com.liuzhihang.doc.view.integration.impl;

import com.liuzhihang.doc.view.integration.YApiRemoteException;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YApiFacadeServiceImplTest {
    @Test
    public void incompleteLastPageMustNotBeReportedAsInterfaceAbsence() throws Exception {
        String fullPage = com.google.gson.JsonParser.parseString(fullNonMatchingPage()).getAsJsonObject().get("data").toString();
        for (String data : List.of("{\"total\":1,\"count\":101,\"list\":" + fullPage + "}",
                "{\"total\":0,\"count\":1,\"list\":[]}")) {
            YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":" + data + "}");
            var failure = org.junit.Assert.assertThrows(YApiRemoteException.class, () ->
                    service.findInterfaceId("http://yapi.example", 299L, "token", 1376L, "GET", "/missing"));
            assertEquals(YApiRemoteException.Kind.RESPONSE_INVALID, failure.getKind());
        }
    }


    @Test
    public void enumeratesRequestedCategoryAndAcceptsRowsWithoutRepeatedCatId() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            var uri = new org.apache.http.client.utils.URIBuilder(url);
            assertEquals("/api/interface/list_cat", uri.getPath());
            var params = uri.getQueryParams();
            assertEquals("1376", params.stream().filter(v -> v.getName().equals("catid")).findFirst().orElseThrow().getValue());
            assertTrue(params.stream().noneMatch(v -> v.getName().equals("project_id")));
            return "{\"errcode\":0,\"data\":{\"count\":1,\"total\":1,\"list\":["
                    + "{\"_id\":4396,\"path\":\"/target\",\"method\":\"GET\"}]}}";
        });
        assertEquals(Optional.of(4396L), service.findInterfaceId(
                "http://yapi.example", 299L, "a&b+c /中文", 1376L, "get", "/target"));
    }

    @Test
    public void followsTotalPagesEvenWhenServerCapsPageBelowRequestedLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            int page = calls.incrementAndGet();
            return "{\"errcode\":0,\"data\":{\"count\":2,\"total\":2,\"list\":["
                    + "{\"_id\":" + page + ",\"path\":\"/" + (page == 1 ? "other" : "target")
                    + "\",\"method\":\"GET\"}]}}";
        });
        assertEquals(Optional.of(2L), service.findInterfaceId(
                "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
        assertEquals(2, calls.get());
    }

    @Test
    public void capturesValidIdReturnedBySaveWithoutAnotherLookup() throws Exception {
        for (String data : List.of("{\"_id\":4396}", "{\"id\":\"4396\"}", "[{\"_id\":4396}]")) {
            YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(
                    url -> { throw new AssertionError("Save response already supplies the interface ID"); },
                    (url, body) -> "{\"errcode\":0,\"data\":" + data + "}");
            var input = save(null);
            service.save(input);
            assertEquals("4396", input.getId());
            input.setProjectId(299L);
            assertEquals("http://yapi.example/project/299/interface/api/4396",
                    com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolver.resolve(service, input));
        }
    }

    @Test
    public void invalidOrAmbiguousSaveDataStillUsesCategoryLookup() throws Exception {
        for (String data : List.of("1", "{\"_id\":-3}", "{\"_id\":2.5}",
                "{\"id\":\"cat_1376\"}", "[{\"_id\":1},{\"_id\":2}]",
                "{\"_id\":123,\"catid\":999}", "{\"_id\":123,\"path\":\"/wrong\"}")) {
            AtomicInteger lookupCalls = new AtomicInteger();
            YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
                lookupCalls.incrementAndGet();
                return "{\"errcode\":0,\"data\":[{\"_id\":4396,\"method\":\"GET\",\"path\":\"/target\"}]}";
            }, (url, body) -> "{\"errcode\":0,\"data\":" + data + "}");
            var input = save(null);
            input.setProjectId(299L);
            input.setCatId(1376L);
            input.setMethod("GET");
            input.setPath("/target");
            service.save(input);
            assertEquals(null, input.getId());
            assertEquals("http://yapi.example/project/299/interface/api/4396",
                    com.liuzhihang.doc.view.service.impl.YApiInterfaceUrlResolver.resolve(service, input));
            assertEquals(1, lookupCalls.get());
        }
    }

    @Test
    public void overlappingPageIsRejectedEvenWhenItAlsoContainsNewIds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            int page = calls.incrementAndGet();
            return "{\"errcode\":0,\"data\":{\"total\":3,\"list\":["
                    + "{\"_id\":1,\"catid\":1376,\"path\":\"/one\",\"method\":\"GET\"},"
                    + "{\"_id\":" + (page + 1) + ",\"catid\":1376,\"path\":\"/other\",\"method\":\"GET\"}]}}";
        });
        try {
            service.findInterfaceId("http://yapi.example", 299L, "token", 1376L, "GET", "/target");
            throw new AssertionError("Overlapping pages must fail rather than risk inaccurate absence");
        } catch (YApiRemoteException expected) {
            assertEquals(YApiRemoteException.Kind.RESPONSE_INVALID, expected.getKind());
            assertEquals(2, calls.get());
        }
    }

    @Test(expected = YApiRemoteException.class)
    public void rejectsIncompleteInterfaceRowsInsteadOfAssumingAnInterfaceDoesNotExist() throws Exception {
        new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[{}]}")
                .findInterfaceId("http://yapi.example", 299L, "token", 1L, "GET", "/target");
    }

    @Test
    public void repeatedFullPageStopsWithInvalidResponseInsteadOfLooping() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url ->
                calls.incrementAndGet() <= 2 ? fullNonMatchingPage() : "{\"errcode\":0,\"data\":[]}");
        try {
            service.findInterfaceId("http://yapi.example", 299L, "token", 1L, "GET", "/target");
            throw new AssertionError("Repeated pages must not be treated as successful absence");
        } catch (YApiRemoteException invalid) {
            assertEquals(YApiRemoteException.Kind.RESPONSE_INVALID, invalid.getKind());
            assertEquals(2, calls.get());
        }
    }


    @Test
    public void preservesSpecialTokenCharactersForCategoryAndInterfaceLookup() throws Exception {
        List<String> urls = new ArrayList<>();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            urls.add(url);
            return "{\"errcode\":0,\"data\":[]}";
        });
        String token = "a&b+c /中文";
        service.getCatMenu("http://yapi.example", 299L, token);
        service.findInterfaceId("http://yapi.example", 299L, token, 1L, "GET", "/target");
        for (String url : urls) {
            var params = new org.apache.http.client.utils.URIBuilder(url).getQueryParams();
            assertEquals(token, params.stream().filter(v -> v.getName().equals("token")).findFirst().orElseThrow().getValue());
        }
    }

    @Test(expected = com.intellij.openapi.progress.ProcessCanceledException.class)
    public void neverConvertsCancellationToRemoteFailure() throws Exception {
        new YApiFacadeServiceImpl(url -> { throw new com.intellij.openapi.progress.ProcessCanceledException(); })
                .getCatMenu("http://yapi.example", 299L, "token");
    }


    @Test
    public void findsMatchingInterfaceOnFirstPage() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            assertTrue(url.contains("/api/interface/list_cat?"));
            assertTrue(url.contains("catid=1376"));
            assertTrue(url.contains("token=token-value"));
            assertTrue(url.contains("page=1"));
            assertTrue(url.contains("limit=100"));
            return "{\"errcode\":0,\"errmsg\":\"成功\",\"data\":["
                    + "{\"_id\":4396,\"catid\":1376,\"path\":\"/api/group/list\",\"method\":\"GET\"}]}";
        });

        Optional<Long> result = service.findInterfaceId(
                "http://yapi.example", 299L, "token-value", 1376L, "get", "/api/group/list");

        assertEquals(Optional.of(4396L), result);
    }

    @Test
    public void continuesToNextPageUntilMatchIsFound() throws Exception {
        List<String> requestedUrls = new ArrayList<>();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            requestedUrls.add(url);
            return url.contains("page=1") ? fullNonMatchingPage() : matchingPage();
        });

        assertEquals(Optional.of(4396L), service.findInterfaceId(
                "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
        assertEquals(2, requestedUrls.size());
        assertTrue(requestedUrls.get(1).contains("page=2"));
    }

    @Test
    public void requiresCategoryMethodAndPathToMatch() throws Exception {
        String data = "{\"errcode\":0,\"data\":["
                + "{\"_id\":1,\"catid\":999,\"path\":\"/target\",\"method\":\"GET\"},"
                + "{\"_id\":2,\"catid\":1376,\"path\":\"/other\",\"method\":\"GET\"},"
                + "{\"_id\":3,\"catid\":1376,\"path\":\"/target\",\"method\":\"POST\"}]}";
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> data);

        assertEquals(Optional.empty(), service.findInterfaceId(
                "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
    }

    @Test
    public void stopsAfterShortPageWithoutMatch() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            requests.incrementAndGet();
            return "{\"errcode\":0,\"data\":[]}";
        });

        assertEquals(Optional.empty(), service.findInterfaceId(
                "http://yapi.example", 299L, "token", 1376L, "GET", "/target"));
        assertEquals(1, requests.get());
    }

    @Test(expected = Exception.class)
    public void rejectsYApiErrorResponse() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(
                url -> "{\"errcode\":400,\"errmsg\":\"failed\"}");
        service.findInterfaceId("http://yapi.example", 299L, "token", 1376L, "GET", "/target");
    }

    @Test
    public void savesNewInterfaceWithoutId() throws Exception {
        List<String> requests = new ArrayList<>();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> {
                    requests.add(url + "\\n" + body);
                    return "{\"errcode\":0}";
                });

        service.save(save(null));

        assertEquals(1, requests.size());
        assertTrue(requests.get(0).startsWith("http://yapi.example/api/interface/save\\n"));
        assertTrue(requests.get(0).contains("\"id\":null"));
    }

    @Test
    public void treatsBlankIdAsNewInterface() throws Exception {
        List<String> requests = new ArrayList<>();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> {
                    requests.add(body);
                    return "{\"errcode\":0}";
                });

        service.save(save("  "));

        assertEquals(1, requests.size());
        assertTrue(requests.get(0).contains("\"id\":null"));
    }

    @Test
    public void savesExistingInterfaceWithItsId() throws Exception {
        List<String> requests = new ArrayList<>();
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> {
                    requests.add(body);
                    return "{\"errcode\":0}";
                });

        service.save(save("4396"));

        assertEquals(1, requests.size());
        assertTrue(requests.get(0).contains("\"id\":\"4396\""));
    }

    @Test(expected = Exception.class)
    public void rejectsBlankSaveResponse() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> " ");

        service.save(save(null));
    }

    @Test(expected = Exception.class)
    public void rejectsFailedSaveResponse() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> "{\"errcode\":400,\"errmsg\":\"failed\"}");

        service.save(save(null));
    }

    @Test
    public void classifiesMalformedSaveResponseAsInvalidResponse() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> "not-json");

        try {
            service.save(save(null));
        } catch (YApiRemoteException exception) {
            assertEquals(YApiRemoteException.Kind.RESPONSE_INVALID, exception.getKind());
            return;
        }
        throw new AssertionError("expected YApiRemoteException");
    }

    @Test
    public void classifiesCategoryTransportFailureAndUsesInjectedRequester() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> {
                    assertTrue(url.endsWith("/api/interface/add_cat"));
                    throw new Exception("connection refused");
                });
        YApiCat category = new YApiCat();
        category.setYapiUrl("http://yapi.example");

        try {
            service.addCat(category);
        } catch (YApiRemoteException exception) {
            assertEquals(YApiRemoteException.Kind.REQUEST_FAILED, exception.getKind());
            assertTrue(exception.getMessage().contains("connection refused"));
            return;
        }
        throw new AssertionError("expected YApiRemoteException");
    }

    @Test
    public void rejectsCategoryResponseWithoutValidId() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> "{\"errcode\":0,\"data\":[]}",
                (url, body) -> "{\"errcode\":0,\"data\":{\"name\":\"Orders\"}}");
        YApiCat category = new YApiCat();
        category.setYapiUrl("http://yapi.example");

        try {
            service.addCat(category);
        } catch (YApiRemoteException exception) {
            assertEquals(YApiRemoteException.Kind.RESPONSE_INVALID, exception.getKind());
            assertTrue(exception.getMessage().contains("id"));
            return;
        }
        throw new AssertionError("expected YApiRemoteException");
    }

    private static com.liuzhihang.doc.view.integration.dto.YapiSave save(String id) {
        com.liuzhihang.doc.view.integration.dto.YapiSave save = new com.liuzhihang.doc.view.integration.dto.YapiSave();
        save.setYapiUrl("http://yapi.example");
        save.setId(id);
        return save;
    }

    private static String fullNonMatchingPage() {
        String entries = LongStream.range(1, 101)
                .mapToObj(id -> "{\"_id\":" + id
                        + ",\"catid\":1376,\"path\":\"/other/" + id
                        + "\",\"method\":\"GET\"}")
                .collect(Collectors.joining(","));
        return "{\"errcode\":0,\"data\":[" + entries + "]}";
    }

    private static String matchingPage() {
        return "{\"errcode\":0,\"data\":["
                + "{\"_id\":4396,\"catid\":1376,\"path\":\"/target\",\"method\":\"GET\"}]}";
    }
}
