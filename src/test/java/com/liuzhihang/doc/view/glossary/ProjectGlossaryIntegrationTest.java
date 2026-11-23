package com.liuzhihang.doc.view.glossary;

import com.alibaba.fastjson2.JSON;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.service.gpt.GptService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ProjectGlossaryIntegrationTest extends LightJavaCodeInsightFixtureTestCase {
    public void testCollectsIdentifiersAndRequestsModelWithoutSendingSourceBodies() throws Exception {
        myFixture.addFileToProject("billing/TrecPayoutService.java", """
                package billing;
                // SENTINEL_COMMENT
                class TrecPayoutService {
                    String settleQuoteId() { return "SENTINEL_VALUE"; }
                }
                """);
        myFixture.addFileToProject("billing/VatRule.kt", "class VatRule { fun calculateVat() = 0 }");

        var evidence = AnalyzeProjectTermsAction.scan(getProject(), new EmptyProgressIndicator());
        assertTrue(evidence.terms().containsAll(List.of("trec", "settlequoteid", "vatrule")));
        assertFalse(evidence.prompt().contains("SENTINEL"));

        String answer = "[{\"term\":\"TREC\",\"meaning\":\"交易对账\"}]";
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var response = JSON.toJSONString(Map.of("choices", List.of(
                        Map.of("message", Map.of("content", answer))))).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        var app = ApplicationManager.getApplication();
        var config = app.getService(EasyDocConfigComponent.class).getState();
        String oldUrl = config.getOpenAiApiUrl();
        String oldKey = config.getOpenAiApiKey();
        String oldModel = config.getOpenAiModel();
        try {
            config.setOpenAiApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
            config.setOpenAiApiKey("test-key");
            config.setOpenAiModel("test-model");
            String response = app.getService(GptService.class).chat(Consts.OPENAI_GPT, evidence.prompt());
            assertEquals(Map.of("trec", "交易对账"), ProjectGlossary.parse(response, evidence.terms()));
            assertEquals("Bearer test-key", authorization.get());
            var body = JSON.parseObject(request.get());
            assertEquals("test-model", body.getString("model"));
            assertEquals(evidence.prompt(), body.getJSONArray("messages").getJSONObject(0).getString("content"));
        } finally {
            config.setOpenAiApiUrl(oldUrl);
            config.setOpenAiApiKey(oldKey);
            config.setOpenAiModel(oldModel);
            server.stop(0);
        }
    }
}
