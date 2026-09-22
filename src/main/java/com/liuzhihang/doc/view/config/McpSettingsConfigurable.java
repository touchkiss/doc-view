package com.liuzhihang.doc.view.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBTextField;
import com.liuzhihang.doc.view.mcp.McpServerService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Global settings for the local MCP server. */
public final class McpSettingsConfigurable implements SearchableConfigurable {

    private final JCheckBox enabledCheckBox = new JCheckBox("Enable MCP Server");
    private final JBTextField portTextField = new JBTextField();
    private JPanel panel;

    @NotNull
    @Override
    public String getId() {
        return "liuzhihang.api.doc.McpSettingsConfigurable";
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "MCP Server";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 8, 8);
        panel.add(enabledCheckBox, constraints);

        constraints.gridy++;
        panel.add(new JLabel("Port:"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        panel.add(portTextField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("Local endpoint: http://127.0.0.1:<port>/mcp"), constraints);
        return panel;
    }

    @Override
    public boolean isModified() {
        ApplicationSettings settings = ApplicationSettings.getInstance();
        return enabledCheckBox.isSelected() != Boolean.TRUE.equals(settings.getMcpServerEnabled())
                || !portTextField.getText().trim().equals(String.valueOf(settings.getMcpServerPort()));
    }

    @Override
    public void apply() throws ConfigurationException {
        int port;
        try {
            port = Integer.parseInt(portTextField.getText().trim());
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("MCP server port must be a number between 1 and 65535.");
        }
        if (!McpServerService.isValidPort(port)) {
            throw new ConfigurationException("MCP server port must be between 1 and 65535.");
        }

        ApplicationSettings settings = ApplicationSettings.getInstance();
        settings.setMcpServerEnabled(enabledCheckBox.isSelected());
        settings.setMcpServerPort(port);
        ApplicationManager.getApplication().getService(McpServerService.class).restart();
    }

    @Override
    public void reset() {
        ApplicationSettings settings = ApplicationSettings.getInstance();
        enabledCheckBox.setSelected(Boolean.TRUE.equals(settings.getMcpServerEnabled()));
        portTextField.setText(String.valueOf(settings.getMcpServerPort()));
    }
}
