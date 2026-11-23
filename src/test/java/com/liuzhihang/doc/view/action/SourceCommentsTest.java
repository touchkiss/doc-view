package com.liuzhihang.doc.view.action;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.liuzhihang.doc.view.common.Consts;
import com.sun.net.httpserver.HttpServer;
import com.liuzhihang.doc.view.utils.CustomPsiCommentUtils;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.service.KdocSupport;
import com.liuzhihang.doc.view.service.PackageInfoService;
import com.liuzhihang.doc.view.service.WriterService;
import com.liuzhihang.doc.view.view.settings.CommonSettingsConfigurable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.swing.KeyStroke;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.liuzhihang.doc.view.action.SourceCommentActionSupport.config;
import static com.liuzhihang.doc.view.action.SourceCommentActionSupport.service;

public class SourceCommentsTest extends LightJavaCodeInsightFixtureTestCase {
    public void testGenerateActionUsesProjectMappingAndDocViewReadsIt() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("Settlement.java", """
                class Settlement {
                    String find<caret>ByTrec(String trec) { return trec; }
                }
                """);
        var action = ActionManager.getInstance().getAction("liuzhihang.doc.sourceComments.generate");
        assertNotNull(action);
        var settings = config();
        var mappings = settings.projectMappings(getProject());
        String oldGlobal = settings.getWordMap().put("trec", "全局对账");
        String oldProject = mappings.put("trec", "项目交易对账");
        try {
            myFixture.testAction(action);
            var method = file.getClasses()[0].getMethods()[0];
            assertNotNull("Action must write a real PSI doc comment", method.getDocComment());
            assertEquals("项目交易对账", CustomPsiCommentUtils.paramDocComment(method.getDocComment(), method.getParameterList().getParameters()[0]));
        } finally {
            restore(settings.getWordMap(), "trec", oldGlobal);
            restore(mappings, "trec", oldProject);
        }
    }

    public void testOpenAiActionIsBoundToCtrlBackslashAndWritesWithoutConfirmation() throws Exception {
        String actionId = "liuzhihang.doc.sourceComments.generate";
        var keymap = KeymapManager.getInstance().getActiveKeymap();
        var shortcut = new KeyboardShortcut(KeyStroke.getKeyStroke("ctrl BACK_SLASH"), null);
        assertTrue(Arrays.asList(keymap.getShortcuts(actionId)).contains(shortcut));

        var file = (PsiJavaFile) myFixture.configureByText("ModelDemo.java", "class ModelDemo { void lo<caret>ad() {} }");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                byte[] body = """
                        {"choices":[{"message":{"content":"生成结果： /** 加载数据。 */"}}]}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        var settings = config();
        String translator = settings.getTranslator();
        String url = settings.getOpenAiApiUrl();
        String key = settings.getOpenAiApiKey();
        try {
            settings.setTranslator(Consts.OPENAI_GPT);
            settings.setOpenAiApiUrl("http://127.0.0.1:" + server.getAddress().getPort());
            settings.setOpenAiApiKey("");
            myFixture.testAction(ActionManager.getInstance().getAction(actionId));
            var method = file.getClasses()[0].getMethods()[0];
            assertEquals("加载数据。", CustomPsiCommentUtils.tagDocComment(method.getDocComment()));
        } finally {
            settings.setTranslator(translator);
            settings.setOpenAiApiUrl(url);
            settings.setOpenAiApiKey(key);
            server.stop(0);
        }
    }

    public void testMergePreservesExistingDocumentationAndAddsOnlyMissingTags() {
        PsiClass type = myFixture.addClass("""
                class Settlement {
                    /**
                     * Human documentation.
                     * @param quote Existing quote
                     * @throws IllegalStateException Existing exception
                     * @deprecated Existing note
                     */
                    String get(String quote, String region) throws IllegalStateException {
                        return quote;
                    }
                }
                """);
        var method = type.getMethods()[0];
        var writer = service(WriterService.class);
        String generated = """
                /**
                 * Different description.
                 * @param quote Generated quote
                 * @param region Region
                 * @throws IllegalStateException Generated exception
                 * @return Result
                 */
                """;
        assertNull(writer.prepareJavadoc(method, generated, EasyDocConfig.COVER_MODE_IGNORE));
        var change = writer.prepareJavadoc(method, generated, EasyDocConfig.COVER_MODE_MERGE);
        assertNotNull(change);
        writer.applyJavadocs(getProject(), List.of(change));
        String doc = method.getDocComment().getText();
        assertTrue(doc, doc.contains("Human documentation."));
        assertTrue(doc, doc.contains("@param quote Existing quote"));
        assertTrue(doc, doc.contains("@param region Region"));
        assertTrue(doc, doc.contains("@throws IllegalStateException Existing exception"));
        assertTrue(doc, doc.contains("@deprecated Existing note"));
        assertTrue(doc, doc.contains("@return Result"));
        assertFalse(doc, doc.contains("Generated quote") || doc.contains("Generated exception"));
        assertNull(writer.prepareJavadoc(method, generated, EasyDocConfig.COVER_MODE_MERGE));
    }

    public void testBatchWithMultipleDeclarationsCanBeUndoneOnce() {
        var file = (PsiJavaFile) myFixture.configureByText("Batch.java", """
                class Batch {
                    int count;
                    void reset() {}
                }
                """);
        String before = file.getText();
        var type = file.getClasses()[0];
        var writer = service(WriterService.class);
        var changes = List.of(type, type.getFields()[0], type.getMethods()[0]).stream()
                .map(target -> writer.prepareJavadoc(target, "/** Description */", EasyDocConfig.COVER_MODE_IGNORE)).toList();
        writer.applyJavadocs(getProject(), changes);
        assertNotNull(type.getDocComment());
        assertNotNull(type.getFields()[0].getDocComment());
        assertNotNull(type.getMethods()[0].getDocComment());

        var undo = UndoManager.getInstance(getProject());
        var editor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
        assertTrue(undo.isUndoAvailable(editor));
        undo.undo(editor);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(before, file.getText());
    }

    public void testSharedFieldDeclarationKeepsFirstFieldComment() {
        var type = myFixture.addClass("class Example { int first, second; }");
        var writer = service(WriterService.class);
        var fields = type.getFields();
        assertSame(fields[0], WriterService.declarationOwner(fields[1]));
        var first = writer.prepareJavadoc(fields[0], "/** First */", EasyDocConfig.COVER_MODE_IGNORE);
        assertNull(writer.prepareJavadoc(fields[1], "/** Second */", EasyDocConfig.COVER_MODE_IGNORE));
        writer.applyJavadocs(getProject(), List.of(first));
        assertTrue(type.getText(), type.getText().contains("First"));
        assertFalse(type.getText(), type.getText().contains("Second"));
    }

    public void testRecordWriterRejectsImplicitAccessor() {
        PsiClass type = myFixture.addClass("record Ticket(int count) {}");
        var accessors = type.findMethodsByName("count", false);
        assertEquals(1, accessors.length);
        var accessor = accessors[0];
        assertFalse(accessor.isPhysical());
        assertTrue("The implicit accessor reports writable despite having no declaration", accessor.isWritable());
        var writer = service(WriterService.class);
        assertNull(writer.prepareJavadoc(accessor, "/** Count */", EasyDocConfig.COVER_MODE_IGNORE));
    }

    public void testRecordBatchDocumentsOnlyExplicitMembers() {
        var file = (PsiJavaFile) myFixture.configureByText("Ticket.java", """
                record Tic<caret>ket(int count) {
                    Ticket {}
                    int doubleCount() { return 2 * count; }
                }
                """);
        var action = ActionManager.getInstance().getAction("liuzhihang.doc.sourceComments.generateMembers");
        myFixture.testAction(action);
        PlatformTestUtil.waitWithEventsDispatching("Record constructor and explicit method receive Javadoc", () -> {
            var type = file.getClasses()[0];
            return type.getConstructors()[0].getDocComment() != null
                    && type.findMethodsByName("doubleCount", false)[0].getDocComment() != null;
        }, 10);
        var type = file.getClasses()[0];
        assertNull(type.findMethodsByName("count", false)[0].getDocComment());
        assertTrue(file.getText().contains("record Ticket(int count)"));
    }

    public void testRecordComponentGetsDocViewReadableParamTag() {
        var file = (PsiJavaFile) myFixture.configureByText("Identifier.java", """
                record Identifier(String tre<caret>c) {}
                """);
        var type = file.getClasses()[0];
        var mappings = config().projectMappings(getProject());
        String old = mappings.put("trec", "项目对账");
        try {
            myFixture.testAction(ActionManager.getInstance().getAction("liuzhihang.doc.sourceComments.generate"));
            assertNotNull(type.getDocComment());
            assertTrue(type.getDocComment().getText(), type.getDocComment().getText().contains("@param trec 项目对账"));
        } finally {
            restore(mappings, "trec", old);
        }
    }

    public void testProtoMemberGenerationUsesSameProjectMapping() {
        var file = myFixture.configureByText("Settlement.proto", """
                syntax = "proto3";
                message Settlement {
                  string tr<caret>ec = 1;
                }
                """);
        var mappings = config().projectMappings(getProject());
        String old = mappings.put("trec", "项目对账");
        try {
            myFixture.testAction(ActionManager.getInstance().getAction("liuzhihang.doc.sourceComments.generateMembers"));
            PlatformTestUtil.waitWithEventsDispatching("Proto comment was written",
                    () -> file.getText().contains("项目对账"), 10);
            assertTrue(file.getText(), file.getText().contains("string trec = 1;"));
        } finally {
            restore(mappings, "trec", old);
        }
    }

    public void testBatchTranslationReleasesReadLockAndDiscardsStaleOutput() throws Exception {
        var file = (PsiJavaFile) myFixture.configureByText("Waiting.java", "class Waiting { void delayed() {} }");
        var method = file.getClasses()[0].getMethods()[0];
        var editorWrite = new CountDownLatch(1);
        var responseSent = new AtomicBoolean();
        var editedBeforeResponse = new AtomicBoolean();
        var document = myFixture.getEditor().getDocument();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "// user edit\n"));
                    editorWrite.countDown();
                });
                try {
                    editedBeforeResponse.set(editorWrite.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = "{\"code\":0,\"data\":\"远程释义\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                responseSent.set(true);
            }
        });
        server.start();
        var settings = config();
        String translator = settings.getTranslator();
        String url = settings.getCustomUrl();
        String httpMethod = settings.getCustomHttpMethod();
        int timeout = settings.getTimeout();
        try {
            settings.setTranslator(Consts.CUSTOM_URL);
            settings.setCustomUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/?q={query}");
            settings.setCustomHttpMethod("GET");
            settings.setTimeout(15000);
            BatchJavadocGeneration.start(getProject(), file, List.of(method));
            PlatformTestUtil.waitWithEventsDispatching("Translation returned", responseSent::get, 15);
            assertTrue("The edit must finish before the server returns a translation", editedBeforeResponse.get());
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();
            assertNull(file.getClasses()[0].getMethods()[0].getDocComment());
        } finally {
            server.stop(0);
            settings.setTranslator(translator);
            settings.setCustomUrl(url);
            settings.setCustomHttpMethod(httpMethod);
            settings.setTimeout(timeout);
        }
    }

    public void testPackageInfoCreationAndSkip() {
        var type = myFixture.addClass("package glossary; class Term {}");
        var directory = type.getContainingFile().getContainingDirectory();
        var packages = service(PackageInfoService.class);
        packages.write(getProject(), Map.of(directory, "项目术语"), EasyDocConfig.COVER_MODE_IGNORE);
        var file = directory.findFile("package-info.java");
        assertNotNull(file);
        assertTrue(file.getText(), file.getText().contains("package glossary;"));
        packages.write(getProject(), Map.of(directory, "其他内容"), EasyDocConfig.COVER_MODE_IGNORE);
        assertFalse(file.getText(), file.getText().contains("其他内容"));
    }

    public void testOptionalKdocServiceWritesKdocAndKeepsExistingByDefault() {
        var file = (KtFile) myFixture.configureByText(KotlinFileType.INSTANCE, "fun findTrec(trec: String): String = trec");
        var target = (KtNamedFunction) file.getDeclarations().getFirst();
        KdocSupport kotlin = ApplicationManager.getApplication().getService(KdocSupport.class);
        assertNotNull(kotlin);
        var write = kotlin.prepare(target, EasyDocConfig.COVER_MODE_IGNORE);
        assertNotNull(write);
        write.run();
        assertNotNull(target.getDocComment());
        assertTrue(target.getDocComment().getText().contains("@param"));
        assertNull(kotlin.prepare(target, EasyDocConfig.COVER_MODE_IGNORE));
    }

    public void testSettingsFormCanBeCreated() {
        var configurable = new CommonSettingsConfigurable();
        var tabs = (javax.swing.JTabbedPane) configurable.createComponent();
        assertEquals(3, tabs.getTabCount());
        assertEquals("模型与翻译", tabs.getTitleAt(0));
        assertEquals("项目词典", tabs.getTitleAt(2));
        configurable.reset();
        configurable.disposeUIResources();
    }

    public void testKdocSettingsAndTemplateFormsCanBeCreated() {
        assertNotNull(new com.liuzhihang.doc.view.view.settings.kdoc.KdocSettingsConfigurable().createComponent());
        assertNotNull(new com.liuzhihang.doc.view.view.settings.kdoc.template.KtMethodSettingsConfigurable().createComponent());
    }

    private static void restore(Map<String, String> map, String key, String old) {
        if (old == null) {
            map.remove(key);
        } else {
            map.put(key, old);
        }
    }
}
