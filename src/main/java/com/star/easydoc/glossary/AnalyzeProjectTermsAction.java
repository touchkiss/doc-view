package com.star.easydoc.glossary;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.star.easydoc.common.Consts;
import com.star.easydoc.common.util.HttpUtil;
import com.star.easydoc.service.gpt.ModelRequestException;
import com.star.easydoc.config.EasyDocConfig;
import com.star.easydoc.config.EasyDocConfigComponent;
import com.star.easydoc.service.gpt.GptService;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class AnalyzeProjectTermsAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation().setEnabled(project != null && !DumbService.isDumb(project));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        var application = ApplicationManager.getApplication();
        EasyDocConfig config = application.getService(EasyDocConfigComponent.class).getState();
        String endpoint = config.getOpenAiApiUrl();
        boolean compatibleModelConfigured = !blank(endpoint)
                && (!blank(config.getOpenAiApiKey()) || HttpUtil.isLoopback(GptService.endpointHost(endpoint)));
        boolean glm = Consts.CHATGLM_GPT.equals(config.getTranslator())
                || !compatibleModelConfigured && !blank(config.getChatGlmApiKey());
        String provider = glm ? Consts.CHATGLM_GPT : Consts.OPENAI_GPT;
        if (glm ? blank(config.getChatGlmApiKey()) : !compatibleModelConfigured) {
            Messages.showErrorDialog(project, "请先在 Doc View 的源码注释设置中配置通用大模型或智谱清言。",
                    "AI 分析项目术语");
            return;
        }
        if (Messages.showOkCancelDialog(project,
                "从项目 Java 和 Kotlin 源码抽取最多 400 个声明标识符，发送给"
                        + (glm ? "智谱清言（open.bigmodel.cn）" : GptService.endpointHost(config.getOpenAiApiUrl())) + "。确认后可审阅释义并选择保存范围。",
                "AI 分析项目术语", "开始分析", "取消", Messages.getQuestionIcon()) != Messages.OK) {
            return;
        }

        new Task.Backgroundable(project, "分析项目术语", true) {
            private Map<String, String> suggestions = Map.of();
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                ProjectGlossary.Evidence evidence = scan(project, indicator);
                indicator.checkCanceled();
                if (evidence.terms().isEmpty()) {
                    return;
                }
                try {
                    indicator.setText("正在生成候选释义");
                    String response = application.getService(GptService.class).chat(provider, evidence.prompt());
                    indicator.checkCanceled();
                    suggestions = ProjectGlossary.parse(response, evidence.terms());
                } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                    throw e;
                } catch (IllegalArgumentException | ModelRequestException e) {
                    error = e.getMessage();
                } catch (RuntimeException e) {
                    error = "模型请求失败，请检查地址、密钥、模型名称和网络连接。";
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (error != null) {
                    Messages.showErrorDialog(project, error, "AI 分析项目术语");
                } else if (suggestions.isEmpty()) {
                    Messages.showInfoMessage(project, "未找到可保存的术语。", "AI 分析项目术语");
                } else {
                    new GlossaryReviewDialog(project, config, suggestions).show();
                }
            }
        }.queue();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static ProjectGlossary.Evidence scan(Project project, ProgressIndicator indicator) {
        var evidence = new ProjectGlossary.Evidence();
        indicator.setText("正在收集项目声明标识符");
        ReadAction.run(() -> {
            ProjectFileIndex index = ProjectFileIndex.getInstance(project);
            PsiManager psiManager = PsiManager.getInstance(project);
            int[] scanned = {0};
            index.iterateContent(file -> {
                indicator.checkCanceled();
                String extension = file.getExtension();
                if (!file.isDirectory() && ("java".equals(extension) || "kt".equals(extension))
                        && index.isInSourceContent(file)) {
                    scanned[0]++;
                    if (file.getLength() <= 256 * 1024) {
                        var psiFile = psiManager.findFile(file);
                        if (psiFile != null) {
                            int[] declarations = {0};
                            psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
                                @Override
                                public void visitElement(@NotNull PsiElement element) {
                                    indicator.checkCanceled();
                                    if (element instanceof PsiNameIdentifierOwner owner) {
                                        evidence.add(owner.getName());
                                        declarations[0]++;
                                    }
                                    if (evidence.isFull() || declarations[0] >= 24) {
                                        stopWalking();
                                    } else {
                                        super.visitElement(element);
                                    }
                                }
                            });
                        }
                    }
                }
                return scanned[0] < 250 && !evidence.isFull();
            });
        });
        return evidence;
    }
}
