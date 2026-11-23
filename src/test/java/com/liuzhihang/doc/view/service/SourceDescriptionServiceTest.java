package com.liuzhihang.doc.view.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocDescription;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.utils.CustomPsiCommentUtils;
import com.liuzhihang.doc.view.utils.DocViewUtils;

import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
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

    public void testClassBatchCombinesRecordChangesThatShareOneJavadoc() {
        var type = myFixture.addClass("""
                /**
                 * Ticket details.
                 * @param count Original count.
                 * @param region Original region.
                 * @since 2.0
                 */
                record Ticket(int count, String region) { static int code; }
                """);
        var count = SourceDescriptionService.fieldTarget("count", null, type.getRecordComponents()[0]);
        var region = SourceDescriptionService.fieldTarget("region", null, type.getRecordComponents()[1]);
        var description = SourceDescriptionService.classTarget(type);
        var codeField = type.findFieldByName("code", false);
        var code = SourceDescriptionService.fieldTarget("code", null, codeField);
        assertTrue(SourceDescriptionService.applyBatch(List.of(
                new SourceDescriptionService.Edit(SourceDescriptionService.capture(description), "Ticket summary."),
                new SourceDescriptionService.Edit(SourceDescriptionService.capture(count), "Number of tickets."),
                new SourceDescriptionService.Edit(SourceDescriptionService.capture(region), "Routing region."),
                new SourceDescriptionService.Edit(SourceDescriptionService.capture(code), "Ticket code."))));
        String text = type.getDocComment().getText();
        assertTrue(text, text.contains("Ticket summary."));
        assertTrue(text, text.contains("@param count Number of tickets."));
        assertTrue(text, text.contains("@param region Routing region."));
        assertTrue(text, text.contains("@since 2.0"));
        assertTrue(codeField.getDocComment().getText().contains("Ticket code."));
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

    public void testOpenApiAndEnumProvenanceSurviveCopiesAndSourceRewrite() {
        myFixture.addClass("package io.swagger.v3.oas.annotations.media; public @interface Schema { String description(); }");
        var type = myFixture.addClass("""
                import io.swagger.v3.oas.annotations.media.Schema;
                class OrderStateDto {
                    enum State {
                        /** Pending payment. */
                        PENDING
                    }

                    /**
                     * Original state {@link State}.
                     * @see State
                     */
                    @Schema(description = "Published state.")
                    State state;
                }
                """);
        var field = type.getFields()[0];
        var description = DocViewUtils.fieldDescription(field);
        assertEquals("Original state {@link State}.", description.sourceComment());
        assertEquals("Published state.", description.render());
        assertEquals(List.of(DocDescription.Source.ENUM_REFERENCE, DocDescription.Source.OPEN_API),
                description.supplements().stream().map(DocDescription.Supplement::source).toList());
        assertTrue(description.supplementaryText().contains("PENDING"));

        var body = new Body();
        body.setDescription(description);
        var cached = DtoSchemaCacheService.copyBody(body, null, false);
        var row = DocViewData.buildBodyDataList(List.of(cached)).getFirst();
        assertEquals(description, row.getDescription());
        assertEquals("Published state.", row.getDesc());

        var target = SourceDescriptionService.fieldDescriptionTarget("state", description, field);
        var draft = SourceDescriptionService.capture(target);
        assertTrue(SourceDescriptionService.apply(draft, "Changed state."));
        assertTrue(field.getDocComment().getText().contains("{@link State}"));
        assertTrue(field.getDocComment().getText().contains("@see State"));
        assertFalse(field.getDocComment().getText().contains("PENDING"));
        assertEquals("Published state.", DocViewUtils.fieldDesc(field));
    }

    public void testOrdinaryFieldCommentIsReplacedInPlace() {
        var type = myFixture.addClass("""
                class Credentials {
                    String /* Original token. */ token;
                }
                """);
        var field = type.getFields()[0];
        var description = DocViewUtils.fieldDescription(field);
        assertEquals("Original token.", description.sourceComment());
        var draft = SourceDescriptionService.capture(
                SourceDescriptionService.fieldDescriptionTarget("token", description, field));
        assertTrue(SourceDescriptionService.apply(draft, "Updated token."));
        assertTrue(field.getText().contains("/* Updated token. */"));
        assertNull(field.getDocComment());
    }

    public void testProtoGetterDescriptionHasNoFieldWriteTarget() {
        var field = myFixture.addClass("class GeneratedDto { int count; }").getFields()[0];
        var description = DocDescription.EMPTY.with(DocDescription.Source.PROTO_GETTER, "Generated field.");
        assertNull(SourceDescriptionService.fieldDescriptionTarget("count", description, field));
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
