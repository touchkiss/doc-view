package com.liuzhihang.doc.view.ui.treeview;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.dto.DocViewParamData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.EventObject;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

/**
 * @author liuzhihang
 * @date 2021/10/20 19:51
 */
public class ParamTreeTableView extends TreeTableView {

    public static final int REQUIRED_COLUMN = 2;
    public static final int SOURCE_COMMENT_COLUMN = 3;
    public static final int SUPPLEMENT_COLUMN = 4;
    public static final int ACTIONS_COLUMN = 5;

    public static final ColumnInfo[] COLUMN_INFOS = new ColumnInfo[]{
            new TreeColumnInfo(DocViewBundle.message("param.column.name")) {

                @NlsContexts.Tooltip
                @Nullable
                @Override
                public String getTooltipText() {
                    return DocViewBundle.message("param.column.readonly");
                }
            },
            new ColumnInfo(DocViewBundle.message("param.column.type")) {
                @Nullable
                @Override
                public Object valueOf(Object o) {
                    if (o instanceof DefaultMutableTreeNode node
                            && node.getUserObject() instanceof DocViewParamData paramData) {
                        return paramData.getType();
                    }
                    return o;
                }

                @NlsContexts.Tooltip
                @Nullable
                @Override
                public String getTooltipText() {
                    return DocViewBundle.message("param.column.readonly");
                }
            },
            new ColumnInfo("必填") {
                @Nullable
                @Override
                public Object valueOf(Object o) {
                    if (o instanceof DefaultMutableTreeNode node
                            && node.getUserObject() instanceof DocViewParamData paramData) {
                        return paramData.getRequired();
                    }
                    return o;
                }

                @Override
                public int getWidth(JTable table) {
                    return 80;
                }

                @Override
                public String getTooltipText() {
                    return "根据源码和生成设置计算";
                }
            },
            new ColumnInfo("源码注释") {
                @Nullable
                @Override
                public Object valueOf(Object o) {
                    if (o instanceof DefaultMutableTreeNode node
                            && node.getUserObject() instanceof DocViewParamData paramData) {
                        return paramData.getDescription().editableText();
                    }
                    return o;
                }
            },
            new ColumnInfo<DefaultMutableTreeNode, String>("补充说明") {
                @Override
                public String valueOf(DefaultMutableTreeNode node) {
                    return node.getUserObject() instanceof DocViewParamData data
                            ? data.getDescription().supplementaryText() : "";
                }
            },
            new ColumnInfo<DefaultMutableTreeNode, DocViewParamData>("操作") {
                @Override
                public DocViewParamData valueOf(DefaultMutableTreeNode node) {
                    return node.getUserObject() instanceof DocViewParamData data ? data : null;
                }

                @Override
                public int getWidth(JTable table) {
                    return JBUI.scale(95);
                }
            }
    };

    private static final TableCellRenderer SOURCE_COMMENT_RENDERER = new DefaultTableCellRenderer() {
        @Override
        protected void setValue(Object value) {
            String text = Objects.toString(value, "");
            setText(text.isBlank() ? "未写注释" : text);
            setToolTipText(text.isBlank() ? null : text);
        }
    };

    private static final TableCellRenderer SUPPLEMENT_RENDERER = new DefaultTableCellRenderer() {
        @Override
        protected void setValue(Object value) {
            String text = Objects.toString(value, "");
            setText(text);
            setToolTipText(text.isBlank() ? null : text);
        }
    };

