package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.service.SourceDescriptionService;
import com.liuzhihang.doc.view.service.gpt.ModelRequestException;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class ClassDescriptionDialog extends DialogWrapper {
    record Row(SourceDescriptionService.Draft draft, String candidate, boolean selected) {}

    private final Project project;
    private final List<Row> rows;
    private final DefaultTableModel model;
    private final JBTable table;
    private final JBLabel status = new JBLabel("勾选要生成或写回的说明。");
    private List<SourceDescriptionService.Edit> written = List.of();

    ClassDescriptionDialog(Project project, List<Row> rows) {
        super(project);
        this.project = project;
        this.rows = rows;
        model = new DefaultTableModel(new String[]{"选择", "声明", "当前源码注释", "待写回的说明"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }
        };
        for (Row row : rows) {
            model.addRow(new Object[]{row.selected(), row.draft().target().label(), row.draft().original(), row.candidate()});
        }
        table = new JBTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(58));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(180));
        table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(280));
        setTitle("整类源码注释");
        setOKButtonText("写回选中说明");
        init();
    }

    List<SourceDescriptionService.Edit> written() {
        return written;
    }

    @Override
    protected JComponent createCenterPanel() {
        var panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.setPreferredSize(new Dimension(JBUI.scale(810), JBUI.scale(370)));
        panel.add(new JBScrollPane(table), BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout());
        var generate = new JButton("重新生成选中说明");
        generate.addActionListener(event -> generate());
        footer.add(status, BorderLayout.CENTER);
        footer.add(generate, BorderLayout.EAST);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void generate() {
        if (!stopEditing()) {
            return;
        }
        var selected = selectedRows();
        if (selected.isEmpty()) {
            status.setText("请勾选要生成的说明。");
            return;
        }
        record Job(int index, Supplier<String> generation) {}
        record Result(int index, String text, String error) {}
        try {
            var jobs = ReadAction.compute(() -> selected.stream().map(index ->
                    new Job(index, SourceDescriptionService.prepareGeneration(rows.get(index).draft(), ""))).toList());
            List<Result> results = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                List<Result> generated = new ArrayList<>();
                for (Job job : jobs) {
                    ProgressManager.checkCanceled();
                    try {
                        String text = job.generation() == null ? null : job.generation().get();
                        generated.add(new Result(job.index(), text, text == null || text.isBlank()
                                ? "目标已变更或生成结果为空。" : ""));
                    } catch (ProcessCanceledException canceled) {
                        throw canceled;
                    } catch (ModelRequestException failure) {
                        generated.add(new Result(job.index(), null, failure.getMessage()));
                    } catch (RuntimeException failure) {
                        generated.add(new Result(job.index(), null, "生成失败，请检查生成设置。"));
                    }
                }
                return generated;
            }, "正在重新生成整类注释", true, project);
            if (results == null) {
                return;
            }
            String error = "";
            int generated = 0;
            for (Result result : results) {
                if (result.error().isEmpty()) {
                    model.setValueAt(result.text().strip(), result.index(), 3);
                    generated++;
                } else if (error.isEmpty()) {
                    error = result.error();
                }
            }
            status.setText("已生成 " + generated + " 条。" + error);
        } catch (ProcessCanceledException ignored) {
            // Keep the current drafts on cancel.
        } catch (RuntimeException failure) {
            status.setText("生成失败，请检查生成设置。");
        }
    }

    private boolean stopEditing() {
        return !table.isEditing() || table.getCellEditor().stopCellEditing();
    }

    private List<Integer> selectedRows() {
        List<Integer> selected = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            if (Boolean.TRUE.equals(model.getValueAt(index, 0))) {
                selected.add(index);
            }
        }
        return selected;
    }

    @Override
    protected void doOKAction() {
        if (!stopEditing()) {
            return;
        }
        var selected = selectedRows();
        List<SourceDescriptionService.Edit> edits = new ArrayList<>();
        for (int index : selected) {
            var draft = rows.get(index).draft();
            String candidate = model.getValueAt(index, 3) instanceof String text ? text.strip() : "";
            if (!candidate.isBlank() && !candidate.equals(draft.original().strip())) {
                edits.add(new SourceDescriptionService.Edit(draft, candidate));
            }
        }
        if (edits.isEmpty()) {
            setErrorText("请勾选至少一条已生成或编辑的说明。", table);
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (!SourceDescriptionService.applyBatch(edits)) {
            setErrorText("源码已修改，本次未写回。请关闭对话框并重试。", table);
            return;
        }
        written = List.copyOf(edits);
        super.doOKAction();
    }
}
