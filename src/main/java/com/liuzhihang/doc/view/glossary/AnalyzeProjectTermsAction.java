package com.liuzhihang.doc.view.glossary;

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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.common.util.HttpUtil;
import com.liuzhihang.doc.view.service.gpt.ModelRequestException;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.service.gpt.GptService;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class AnalyzeProjectTermsAction extends AnAction {
    private static final String DIALOG_TITLE = "AI 分析项目术语";
    private static final int MAX_SOURCE_FILES = 250;
    private static final int MAX_SOURCE_BYTES = 256 * 1024;
    private static final int MAX_DECLARATIONS_PER_FILE = 24;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation().setEnabledAndVisible(project != null && !DumbService.isDumb(project));
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
                    DIALOG_TITLE);
            return;
        }
        if (!confirmAnalysis(project, config, glm)) {
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
                    suggestions = analyze(evidence, indicator, provider);
                } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                    throw e;
                } catch (IllegalArgumentException | ModelRequestException e) {
                    error = e.getMessage();
                } catch (RuntimeException _) {
                    error = "模型请求失败，请检查地址、密钥、模型名称和网络连接。";
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                if (error != null) {
                    Messages.showErrorDialog(project, error, DIALOG_TITLE);
                } else if (suggestions.isEmpty()) {
                    Messages.showInfoMessage(project, "未找到可保存的术语。", DIALOG_TITLE);
                } else {
                    new GlossaryReviewDialog(project, config, suggestions).show();
                }
            }
        }.queue();
    }

    private static Map<String, String> analyze(ProjectGlossary.Evidence evidence, ProgressIndicator indicator,
                                               String provider) {
        indicator.setText("正在生成候选释义");
        String response = ApplicationManager.getApplication().getService(GptService.class)
                .chat(provider, evidence.prompt());
        indicator.checkCanceled();
        return ProjectGlossary.parse(response, evidence.terms());
    }

    private static boolean confirmAnalysis(Project project, EasyDocConfig config, boolean glm) {
        String destination = glm ? "智谱清言（open.bigmodel.cn）" : GptService.endpointHost(config.getOpenAiApiUrl());
        return Messages.showOkCancelDialog(project,
                "从项目 Java 和 Kotlin 源码抽取最多 400 个声明标识符，发送给"
                        + destination + "。确认后可审阅释义并选择保存范围。",
                DIALOG_TITLE, "开始分析", "取消", Messages.getQuestionIcon()) == Messages.OK;
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
                boolean supported = "java".equals(extension) || "kt".equals(extension);
                if (file.isDirectory() || !supported || !index.isInSourceContent(file)) {
                    return true;
                }
                scanned[0]++;
                if (file.getLength() <= MAX_SOURCE_BYTES) {
                    var psiFile = psiManager.findFile(file);
                    if (psiFile != null) {
                        collectIdentifiers(psiFile, evidence, indicator);
                    }
                }
                return scanned[0] < MAX_SOURCE_FILES && !evidence.isFull();
            });
        });
        return evidence;
    }

    private static void collectIdentifiers(PsiFile file, ProjectGlossary.Evidence evidence, ProgressIndicator indicator) {
        file.accept(new PsiRecursiveElementWalkingVisitor() {
            private int declarations;

            @Override
            public void visitElement(@NotNull PsiElement element) {
                indicator.checkCanceled();
                if (element instanceof PsiNameIdentifierOwner owner) {
                    evidence.add(owner.getName());
                    declarations++;
                }
                if (evidence.isFull() || declarations >= MAX_DECLARATIONS_PER_FILE) {
                    stopWalking();
                } else {
                    super.visitElement(element);
                }
            }
        });
    }
}
