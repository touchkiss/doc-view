package com.liuzhihang.doc.view.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.UrlRewriteRule;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author liuzhihang
 * @date 2020/2/26 19:17
 */
public class SettingsForm {

    private static final TitledBorder titleTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.title"));
    private static final TitledBorder nameTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.name"));
    private static final TitledBorder descTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.desc"));
    private static final TitledBorder requiredTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.required"));
    private static final TitledBorder exportTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.export"));
    private static final TitledBorder lineMarkerTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.setting"));
    private static final TitledBorder otherTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.doc.other"));
    private static final TitledBorder previewTitleBorder = IdeBorderFactory.createTitledBorder(DocViewBundle.message("settings.preview"));
    private static final TitledBorder urlRewriteTitleBorder = IdeBorderFactory.createTitledBorder("URL Rewrite");

    private final Project project;

    @Getter
    private JPanel rootPanel;

    private LinkLabel<String> supportLinkLabel;

    private JPanel titlePanel;
    private JCheckBox titleCommentTagCheckBox;
    private JCheckBox titleFullClassNameCheckBox;
    private JCheckBox titleSimpleClassNameCheckBox;
    private JCheckBox titleClassCommentCheckBox;

    private JPanel namePanel;
    private JCheckBox nameSwagger3CheckBox;
    private JCheckBox nameSwaggerCheckBox;
    private JCheckBox nameCommentTagCheckBox;
    private JCheckBox nameMethodCommentCheckBox;

    private JPanel docDescPanel;
    private JCheckBox descSwagger3CheckBox;
    private JCheckBox descSwaggerCheckBox;

    private JPanel requirePanel;
    private JCheckBox requireCommentTagCheckBox;
    /**
     * 字段名称是否取 JsonProperty 注解 checkBox
     */
    private JCheckBox fieldNameJsonPropertyCheckBox;
    /**
     * 当没有JsonProperty注解时，是否使用驼峰命名
     */
    private JCheckBox fieldNameSnakeCase;

    private JPanel exportPanel;
    private JCheckBox mergeExportCheckBox;

    private JPanel previewPane;
    private JCheckBox hideLeftCheckBox;

    private JPanel lineMarkerPanel;
    private JCheckBox lineMarkerCheckBox;
    private JCheckBox includeNormalInterfaceCheckBox;
    private JPanel otherPanel;
    private JBTextField prefixSymbol1TextField;
    private JBTextField prefixSymbol2TextField;
    private JCheckBox separateParamCheckBox;

    /**
     * URL 重写规则表格容器, 由 Settings.form 绑定, 内容在代码中动态构建
     */
    private JPanel urlRewritePanel;
    private ListTableModel<UrlRewriteRule> urlRewriteTableModel;

    public SettingsForm(@NotNull Project project) {

        this.project = project;

        supportLinkLabel.setBorder(JBUI.Borders.emptyTop(20));
        supportLinkLabel.setIcon(AllIcons.Actions.Find);

        supportLinkLabel.setListener((source, data) -> new SupportForm(project).show(), null);

        initTitleBorder();
        initUrlRewriteTable();
    }

    private void initTitleBorder() {
        titlePanel.setBorder(titleTitleBorder);
        namePanel.setBorder(nameTitleBorder);
        docDescPanel.setBorder(descTitleBorder);
        requirePanel.setBorder(requiredTitleBorder);
        exportPanel.setBorder(exportTitleBorder);
        lineMarkerPanel.setBorder(lineMarkerTitleBorder);
        otherPanel.setBorder(otherTitleBorder);
        previewPane.setBorder(previewTitleBorder);
        urlRewritePanel.setBorder(urlRewriteTitleBorder);
    }

