package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.httpClient.http.request.psi.*;
import com.intellij.httpClient.http.request.HttpRequestVariableSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.httpClient.execution.common.*;
import com.intellij.httpClient.execution.RestClientRequest;
import com.intellij.openapi.actionSystem.ActionManager;

public class HttpJson5IntegrationTest extends BasePlatformTestCase {
    private HttpRequest configure(String body) {
        myFixture.configureByText("demo.http", "POST http://127.0.0.1:12345/test\nContent-Type: application/json\n\n" + body);
        return PsiTreeUtil.findChildOfType(myFixture.getFile(), HttpRequest.class);
    }
    public void testJson5IsInjectedAndFormattingPreservesComments() {
        HttpRequest req = configure("{ // keep me\nname:'Alice', age:28,}\n");
        HttpMessageBody body = PsiTreeUtil.findChildOfType(req, HttpMessageBody.class);
        var injections = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(body);
        assertNotNull(injections);
        assertEquals("JSON5", injections.getFirst().getFirst().getLanguage().getID());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> { CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()); });
        assertTrue(myFixture.getFile().getText().contains("// keep me"));
    }
    public void testNativeRuntimeAndCopyActionAreEnhanced() {
        RuntimeInstaller.install();
        var support = RequestExecutionSupport.All.forContext(new RequestContext("POST", false, "http"));
        assertInstanceOf(support, Json5ExecutionSupport.class);
        assertInstanceOf(ActionManager.getInstance().getAction("HTTPClientConvertToCurlAndCopy"), Json5CopyAsCurlAction.class);
    }
    public void testNativeConversionSkipsCommentVariablesAndKeepsSource() throws Exception {
        HttpRequest req = configure("{ // {{undefined}}\nname:'Alice', n:0xff,}\n");
        String before = myFixture.getFile().getText();
        RestClientRequest converted = new Json5RequestConverter().psiToCommonRequest(
            com.intellij.psi.SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(req),
            HttpRequestVariableSubstitutor.empty());
        assertEquals("{\"name\":\"Alice\",\"n\":255}", converted.textToSend.trim());
        assertEquals(before, myFixture.getFile().getText());
    }
    public void testNativeCurlBuilderUsesSameNormalization() throws Exception {
        HttpRequest req = configure("{name:'O\\'Brien', url:'https://a/b', tags:[1,],}");
        String curl = Json5CurlExporter.convert(req, HttpRequestVariableSubstitutor.empty());
        assertFalse(curl.contains("tags:["));
        assertTrue(curl.contains("\"tags\":[1]"));
        assertTrue(curl.contains("https://a/b"));
    }

    public void testVariablesRemainValidWhileEditing() {
        HttpRequest req = configure("{id:{{id}}, name:'{{name}}', /* {{disabled}} */}");
        HttpMessageBody body = PsiTreeUtil.findChildOfType(req, HttpMessageBody.class);
        var injections = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(body);
        assertNotNull(injections);
        assertFalse(injections.getFirst().getFirst().getText(),
            PsiTreeUtil.hasErrorElements(injections.getFirst().getFirst()));
    }
    public void testInvalidCurlDoesNotOverwriteClipboard() {
        HttpRequest req = configure("{broken: }");
        var clipboard = com.intellij.openapi.ide.CopyPasteManager.getInstance();
        clipboard.setContents(new java.awt.datatransfer.StringSelection("keep-existing"));
        assertFalse(Json5CopyAsCurlAction.copy(req, null, HttpRequestVariableSubstitutor.empty()));
        assertEquals("keep-existing", clipboard.getContents(java.awt.datatransfer.DataFlavor.stringFlavor));
    }
    public void testPrivateEnvironmentAndFileVariablesAreSharedBySendAndCurl() throws Exception {
        myFixture.addFileToProject("http-client.env.json", "{\"dev\":{\"host\":\"public.invalid\",\"id\":42}}");
        myFixture.addFileToProject("http-client.private.env.json", "{\"dev\":{\"host\":\"127.0.0.1\"}}");
        myFixture.configureByText("variables.http", "@localName = Alice\nPOST http://{{host}}/test\nContent-Type: application/json\n\n{id:{{id}}, name:'{{localName}}', // {{disabled}}\n}");
        var req = PsiTreeUtil.findChildOfType(myFixture.getFile(), HttpRequest.class);
        var environment = com.intellij.httpClient.http.request.environment.HttpRequestEnvironment.create(getProject(), "dev", myFixture.getFile());
        var substitutor = com.intellij.httpClient.http.request.substitution.HttpRequestVariableRootSubstitutorKt.createRootSubstitutor(
            getProject(), environment, myFixture.getFile(), new com.intellij.httpClient.http.request.run.GlobalHttpClientSessionScope());
        var converted = new Json5RequestConverter().psiToCommonRequest(com.intellij.psi.SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(req), substitutor);
        assertEquals("{\"id\":42,\"name\":\"Alice\"}", converted.textToSend.trim());
        assertTrue(converted.getURL(), converted.getURL().startsWith("http://127.0.0.1/"));
        String curl = Json5CurlExporter.convert(req, substitutor);
        assertTrue(curl, curl.contains("http://127.0.0.1/"));
        assertTrue(curl, curl.contains(converted.textToSend.trim()));
    }
    public void testNonJsonAndExternalFileRequestsRemainNative() throws Exception {
        myFixture.configureByText("plain.http", "POST http://127.0.0.1/test\nContent-Type: text/plain\n\nnot json { // keep\n");
        var req = PsiTreeUtil.findChildOfType(myFixture.getFile(), HttpRequest.class);
        var converted = new Json5RequestConverter().psiToCommonRequest(com.intellij.psi.SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(req), HttpRequestVariableSubstitutor.empty());
        assertTrue(converted.textToSend, converted.textToSend.contains("not json { // keep"));
        assertEquals(com.intellij.httpClient.http.request.HttpRequestPsiConverter.convertFromHttpRequest(req, HttpRequestVariableSubstitutor.empty(), new com.intellij.httpClient.converters.curl.CurlRequestBuilder(false)), Json5CurlExporter.convert(req, HttpRequestVariableSubstitutor.empty()));
    }

    public void testToolbarActionResolvesContentTypeVariableAtRequestComment() {
        myFixture.configureByText("dynamic.http", "@ct = application/json\n### Request comment\nPOST http://127.0.0.1/test\nContent-Type: {{ct}}\n\n{a:1,}");
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("Request comment"));
        var action = ActionManager.getInstance().getAction("HTTPClientConvertToCurlAndCopy");
        var context = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
            .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, getProject())
            .add(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE, myFixture.getFile())
            .add(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR, myFixture.getEditor()).build();
        action.actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(action, null, "test", context));
        String curl = com.intellij.openapi.ide.CopyPasteManager.getInstance().getContents(java.awt.datatransfer.DataFlavor.stringFlavor);
        assertNotNull(curl);
        assertTrue(curl, curl.contains("\"a\":1"));
        assertFalse(curl, curl.contains("{a:1,}"));
    }

    public void testJsonSuffixCharsetAndContentLengthAreHandled() throws Exception {
        myFixture.configureByText("charset.rest", "PATCH http://127.0.0.1/test\nContent-Type: application/problem+json; charset=UTF-8\nContent-Length: 999\n\n{a:1,}");
        var req = PsiTreeUtil.findChildOfType(myFixture.getFile(), HttpRequest.class);
        var body = PsiTreeUtil.findChildOfType(req, HttpMessageBody.class);
        var injections = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(body);
        assertNotNull(injections);
        assertEquals("JSON5", injections.getFirst().getFirst().getLanguage().getID());
        var converted = new Json5RequestConverter().psiToCommonRequest(com.intellij.psi.SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(req), HttpRequestVariableSubstitutor.empty());
        assertEquals("{\"a\":1}", converted.textToSend.trim());
        assertNull(converted.getHeaderValue("Content-Length", null));
        String curl = Json5CurlExporter.convert(req, HttpRequestVariableSubstitutor.empty());
        assertFalse(curl, curl.toLowerCase().contains("content-length"));
    }
}
