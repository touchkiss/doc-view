package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.ShowDocSettings;

import javax.swing.*;
import java.awt.FlowLayout;
import java.util.Arrays;

public class ShowDocSettingForm {
    private JPanel rootPanel;
    private JPanel showDocProjectPanel;
    private JLabel urlLabel;
    private JLabel apiKeyLabel;
    private JLabel apiTokenLabel;
    private JBTextField urlTextField;
    private JBPasswordField apiKeyTextField;
    private JBPasswordField apiTokenTextField;
    private JLabel credentialStorageHint;
    private JPanel integrationHelpContainer;

    private final Project project;

    public ShowDocSettingForm(Project project) {
        this.project = project;
        urlLabel.setText(DocViewBundle.message("showdoc.url"));
        apiKeyLabel.setText(DocViewBundle.message("showdoc.api.key"));
        apiTokenLabel.setText(DocViewBundle.message("showdoc.api.token"));
        apiKeyLabel.setLabelFor(apiKeyTextField);
        apiTokenLabel.setLabelFor(apiTokenTextField);
        credentialStorageHint.setText(DocViewBundle.message("integration.credentials.project.config"));
        integrationHelpContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        integrationHelpContainer.add(SettingsGuideHelp.create(project, "integrationHelpIcon",
                "integration.help.description", "integration.help.open", "/docs/user-guide/integrations.md"));
        showDocProjectPanel.setBorder(IdeBorderFactory.createTitledBorder(DocViewBundle.message("showdoc.project.panel")));
        String secretHint = DocViewBundle.message("integration.credentials.project.config");
        apiKeyTextField.setToolTipText(secretHint);
        apiTokenTextField.setToolTipText(secretHint);
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public boolean isModified() {
        ShowDocSettings settings = ShowDocSettings.getInstance(project);
        return !IntegrationSettingValues.normalizedUrl(urlTextField.getText())
                .equals(IntegrationSettingValues.normalizedUrl(settings.getUrl()))
                || !secret(apiKeyTextField).equals(IntegrationSettingValues.nullToEmpty(settings.getApiKey()))
                || !secret(apiTokenTextField).equals(IntegrationSettingValues.nullToEmpty(settings.getApiToken()));
    }

    public void apply() throws ConfigurationException {
        ShowDocSettings settings = ShowDocSettings.getInstance(project);
        String url = IntegrationSettingValues.normalizedUrl(urlTextField.getText());
        String apiKey = secret(apiKeyTextField);
        String apiToken = secret(apiTokenTextField);

        settings.setUrl(url);
        settings.setApiKey(apiKey);
        settings.setApiToken(apiToken);
        urlTextField.setText(url);
    }

    public void reset() {
        ShowDocSettings settings = ShowDocSettings.getInstance(project);
        urlTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getUrl()));
        apiKeyTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getApiKey()));
        apiTokenTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getApiToken()));
    }

    public void dispose() {
        apiKeyTextField.setText("");
        apiTokenTextField.setText("");
    }

    private static String secret(JBPasswordField field) {
        char[] value = field.getPassword();
        try {
            return new String(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }
}