    /**
     * 构建 URL 重写规则表格 (启用 / 正则 / 替换), 支持增删行.
     */
    private void initUrlRewriteTable() {

        ColumnInfo<UrlRewriteRule, Boolean> enabledColumn = new ColumnInfo<>("Enabled") {
            @Override
            public Boolean valueOf(UrlRewriteRule rule) {
                return rule.enabled;
            }

            @Override
            public Class<?> getColumnClass() {
                return Boolean.class;
            }

            @Override
            public boolean isCellEditable(UrlRewriteRule rule) {
                return true;
            }

            @Override
            public void setValue(UrlRewriteRule rule, Boolean value) {
                rule.enabled = value != null && value;
            }

            @Override
            public int getWidth(JTable table) {
                return 60;
            }
        };

        ColumnInfo<UrlRewriteRule, String> regexColumn = new ColumnInfo<>("Regex") {
            @Override
            public String valueOf(UrlRewriteRule rule) {
                return rule.regex;
            }

            @Override
            public boolean isCellEditable(UrlRewriteRule rule) {
                return true;
            }

            @Override
            public void setValue(UrlRewriteRule rule, String value) {
                rule.regex = value == null ? "" : value;
            }
        };

        ColumnInfo<UrlRewriteRule, String> replacementColumn = new ColumnInfo<>("Replacement") {
            @Override
            public String valueOf(UrlRewriteRule rule) {
                return rule.replacement;
            }

            @Override
            public boolean isCellEditable(UrlRewriteRule rule) {
                return true;
            }

            @Override
            public void setValue(UrlRewriteRule rule, String value) {
                rule.replacement = value == null ? "" : value;
            }
        };

        urlRewriteTableModel = new ListTableModel<>(enabledColumn, regexColumn, replacementColumn);
        urlRewriteTableModel.setItems(copyRules(Settings.getInstance(project).getUrlRewriteRules()));

        TableView<UrlRewriteRule> tableView = new TableView<>(urlRewriteTableModel);
        tableView.getEmptyText().setText("No URL rewrite rules");

        JPanel decorated = ToolbarDecorator.createDecorator(tableView)
                .setAddAction(button -> {
                    stopEditing(tableView);
                    urlRewriteTableModel.addRow(new UrlRewriteRule());
                    int last = urlRewriteTableModel.getRowCount() - 1;
                    tableView.getSelectionModel().setSelectionInterval(last, last);
                })
                .setRemoveAction(button -> {
                    int viewRow = tableView.getSelectedRow();
                    if (viewRow >= 0) {
                        stopEditing(tableView);
                        urlRewriteTableModel.removeRow(tableView.convertRowIndexToModel(viewRow));
                    }
                })
                .createPanel();

        urlRewritePanel.setLayout(new BorderLayout());
        urlRewritePanel.add(decorated, BorderLayout.CENTER);
    }

    private static void stopEditing(TableView<UrlRewriteRule> tableView) {
        if (tableView.isEditing() && tableView.getCellEditor() != null) {
            tableView.getCellEditor().stopCellEditing();
        }
    }

    private static List<UrlRewriteRule> copyRules(List<UrlRewriteRule> source) {
        List<UrlRewriteRule> copy = new ArrayList<>();
        if (source != null) {
            for (UrlRewriteRule rule : source) {
                copy.add(rule.copy());
            }
        }
        return copy;
    }

