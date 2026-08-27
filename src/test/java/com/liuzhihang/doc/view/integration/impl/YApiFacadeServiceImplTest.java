package com.liuzhihang.doc.view.integration.impl;

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
    public void findsMatchingInterfaceOnFirstPage() throws Exception {
        YApiFacadeServiceImpl service = new YApiFacadeServiceImpl(url -> {
            assertTrue(url.contains("project_id=299"));
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

    private static String fullNonMatchingPage() {
        String entries = LongStream.range(0, 100)
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
