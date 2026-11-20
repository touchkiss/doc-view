package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.YApiSettings;

import javax.swing.*;
import java.awt.FlowLayout;
import java.util.Arrays;

public class YApiSettingForm {
    private JPanel rootPanel;
    private JPanel yapiProjectPanel;
    private JLabel urlLabel;
    private JLabel projectIdLabel;
    private JLabel tokenLabel;
    private JBTextField urlTextField;
    private JBTextField projectIdTextField;
    private JBPasswordField tokenTextField;
    private JLabel credentialStorageHint;
    private JPanel integrationHelpContainer;

    private final Project project;

    public YApiSettingForm(Project project) {
        this.project = project;
        urlLabel.setText(DocViewBundle.message("yapi.url"));
        projectIdLabel.setText(DocViewBundle.message("yapi.project.id"));
        tokenLabel.setText(DocViewBundle.message("yapi.token"));
        tokenLabel.setLabelFor(tokenTextField);
        credentialStorageHint.setText(DocViewBundle.message("integration.credentials.project.config"));
        integrationHelpContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        integrationHelpContainer.add(SettingsGuideHelp.create(project, "integrationHelpIcon",
                "integration.help.description", "integration.help.open", "/docs/user-guide/integrations.md"));
        yapiProjectPanel.setBorder(IdeBorderFactory.createTitledBorder(DocViewBundle.message("yapi.project.panel")));
        tokenTextField.setToolTipText(DocViewBundle.message("integration.credentials.project.config"));
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public boolean isModified() {
        YApiSettings settings = YApiSettings.getInstance(project);
        String projectId = IntegrationSettingValues.trimmedOrEmpty(projectIdTextField.getText());
        String savedProjectId = settings.getProjectId() == null ? "" : settings.getProjectId().toString();
        return !IntegrationSettingValues.normalizedUrl(urlTextField.getText())
                .equals(IntegrationSettingValues.normalizedUrl(settings.getUrl()))
                || !projectId.equals(savedProjectId)
                || !secret().equals(IntegrationSettingValues.nullToEmpty(settings.getToken()));
    }

    public void apply() throws ConfigurationException {
        String url = IntegrationSettingValues.normalizedUrl(urlTextField.getText());
        Long projectId = parseProjectId(projectIdTextField.getText());
        String token = secret();

        YApiSettings settings = YApiSettings.getInstance(project);
        settings.setUrl(url);
        settings.setToken(token);
        settings.setProjectId(projectId);
        urlTextField.setText(url);
        projectIdTextField.setText(projectId == null ? "" : projectId.toString());
    }

    private static Long parseProjectId(String rawProjectId) throws ConfigurationException {
        String projectId = IntegrationSettingValues.trimmedOrEmpty(rawProjectId);
        if (projectId.isEmpty()) {
            return null;
        }
        if (!projectId.matches("[0-9]+")) {
            throw new ConfigurationException(DocViewBundle.message("notify.yapi.project.id"));
        }

        try {
            long parsedProjectId = Long.parseLong(projectId);
            if (parsedProjectId <= 0) {
                throw new ConfigurationException(DocViewBundle.message("notify.yapi.project.id"));
            }
            return parsedProjectId;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException(DocViewBundle.message("notify.yapi.project.id"));
        }
    }

    public void reset() {
        YApiSettings settings = YApiSettings.getInstance(project);
        urlTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getUrl()));
        projectIdTextField.setText(settings.getProjectId() == null ? "" : settings.getProjectId().toString());
        tokenTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getToken()));
    }

    public void dispose() {
        tokenTextField.setText("");
    }

    private String secret() {
        char[] value = tokenTextField.getPassword();
        try {
            return new String(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }
}
