package com.liuzhihang.doc.view.glossary;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.liuzhihang.doc.view.config.EasyDocConfig;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.table.DefaultTableModel;
import java.awt.Dimension;
import java.util.Map;

final class GlossaryReviewDialog extends DialogWrapper {
    private enum Scope {
        PROJECT("当前项目"), GLOBAL("全局");

        private final String label;

        Scope(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Project project;
    private final EasyDocConfig config;
    private final DefaultTableModel model;
    private final JBTable table;

    GlossaryReviewDialog(Project project, EasyDocConfig config, Map<String, String> suggestions) {
        super(project);
        this.project = project;
        this.config = config;
        model = new DefaultTableModel(new String[]{"保存", "术语", "建议释义", "范围", "当前释义"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return switch (column) {
                    case 0 -> Boolean.class;
                    case 3 -> Scope.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 2 || column == 3;
            }
        };
        Map<String, String> existing = config.getWordMapWithProject(project);
        suggestions.forEach((term, meaning) -> model.addRow(new Object[]{
                !existing.containsKey(term), term, meaning, Scope.PROJECT, existing.getOrDefault(term, "")
        }));
        table = new JBTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(Scope.values())));
        setTitle("审阅项目术语");
        setOKButtonText("保存选中映射");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        var scroll = new JBScrollPane(table);
        scroll.setPreferredSize(new Dimension(790, 420));
        return scroll;
    }

    @Override
    protected ValidationInfo doValidate() {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (Boolean.TRUE.equals(model.getValueAt(row, 0))
                    && !ProjectGlossary.validMeaning(String.valueOf(model.getValueAt(row, 2)).strip())) {
                return new ValidationInfo("勾选的术语需要一条有效的简短释义。", table);
            }
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
            return;
        }
        if (doValidate() != null) {
            return;
        }
        for (int row = 0; row < model.getRowCount(); row++) {
            if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
                String term = (String) model.getValueAt(row, 1);
                String meaning = ((String) model.getValueAt(row, 2)).strip();
                if (model.getValueAt(row, 3) == Scope.GLOBAL) {
                    config.getWordMap().put(term, meaning);
                } else {
                    config.projectMappings(project).put(term, meaning);
                }
            }
        }
        super.doOKAction();
    }
}
