package com.liuzhihang.doc.view.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 项目级的持久配置
 * <p>
 * <p>
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">IntelliJ Platform SDK DevGuide</a>
 *
 * @author liuzhihang
 * @date 2020/2/27 19:02
 */
@Data
@State(name = "DocViewApplicationSettingsComponent", storages = {@Storage("DocViewApplicationSettings.xml")})
public class ApplicationSettings implements PersistentStateComponent<ApplicationSettings> {

    public static final int DEFAULT_MCP_SERVER_PORT = 12333;

    private String pluginVersion = "0.0.1";
    private Boolean mcpServerEnabled = true;
    private int mcpServerPort = DEFAULT_MCP_SERVER_PORT;


    public static ApplicationSettings getInstance() {
        if (ApplicationManager.getApplication() == null) {
            return null;
        }
        return ApplicationManager.getApplication().getService(ApplicationSettings.class);
    }

    public static ApplicationSettings getInstance(@NotNull Project project) {
        return project.getService(ApplicationSettings.class);
    }


    @Nullable
    @Override
    public ApplicationSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ApplicationSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }


}
