package com.liuzhihang.doc.view.service.gpt;

import com.alibaba.fastjson2.JSON;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class OpenAiCommentGenerationTest {
    @Test
    public void postsToFullEndpointAndExtractsCommentFromWrappedResponse() throws Exception {
        String content = "生成结果如下：\n```java\n/** 查询明细。 */\n```\n";
        var requestPath = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requestPath.set(exchange.getRequestURI().getPath());
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = JSON.toJSONString(Map.of("choices", List.of(
                        Map.of("message", Map.of("content", content))))).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var config = new EasyDocConfig();
            config.setOpenAiApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions/");
            var gpt = new GptService();
            gpt.init(config);
            assertEquals("/** 查询明细。 */", gpt.requestComment(new GptService.CommentInput(Consts.OPENAI_GPT, "demo")));
            assertEquals("/v1/chat/completions", requestPath.get());
            assertNull(auth.get());
        } finally {
            server.stop(0);
        }
    }
    @Test
    public void reportsHttpStatusWithoutEchoingServerResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                byte[] body = "private server detail".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var config = new EasyDocConfig();
            config.setOpenAiApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var service = new GptService();
            service.init(config);
            var failure = assertThrows(ModelRequestException.class, () -> service.chat(Consts.OPENAI_GPT, "demo"));
            assertTrue(failure.getMessage().contains("HTTP 401"));
            assertFalse(failure.getMessage().contains("private server detail"));
        } finally {
            server.stop(0);
        }
    }

}