    private boolean urlRewriteRulesModified(Settings settings) {
        List<UrlRewriteRule> current = urlRewriteTableModel.getItems();
        List<UrlRewriteRule> saved = settings.getUrlRewriteRules();
        if (current.size() != saved.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            UrlRewriteRule a = current.get(i);
            UrlRewriteRule b = saved.get(i);
            if (a.enabled != b.enabled
                    || !Objects.equals(a.regex, b.regex)
                    || !Objects.equals(a.replacement, b.replacement)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否修改，来确定右下角按钮是否亮起
     *
     * @return 是否发生配置变更
     */
    public boolean isModified() {
        Settings settings = Settings.getInstance(project);
        includeNormalInterfaceCheckBox.setEnabled(lineMarkerCheckBox.isSelected());
        return titleCommentTagCheckBox.isSelected() != settings.getTitleUseCommentTag()
                || titleFullClassNameCheckBox.isSelected() != settings.getTitleUseFullClassName()
                || titleSimpleClassNameCheckBox.isSelected() != settings.getTitleUseSimpleClassName()
                || titleClassCommentCheckBox.isSelected() != settings.getTitleClassComment()
                || nameSwagger3CheckBox.isSelected() != settings.getNameUseSwagger3()
                || nameSwaggerCheckBox.isSelected() != settings.getNameUseSwagger()
                || nameCommentTagCheckBox.isSelected() != settings.getNameUseCommentTag()
                || nameMethodCommentCheckBox.isSelected() != settings.getNameMethodComment()
                || descSwagger3CheckBox.isSelected() != settings.getDescUseSwagger3()
                || descSwaggerCheckBox.isSelected() != settings.getDescUseSwagger()
                || requireCommentTagCheckBox.isSelected() != settings.getRequiredUseCommentTag()
                || fieldNameJsonPropertyCheckBox.isSelected() != settings.getFieldNameJsonProperty()
                || fieldNameSnakeCase.isSelected() != settings.getFieldNameCaseType()
                || mergeExportCheckBox.isSelected() != settings.getMergeExport()
                || hideLeftCheckBox.isSelected() != settings.getHideLeft()
                || lineMarkerCheckBox.isSelected() != settings.getLineMarker()
                || includeNormalInterfaceCheckBox.isSelected() != settings.getIncludeNormalInterface()
                || !prefixSymbol1TextField.getText().trim().equals(settings.getPrefixSymbol1())
                || !prefixSymbol2TextField.getText().trim().equals(settings.getPrefixSymbol2())
                || separateParamCheckBox.isSelected() != settings.getSeparateParam()
                || urlRewriteRulesModified(settings)
                ;
    }

    /**
     * 点击 apply 时的动作
     */
    public void apply() {
        Settings settings = Settings.getInstance(project);
        settings.setTitleUseCommentTag(titleCommentTagCheckBox.isSelected());
        settings.setTitleUseFullClassName(titleFullClassNameCheckBox.isSelected());
        settings.setTitleUseSimpleClassName(titleSimpleClassNameCheckBox.isSelected());
        settings.setTitleClassComment(titleClassCommentCheckBox.isSelected());
        settings.setNameUseSwagger3(nameSwagger3CheckBox.isSelected());
        settings.setNameUseSwagger(nameSwaggerCheckBox.isSelected());
        settings.setNameUseCommentTag(nameCommentTagCheckBox.isSelected());
        settings.setNameMethodComment(nameMethodCommentCheckBox.isSelected());
        settings.setDescUseSwagger3(descSwagger3CheckBox.isSelected());
        settings.setDescUseSwagger(descSwaggerCheckBox.isSelected());
        settings.setRequiredUseCommentTag(requireCommentTagCheckBox.isSelected());
        settings.setFieldNameJsonProperty(fieldNameJsonPropertyCheckBox.isSelected());
        settings.setFieldNameCaseType(fieldNameSnakeCase.isSelected());
        settings.setMergeExport(mergeExportCheckBox.isSelected());
        settings.setHideLeft(hideLeftCheckBox.isSelected());
        settings.setLineMarker(lineMarkerCheckBox.isSelected());
        settings.setIncludeNormalInterface(includeNormalInterfaceCheckBox.isSelected());
        settings.setPrefixSymbol1(prefixSymbol1TextField.getText().trim());
        settings.setPrefixSymbol2(prefixSymbol2TextField.getText().trim());
        settings.setSeparateParam(separateParamCheckBox.isSelected());
        settings.setUrlRewriteRules(copyRules(urlRewriteTableModel.getItems()));


        includeNormalInterfaceCheckBox.setEnabled(lineMarkerCheckBox.isSelected());


    }

    /**
     * 点击重置时的动作
     */
    public void reset() {
        Settings settings = Settings.getInstance(project);
        titleCommentTagCheckBox.setSelected(settings.getTitleUseCommentTag());
        titleClassCommentCheckBox.setSelected(settings.getTitleClassComment());
        titleFullClassNameCheckBox.setSelected(settings.getTitleUseFullClassName());
        titleSimpleClassNameCheckBox.setSelected(settings.getTitleUseSimpleClassName());
        nameSwagger3CheckBox.setSelected(settings.getNameUseSwagger3());
        nameSwaggerCheckBox.setSelected(settings.getNameUseSwagger());
        nameCommentTagCheckBox.setSelected(settings.getNameUseCommentTag());
        nameMethodCommentCheckBox.setSelected(settings.getNameMethodComment());
        descSwagger3CheckBox.setSelected(settings.getDescUseSwagger3());
        descSwaggerCheckBox.setSelected(settings.getDescUseSwagger());
        requireCommentTagCheckBox.setSelected(settings.getRequiredUseCommentTag());
        fieldNameJsonPropertyCheckBox.setSelected(settings.getFieldNameJsonProperty());
        fieldNameSnakeCase.setSelected(settings.getFieldNameCaseType());
        mergeExportCheckBox.setSelected(settings.getMergeExport());
        hideLeftCheckBox.setSelected(settings.getHideLeft());
        lineMarkerCheckBox.setSelected(settings.getLineMarker());
        includeNormalInterfaceCheckBox.setSelected(settings.getIncludeNormalInterface());

        includeNormalInterfaceCheckBox.setEnabled(lineMarkerCheckBox.isSelected());
        prefixSymbol1TextField.setText(settings.getPrefixSymbol1());
        prefixSymbol2TextField.setText(settings.getPrefixSymbol2());
        separateParamCheckBox.setSelected(settings.getSeparateParam());
        urlRewriteTableModel.setItems(copyRules(settings.getUrlRewriteRules()));

    }

}
