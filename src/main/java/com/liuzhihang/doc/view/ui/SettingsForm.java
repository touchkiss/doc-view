package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.liuzhihang.doc.view.config.UrlRewriteRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JTable;
import javax.swing.JLabel;
import java.awt.GridLayout;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * The project-wide behavior settings shown on the Doc View root page.
 */
public final class SettingsForm {

    private final Project project;
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private final JCheckBox lineMarkerCheckBox = new JCheckBox();
    private final JCheckBox includeNormalInterfaceCheckBox = new JCheckBox();

    private final JBTextField curlCopyHostTextField = new JBTextField();
    private final JBTextField curlDocHostTextField = new JBTextField();
    private final JBTextField grpcCurlHostTextField = new JBTextField();
    private final JPanel urlRewritePanel = new JPanel(new BorderLayout());
    private ListTableModel<UrlRewriteRule> urlRewriteTableModel;
    private TableView<UrlRewriteRule> urlRewriteTable;

    public SettingsForm(@NotNull Project project) {
        this.project = project;
        rootPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        lineMarkerCheckBox.setName("lineMarkerCheckBox");
        lineMarkerCheckBox.setText(DocViewBundle.message("settings.doc.setting.line.marker.show"));
        lineMarkerCheckBox.setToolTipText(DocViewBundle.message("settings.doc.setting.line.marker.show.tip"));

        includeNormalInterfaceCheckBox.setName("includeNormalInterfaceCheckBox");
        includeNormalInterfaceCheckBox.setText(DocViewBundle.message("settings.doc.setting.include.normal.interface"));
        includeNormalInterfaceCheckBox.setToolTipText(DocViewBundle.message("settings.doc.setting.include.normal.interface.tip"));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(JBUI.Borders.empty(8, 12));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel behaviorPanel = new JPanel();
        behaviorPanel.setLayout(new BoxLayout(behaviorPanel, BoxLayout.Y_AXIS));
        behaviorPanel.setBorder(JBUI.Borders.emptyBottom(8));
        behaviorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        com.intellij.ui.TitledSeparator separator = new com.intellij.ui.TitledSeparator(
                DocViewBundle.message("settings.page.behavior"));
        separator.setName("settingsBehaviorSeparator");
        ContextHelpLabel guide = SettingsGuideHelp.create(project, "settingsGuideHelpIcon",
                "settings.help.description", "settings.help.open", "/docs/user-guide/settings.md");
        heading.add(separator, BorderLayout.CENTER);
        heading.add(guide, BorderLayout.EAST);
        behaviorPanel.add(heading);
        behaviorPanel.add(lineMarkerCheckBox);
        lineMarkerCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        behaviorPanel.add(wrappingHelp(DocViewBundle.message("settings.doc.setting.line.marker.show.tip"),
                "settingsLineMarkerDescription"));
        JPanel interfaceOption = new JPanel(new BorderLayout());
        interfaceOption.setBorder(JBUI.Borders.emptyBottom(8));
        interfaceOption.setAlignmentX(Component.LEFT_ALIGNMENT);
        includeNormalInterfaceCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        interfaceOption.add(includeNormalInterfaceCheckBox, BorderLayout.WEST);
        behaviorPanel.add(interfaceOption);
        behaviorPanel.add(wrappingHelp(DocViewBundle.message("settings.doc.setting.include.normal.interface.tip"),
                "settingsNormalInterfaceDescription"));
        content.add(behaviorPanel);

        JPanel curlHostPanel = new JPanel(new GridLayout(0, 2, JBUI.scale(8), JBUI.scale(6)));
        curlHostPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        curlHostPanel.setBorder(com.intellij.ui.IdeBorderFactory.createTitledBorder(
                DocViewBundle.message("settings.curl.host")));
        addHostSetting(curlHostPanel, curlCopyHostTextField, "curlCopyHostTextField",
                "settings.curl.host.copy", "settings.curl.host.copy.tip");
        addHostSetting(curlHostPanel, curlDocHostTextField, "curlDocHostTextField",
                "settings.curl.host.doc", "settings.curl.host.doc.tip");
        addHostSetting(curlHostPanel, grpcCurlHostTextField, "grpcCurlHostTextField",
                "settings.curl.host.grpc", "settings.curl.host.grpc.tip");
        content.add(curlHostPanel);
        urlRewritePanel.setName("urlRewritePanel");
        urlRewritePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        urlRewritePanel.setBorder(com.intellij.ui.IdeBorderFactory.createTitledBorder("URL Rewrite"));
        initUrlRewriteTable();
        content.add(urlRewritePanel);

        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, JBUI.scale(8)));
        links.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton supportButton = new JButton(DocViewBundle.message("support.start"));
        supportButton.addActionListener(event -> new SupportForm(project).show());
        links.add(supportButton);
        content.add(links);

        rootPanel.add(content, BorderLayout.NORTH);
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public boolean isModified() {
        Settings settings = Settings.getInstance(project);
        return lineMarkerCheckBox.isSelected() != settings.getLineMarker()
                || includeNormalInterfaceCheckBox.isSelected() != settings.getIncludeNormalInterface()
                || !Objects.equals(curlCopyHostTextField.getText().trim(), settings.getCurlCopyHost())
                || !Objects.equals(curlDocHostTextField.getText().trim(), settings.getCurlDocHost())
                || !Objects.equals(grpcCurlHostTextField.getText().trim(), settings.getGrpcCurlHost())
                || urlRewriteRulesModified(settings);
    }

    public void apply() {
        Settings settings = Settings.getInstance(project);
        settings.setLineMarker(lineMarkerCheckBox.isSelected());
        settings.setIncludeNormalInterface(includeNormalInterfaceCheckBox.isSelected());
        settings.setCurlCopyHost(curlCopyHostTextField.getText().trim());
        settings.setCurlDocHost(curlDocHostTextField.getText().trim());
        settings.setGrpcCurlHost(grpcCurlHostTextField.getText().trim());
        stopEditing(urlRewriteTable);
        settings.setUrlRewriteRules(copyRules(urlRewriteTableModel.getItems()));
    }

    public void reset() {
        Settings settings = Settings.getInstance(project);
        lineMarkerCheckBox.setSelected(settings.getLineMarker());
        includeNormalInterfaceCheckBox.setSelected(settings.getIncludeNormalInterface());
        includeNormalInterfaceCheckBox.setEnabled(true);
        curlCopyHostTextField.setText(settings.getCurlCopyHost());
        curlDocHostTextField.setText(settings.getCurlDocHost());
        grpcCurlHostTextField.setText(settings.getGrpcCurlHost());
        stopEditing(urlRewriteTable);
        urlRewriteTableModel.setItems(copyRules(settings.getUrlRewriteRules()));
    }

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
        urlRewriteTable = tableView;
        tableView.setName("urlRewriteTable");
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

    private static void addHostSetting(JPanel panel, JBTextField field, String name,
                                       String labelKey, String tipKey) {
        field.setName(name);
        field.setToolTipText(DocViewBundle.message(tipKey));
        JLabel label = new JLabel(DocViewBundle.message(labelKey));
        label.setLabelFor(field);
        panel.add(label);
        panel.add(field);
    }

    private static JBTextArea wrappingHelp(String text, String name) {
        JBTextArea help = new JBTextArea(text);
        help.setName(name);
        help.setEditable(false);
        help.setFocusable(false);
        help.setOpaque(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setBorder(JBUI.Borders.empty(2, 0));
        return help;
    }
}
