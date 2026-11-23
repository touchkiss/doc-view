package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.utils.CustomPsiCommentUtils;

import com.star.easydoc.common.Consts;
import com.star.easydoc.config.EasyDocConfigComponent;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SourceDescriptionServiceTest extends LightJavaCodeInsightFixtureTestCase {
    public void testMethodAndParameterCanBeAppliedIndependently() {
        var type = myFixture.addClass("""
                class Orders {
                    /**
                     * Old summary.
                     * @param order Old order.
                     * @param region Keep this region.
                     * @return Keep this result.
                     */
                    String load(String order, int region) { return order; }
                }
                """);
        var method = type.getMethods()[0];
        var doc = new DocView();
        doc.setPsiMethod(method);
        var param = new Param();
        param.setPsiElement(method.getParameterList().getParameters()[0]);
        param.setName("order");
        doc.setReqParamList(List.of(param));

        var targets = SourceDescriptionService.targets(doc);
        assertEquals(2, targets.size());
        var parameter = SourceDescriptionService.capture(targets.get(1));
        assertEquals("Old order.", parameter.original());
        assertTrue(SourceDescriptionService.apply(parameter, "New order."));
        assertEquals("New order.", CustomPsiCommentUtils.paramDocComment(
                method.getDocComment(), method.getParameterList().getParameters()[0]));
        assertTrue(method.getDocComment().getText().contains("@param region Keep this region."));

        assertTrue(SourceDescriptionService.apply(SourceDescriptionService.capture(targets.getFirst()), "Load order."));
        String text = method.getDocComment().getText();
        assertTrue(text, text.contains("Load order."));
        assertTrue(text, text.contains("@param order New order."));
        assertTrue(text, text.contains("@return Keep this result."));
    }

    public void testRecordDescriptionUsesOwnParamTagAndPreservesOtherTags() {
        var type = myFixture.addClass("""
                /**
                 * Ticket details.
                 * @param region Routing region.
                 */
                record Ticket(int count, String region) {}
                """);
        var doc = new DocView();
        doc.setPsiClass(type);
        var field = new Body();
        field.setName("count");
        field.setPsiElement(type.getRecordComponents()[0]);
        doc.getRespBody().setChildList(List.of(field));

        var targets = SourceDescriptionService.targets(doc);
        assertEquals(2, targets.size());
        var draft = SourceDescriptionService.capture(targets.get(1));
        assertTrue(SourceDescriptionService.apply(draft, "Number of tickets."));
        String text = type.getDocComment().getText();
        assertTrue(text, text.contains("Ticket details."));
        assertTrue(text, text.contains("@param region Routing region."));
        assertTrue(text, text.contains("@param count Number of tickets."));
    }

    public void testDubboArgumentWritesParamTagInsteadOfDtoClassComment() {
        var dto = myFixture.addClass("/** Keep DTO documentation. */ class OrderData {}");
        var service = myFixture.addClass("class Orders { void load(OrderData order) {} }");
        var method = service.getMethods()[0];
        var doc = new DocView();
        doc.setType(FrameworkEnum.DUBBO);
        doc.setPsiMethod(method);
        var row = new Body();
        row.setName("order");
        row.setPsiElement(dto);
        doc.getReqBody().setChildList(List.of(row));

        var draft = SourceDescriptionService.capture(SourceDescriptionService.targets(doc).get(1));
        assertTrue(SourceDescriptionService.apply(draft, "The order to load."));
        assertEquals("The order to load.", CustomPsiCommentUtils.paramDocComment(
                method.getDocComment(), method.getParameterList().getParameters()[0]));
        assertTrue(dto.getDocComment().getText().contains("Keep DTO documentation."));
    }

    public void testModelCandidateUsesSelectedParameterAndWaitsForApply() throws Exception {
        var method = myFixture.addClass("class Orders { void load(long orderId) {} }").getMethods()[0];
        var doc = new DocView();
        doc.setPsiMethod(method);
        var row = new Param();
        row.setName("orderId");
        row.setPsiElement(method.getParameterList().getParameters()[0]);
        doc.setReqParamList(List.of(row));
        var settings = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
        String oldProvider = settings.getTranslator();
        String oldUrl = settings.getOpenAiApiUrl();
        String oldKey = settings.getOpenAiApiKey();
        var prompt = new AtomicReference<String>();

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                prompt.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = """
                        {"choices":[{"message":{"content":"待查询的订单标识。"}}]}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            settings.setTranslator(Consts.OPENAI_GPT);
            settings.setOpenAiApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            settings.setOpenAiApiKey("");
            var draft = SourceDescriptionService.capture(SourceDescriptionService.targets(doc).get(1));
            var generation = SourceDescriptionService.prepareGeneration(draft, "强调这是查询入参");
            assertEquals("待查询的订单标识。", generation.get());
            assertTrue(prompt.get(), prompt.get().contains("请求参数 · orderId"));
            assertTrue(prompt.get(), prompt.get().contains("强调这是查询入参"));
            assertNull(method.getDocComment());
            assertTrue(SourceDescriptionService.apply(draft, "订单查询标识。"));
            assertEquals("订单查询标识。", CustomPsiCommentUtils.paramDocComment(
                    method.getDocComment(), method.getParameterList().getParameters()[0]));
        } finally {
            settings.setTranslator(oldProvider);
            settings.setOpenAiApiUrl(oldUrl);
            settings.setOpenAiApiKey(oldKey);
            server.stop(0);
        }
    }

    public void testStaleDraftDoesNotWrite() {
        var file = (PsiJavaFile) myFixture.configureByText("Orders.java", "class Orders { void load() {} }");
        var doc = new DocView();
        doc.setPsiMethod(file.getClasses()[0].getMethods()[0]);
        var draft = SourceDescriptionService.capture(SourceDescriptionService.targets(doc).getFirst());
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                myFixture.getEditor().getDocument().insertString(0, "// changed\n"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertFalse(SourceDescriptionService.apply(draft, "Load."));
        assertNull(file.getClasses()[0].getMethods()[0].getDocComment());
    }
}
