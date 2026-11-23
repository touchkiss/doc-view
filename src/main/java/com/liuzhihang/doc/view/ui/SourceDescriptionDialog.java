package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.service.SourceDescriptionService;
import com.star.easydoc.service.gpt.ModelRequestException;
import com.star.easydoc.view.settings.CommonSettingsConfigurable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

final class SourceDescriptionDialog extends DialogWrapper {
    private final Project project;
    private final JComboBox<SourceDescriptionService.Target> targetBox;
    private final JBTextArea original = textArea(false);
    private final JBTextArea candidate = textArea(true);
    private final JBTextField instruction = new JBTextField();
    private final JBLabel status = new JBLabel();
    private SourceDescriptionService.Draft draft;

    SourceDescriptionDialog(Project project, List<SourceDescriptionService.Target> targets) {
        super(project);
        this.project = project;
        targetBox = new JComboBox<>(targets.toArray(SourceDescriptionService.Target[]::new));
        setTitle("重新生成源码注释");
        setOKButtonText("写回源码");
        init();
        targetBox.addActionListener(event -> loadTarget());
        loadTarget();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        outer.setPreferredSize(new Dimension(JBUI.scale(710), JBUI.scale(340)));
        JPanel controls = new JPanel(new BorderLayout(JBUI.scale(8), JBUI.scale(7)));
        JBLabel targetLabel = new JBLabel("要更新的说明");
        targetLabel.setLabelFor(targetBox);
        controls.add(targetLabel, BorderLayout.WEST);
        controls.add(targetBox, BorderLayout.CENTER);
        JButton settings = new JButton("生成设置");
        settings.addActionListener(event -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, CommonSettingsConfigurable.class);
            updateInstruction();
        });
        controls.add(settings, BorderLayout.EAST);
        instruction.getEmptyText().setText("AI 补充要求，例如说明空值时的行为");
        controls.add(instruction, BorderLayout.SOUTH);
        outer.add(controls, BorderLayout.NORTH);

        JBSplitter comparison = new JBSplitter(false, .5f);
        comparison.setFirstComponent(pane("当前源码注释", original));
        comparison.setSecondComponent(pane("待写回的说明", candidate));
        outer.add(comparison, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        JButton generate = new JButton("重新生成");
        generate.addActionListener(event -> generate());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(generate);
        footer.add(status, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        outer.add(footer, BorderLayout.SOUTH);
        return outer;
    }

    private void loadTarget() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        var selected = (SourceDescriptionService.Target) targetBox.getSelectedItem();
        draft = selected == null ? null : ReadAction.compute(() -> SourceDescriptionService.capture(selected));
        original.setText(draft == null ? "" : draft.original());
        candidate.setText("");
        candidate.setEnabled(draft != null);
        setOKActionEnabled(draft != null);
        status.setText(draft == null ? "目标源码当前不可写。"
                : differsFromDisplayed(draft) ? "文档当前显示的说明与 Javadoc 不同，可能来自注解或生成规则。" : "");
        updateInstruction();
    }

    private static boolean differsFromDisplayed(SourceDescriptionService.Draft draft) {
        String visible = draft.target().displayed();
        return visible != null && !visible.isBlank()
                && !visible.replaceAll("\\s+", "").equals(draft.original().replaceAll("\\s+", ""));
    }

    private void updateInstruction() {
        instruction.setEnabled(SourceDescriptionService.usesModel());
        instruction.getEmptyText().setText(instruction.isEnabled()
                ? "AI 补充要求，例如说明空值时的行为" : "当前使用词典或翻译服务，可在「生成设置」切换 AI");
    }

    private void generate() {
        if (draft == null) {
            return;
        }
        String hint = instruction.isEnabled() ? instruction.getText().strip() : "";
        try {
            var generation = ReadAction.compute(() -> SourceDescriptionService.prepareGeneration(draft, hint));
            if (generation == null) {
                status.setText("源码已修改，请重新选择说明后再生成。");
                return;
            }
            String text = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    generation::get, "正在重新生成说明", true, project);
            if (text == null || text.isBlank()) {
                status.setText("没有生成说明，可编辑后写回。");
                return;
            }
            candidate.setText(text.strip());
            candidate.setCaretPosition(0);
        } catch (ProcessCanceledException ignored) {
            // Keep the previous candidate when generation is canceled.
        } catch (ModelRequestException error) {
            status.setText(error.getMessage());
        } catch (RuntimeException error) {
            status.setText("生成失败，请检查生成设置。");
        }
    }

    @Override
    protected ValidationInfo doValidate() {
        return candidate.getText().isBlank() ? new ValidationInfo("请先生成或填写说明。", candidate) : null;
    }

    @Override
    protected void doOKAction() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (draft == null || candidate.getText().isBlank()) {
            super.doOKAction();
            return;
        }
        if (!SourceDescriptionService.apply(draft, candidate.getText())) {
            status.setText("源码已修改，未写回。请重新打开预览后重试。");
            return;
        }
        super.doOKAction();
    }

    private static JBTextArea textArea(boolean editable) {
        var component = new JBTextArea();
        component.setEditable(editable);
        component.setLineWrap(true);
        component.setWrapStyleWord(true);
        return component;
    }

    private static JPanel pane(String title, JBTextArea text) {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        JBLabel label = new JBLabel(title);
        label.setLabelFor(text);
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JBScrollPane(text), BorderLayout.CENTER);
        return panel;
    }
}
