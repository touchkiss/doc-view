package com.liuzhihang.doc.view.httpclient.idea;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.httpClient.actions.copyPaste.HttpRequestCopyAsCurlAction;
import com.intellij.httpClient.http.request.*;
import com.intellij.httpClient.http.request.environment.*;
import com.intellij.httpClient.http.request.run.*;
import com.intellij.httpClient.http.request.substitution.HttpRequestVariableRootSubstitutorKt;
import com.intellij.httpClient.http.request.psi.HttpRequest;
import com.intellij.httpClient.http.request.psi.HttpRequestBlock;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.liuzhihang.doc.view.httpclient.core.JsonBodyNormalizer;
import java.awt.datatransfer.StringSelection;
import java.util.*;

/** Keeps the original toolbar/menu action ID and delegates unaffected requests to the native action. */
public final class Json5CopyAsCurlAction extends DumbAwareAction {
    private static final String VARIABLE_PREFIX = "{{";
    private final HttpRequestCopyAsCurlAction original = new HttpRequestCopyAsCurlAction();
    public Json5CopyAsCurlAction() { copyFrom(original); }
    @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(AnActionEvent event) { original.update(event); }
    @Override public void actionPerformed(AnActionEvent event) {
        var data = original.getData(event);
        if (data == null) {
            return;
        }
        HttpRequest request = PsiTreeUtil.getParentOfType(data.getThird(), HttpRequest.class, false);
        if (request == null) {
            var block = PsiTreeUtil.getParentOfType(data.getThird(), HttpRequestBlock.class, false);
            if (block != null) {
                request = block.getRequest();
            }
        }
        if (request == null) {
            original.actionPerformed(event);
            return;
        }
        final HttpRequest target = request;
        String rawMime = request.getMimeType();
        boolean unsupportedMime = rawMime != null && !rawMime.contains(VARIABLE_PREFIX)
                && !JsonBodyNormalizer.isJsonMediaType(rawMime);
        if (!HttpJsonBodies.isSupported(request) || unsupportedMime) {
            original.actionPerformed(event); return;
        }
        Project project = data.getFirst();
        Editor editor = data.getSecond();
        PsiFile file = request.getContainingFile();
        var environments = HttpClientEnvironmentsProvider.INSTANCE.provideEnvironments(file).asList();
        if (environments.isEmpty() || HttpRequestPsiUtils.collectVariablesRangesInMessageBody(request.getText()).isEmpty()) {
            copy(request, editor, noEnvironment(project, file)); return;
        }
        // Same provider and root substitutor as the native cURL action; private environments retain precedence.
        List<String> choices = new ArrayList<>();
        environments.forEach(env -> choices.add(env.getName()));
        choices.add("无环境");
        JBPopupFactory.getInstance().createPopupChooserBuilder(choices)
            .setTitle("选择 cURL 环境")
            .setItemChosenCallback(name -> {
                try {
                    var env = "无环境".equals(name) ? HttpRequestEnvironment.empty() : HttpRequestEnvironment.create(project, name, file);
                    copy(target, editor, HttpRequestVariableRootSubstitutorKt.createRootSubstitutor(project, env, file, new GlobalHttpClientSessionScope()));
                } catch (com.intellij.execution.ExecutionException e) { HintManager.getInstance().showErrorHint(editor, e.getMessage()); }
            }).createPopup().showInBestPositionFor(editor);
    }
    private static HttpRequestVariableSubstitutor noEnvironment(Project project, PsiFile file) {
        return HttpRequestVariableRootSubstitutorKt.createRootSubstitutor(project, HttpRequestEnvironment.empty(), file, new GlobalHttpClientSessionScope());
    }
    public static boolean copy(HttpRequest request, Editor editor, HttpRequestVariableSubstitutor substitutor) {
        try {
            String curl = Json5CurlExporter.convert(request, substitutor);
            CopyPasteManager.getInstance().setContents(new StringSelection(curl));
            if (editor != null) {
                HintManager.getInstance().showInformationHint(editor, "已复制 cURL（标准 JSON）");
            }
            return true;
        } catch (com.intellij.execution.ExecutionException | IllegalArgumentException e) {
            if (editor != null) {
                HintManager.getInstance().showErrorHint(editor, e.getMessage());
            }
            return false;
        }
    }
}
