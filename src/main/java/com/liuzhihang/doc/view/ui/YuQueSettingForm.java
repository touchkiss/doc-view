package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.YuQueSettings;

import javax.swing.*;
import java.awt.FlowLayout;
import java.util.Arrays;

/**
 * @author liuzhihang
 * @date 2022/4/3 11:25
 */
public class YuQueSettingForm {
    private JPanel rootPanel;
    private JPanel yuQueProjectPanel;
    private JLabel urlLabel;
    private JLabel apiUrlLabel;
    private JLabel loginLabel;
    private JLabel reposLabel;
    private JLabel tokenLabel;
    private JBTextField urlTextField;
    private JBTextField loginTextField;
    private JBTextField reposTextField;
    private JBPasswordField tokenTextField;
    private JBTextField apiUrlTextField;
    private JLabel credentialStorageHint;
    private JPanel integrationHelpContainer;

    private final Project project;

    public YuQueSettingForm(Project project) {
        this.project = project;
        urlLabel.setText(DocViewBundle.message("yuque.url"));
        apiUrlLabel.setText(DocViewBundle.message("yuque.api.url"));
        loginLabel.setText(DocViewBundle.message("yuque.username"));
        reposLabel.setText(DocViewBundle.message("yuque.repos"));
        tokenLabel.setText(DocViewBundle.message("yuque.token"));
        tokenLabel.setLabelFor(tokenTextField);
        credentialStorageHint.setText(DocViewBundle.message("integration.credentials.project.config"));
        integrationHelpContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        integrationHelpContainer.add(SettingsGuideHelp.create(project, "integrationHelpIcon",
                "integration.help.description", "integration.help.open", "/docs/user-guide/integrations.md"));
        yuQueProjectPanel.setBorder(IdeBorderFactory.createTitledBorder(DocViewBundle.message("yapi.project.panel")));
        tokenTextField.setToolTipText(DocViewBundle.message("integration.credentials.project.config"));
        urlTextField.setToolTipText(DocViewBundle.message("yuque.site.url.tip"));
        apiUrlTextField.setToolTipText(DocViewBundle.message("yuque.api.url.tip"));
        loginTextField.setToolTipText(DocViewBundle.message("yuque.login.tip"));
        reposTextField.setToolTipText(DocViewBundle.message("yuque.repos.tip"));
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public boolean isModified() {
        YuQueSettings settings = YuQueSettings.getInstance(project);
        return !IntegrationSettingValues.normalizedUrl(urlTextField.getText())
                .equals(IntegrationSettingValues.normalizedUrl(settings.getUrl()))
                || !IntegrationSettingValues.normalizedUrl(apiUrlTextField.getText())
                .equals(IntegrationSettingValues.normalizedUrl(settings.getApiUrl()))
                || !IntegrationSettingValues.trimmedOrEmpty(loginTextField.getText())
                .equals(IntegrationSettingValues.trimmedOrEmpty(settings.getLogin()))
                || !IntegrationSettingValues.trimmedOrEmpty(reposTextField.getText())
                .equals(IntegrationSettingValues.trimmedOrEmpty(settings.getRepos()))
                || !secret().equals(IntegrationSettingValues.nullToEmpty(settings.getToken()));
    }

    public void apply() throws ConfigurationException {
        YuQueSettings settings = YuQueSettings.getInstance(project);
        String url = IntegrationSettingValues.normalizedUrl(urlTextField.getText());
        String apiUrl = IntegrationSettingValues.normalizedUrl(apiUrlTextField.getText());
        String login = IntegrationSettingValues.trimmedOrEmpty(loginTextField.getText());
        String repos = IntegrationSettingValues.trimmedOrEmpty(reposTextField.getText());
        String token = secret();

        settings.setUrl(url);
        settings.setToken(token);
        settings.setApiUrl(apiUrl);
        settings.setLogin(login);
        settings.setRepos(repos);
        urlTextField.setText(url);
        apiUrlTextField.setText(apiUrl);
        loginTextField.setText(login);
        reposTextField.setText(repos);
    }

    public void reset() {
        YuQueSettings settings = YuQueSettings.getInstance(project);
        urlTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getUrl()));
        apiUrlTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getApiUrl()));
        loginTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getLogin()));
        reposTextField.setText(IntegrationSettingValues.nullToEmpty(settings.getRepos()));
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