    private static final NodeRenderer NODE_RENDERER = new NodeRenderer() {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof DocViewParamData paramData) {
                append(paramData.getName() == null ? "" : paramData.getName());
            }
        }

    };

    private final Predicate<DocViewParamData> canWrite;
    private final RowActionCell rowActionCell;
    private final Function<DocViewParamData, Consumer<String>> beginDescriptionEdit;
    private record DescriptionEdit(DocViewParamData row, String initialValue, Consumer<String> commit) {}
    private DescriptionEdit descriptionEdit;
    private boolean finishingDraftForCopy;

    public ParamTreeTableView(ListTreeTableModelOnColumns treeTableModel, Predicate<DocViewParamData> canWrite,
                              Consumer<DocViewParamData> regenerate,
                              Function<DocViewParamData, Consumer<String>> beginDescriptionEdit) {
        super(treeTableModel);
        this.canWrite = canWrite;
        this.beginDescriptionEdit = beginDescriptionEdit;
        rowActionCell = new RowActionCell(regenerate);
        getTableHeader().setReorderingAllowed(false);
    }

    @Override
    public void setTreeCellRenderer(TreeCellRenderer renderer) {
        super.setTreeCellRenderer(NODE_RENDERER);
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {

        if (column == ACTIONS_COLUMN) {
            return rowActionCell;
        }
        return super.getCellEditor(row, column);
    }


    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        return switch (column) {
            case ACTIONS_COLUMN -> rowActionCell != null ? rowActionCell : super.getCellRenderer(row, column);
            case SOURCE_COMMENT_COLUMN -> SOURCE_COMMENT_RENDERER;
            case SUPPLEMENT_COLUMN -> SUPPLEMENT_RENDERER;
            default -> super.getCellRenderer(row, column);
        };
    }

    @Override
    public boolean editCellAt(int row, int column, EventObject event) {
        boolean wasEditing = isEditing() && getEditingRow() == row && getEditingColumn() == column;
        if (!super.editCellAt(row, column, event)) {
            return false;
        }
        boolean needsDescriptionEdit = !wasEditing || descriptionEdit == null;
        if (column == SOURCE_COMMENT_COLUMN && needsDescriptionEdit) {
            var data = (DocViewParamData) getValueAt(row, ACTIONS_COLUMN);
            String displayed = data.getDescription().editableText();
            var commit = beginDescriptionEdit.apply(data);
            String current = data.getDescription().editableText();
            if (getEditorComponent() instanceof JTextField textField) {
                if (!Objects.equals(displayed, current)) {
                    textField.setText(current);
                }
                current = textField.getText();
            }
            descriptionEdit = new DescriptionEdit(data, current, commit);
        }
        return true;
    }

    public boolean finishEditingForCopy() {
        finishingDraftForCopy = true;
        try {
            return !isEditing() || getCellEditor().stopCellEditing();
        } finally {
            finishingDraftForCopy = false;
        }
    }

    @Override
    public void editingStopped(ChangeEvent event) {
        try {
            super.editingStopped(event);
        } finally {
            descriptionEdit = null;
        }
    }

    @Override
    public void editingCanceled(ChangeEvent event) {
        descriptionEdit = null;
        super.editingCanceled(event);
    }

    @Override
    public Object getValueAt(int row, int column) {
        ListTreeTableModelOnColumns tableModel = (ListTreeTableModelOnColumns) getTableModel();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tableModel.getRowValue(row);
        DocViewParamData paramData = (DocViewParamData) node.getUserObject();
        switch (column) {
            case 0:
                return paramData.getName();
            case 1:
                return paramData.getType();
            case REQUIRED_COLUMN:
                return paramData.getRequired();
            case SOURCE_COMMENT_COLUMN:
                return paramData.getDescription().editableText();
            case SUPPLEMENT_COLUMN:
                return paramData.getDescription().supplementaryText();
            case ACTIONS_COLUMN:
                return paramData;
            default:
                return "";
        }
    }


    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != SOURCE_COMMENT_COLUMN) {
            return;
        }

        var data = (DocViewParamData) getValueAt(row, ACTIONS_COLUMN);
        String description = Objects.toString(value, "");
        var edit = descriptionEdit;
        boolean activeEdit = edit != null && edit.row() == data;
        String initialValue = activeEdit ? edit.initialValue() : data.getDescription().editableText();
        if (!Objects.equals(initialValue, description)) {
            if (finishingDraftForCopy) {
                data.setDescription(data.getDescription().withDraft(description));
            } else {
                var commit = activeEdit ? edit.commit() : beginDescriptionEdit.apply(data);
                commit.accept(description);
            }
        }
        super.setValueAt(data.getDescription().editableText(), row, column);
    }

    @Override
    public boolean isCellEditable(int row, int column) {

        if (column == ACTIONS_COLUMN) {
            return canWrite != null && getValueAt(row, column) instanceof DocViewParamData data && canWrite.test(data);
        }
        return column == SOURCE_COMMENT_COLUMN;
    }

    @Override
    public int getRowHeight() {
        return JBUI.scale(28);
    }

    private final class RowActionCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
        private final ActionButtons renderer = new ActionButtons();
        private final ActionButtons editor = new ActionButtons();
        private final Consumer<DocViewParamData> regenerate;
        private DocViewParamData activeRow;

        private RowActionCell(Consumer<DocViewParamData> regenerate) {
            this.regenerate = regenerate;
            editor.regenerate.addActionListener(event -> perform());
        }

        private void perform() {
            DocViewParamData row = activeRow;
            fireEditingStopped();
            activeRow = null;
            if (row != null && canWrite.test(row)) {
                regenerate.accept(row);
            }
        }

        @Override
        public Object getCellEditorValue() {
            return activeRow;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row, int column) {
            activeRow = value instanceof DocViewParamData data ? data : null;
            editor.update(activeRow != null && canWrite.test(activeRow), table, selected);
            return editor;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            renderer.update(value instanceof DocViewParamData data && canWrite.test(data), table, selected);
            return renderer;
        }
    }

    private static final class ActionButtons extends JPanel {
        private final JButton regenerate = new JButton("重新生成");

        private ActionButtons() {
            super(new FlowLayout(FlowLayout.CENTER, JBUI.scale(3), 0));
            regenerate.setMargin(JBUI.insets(1, 5));
            add(regenerate);
        }

        private void update(boolean enabled, JTable table, boolean selected) {
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            regenerate.setEnabled(enabled);
            setToolTipText(enabled ? null : "当前无法重新生成此行的源码注释");
        }
    }
}
