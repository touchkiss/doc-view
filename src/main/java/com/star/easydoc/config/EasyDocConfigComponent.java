package com.star.easydoc.config;

import java.util.Objects;

import com.google.common.collect.Maps;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.star.easydoc.common.Consts;
import com.star.easydoc.config.EasyDocConfig.TemplateConfig;
import org.jetbrains.annotations.NotNull;

/**
 * @author wangchao
 * @date 2019/08/25
 */
@State(name = "DocViewSourceComments", storages = @Storage("doc-view-source-comments.xml"))
public class EasyDocConfigComponent implements PersistentStateComponent<EasyDocConfig> {
    /** 配置 */
    private final EasyDocConfig config = createDefaultConfig();

    /**
     * 获取配置
     *
     * @return 配置
     */
    @NotNull
    @Override
    public EasyDocConfig getState() {
        return config;
    }

    static EasyDocConfig createDefaultConfig() {
        EasyDocConfig config = new EasyDocConfig();
        config.setAuthor(System.getProperty("user.name"));
        config.setKdocAuthor(System.getProperty("user.name"));
        config.setDateFormat(Consts.DEFAULT_DATE_FORMAT);
        config.setDocPriority(EasyDocConfig.DOC_FIRST);
        config.setKdocDateFormat(Consts.DEFAULT_DATE_FORMAT);
        config.setSimpleFieldDoc(true);
        config.setKdocSimpleFieldDoc(true);
        config.setKdocParamType(EasyDocConfig.LINK_PARAM_TYPE);
        config.setMethodReturnType(EasyDocConfig.LINK_RETURN_TYPE);
        config.setWordMap(Maps.newTreeMap());
        config.setProjectWordMap(Maps.newTreeMap());
        config.setTranslator(Consts.LOCAL_DICTIONARY);
        config.setTimeout(EasyDocConfig.DEFAULT_TIMEOUT);
        config.setClassTemplateConfig(new TemplateConfig());
        config.setKdocClassTemplateConfig(new TemplateConfig());
        config.setMethodTemplateConfig(new TemplateConfig());
        config.setKdocMethodTemplateConfig(new TemplateConfig());
        config.setFieldTemplateConfig(new TemplateConfig());
        config.setKdocFieldTemplateConfig(new TemplateConfig());
        config.setCoverMode(EasyDocConfig.COVER_MODE_IGNORE);
        return config;
    }

    /**
     * 加载配置
     *
     * @param state 配置
     */
    @Override
    public void loadState(@NotNull EasyDocConfig state) {
        XmlSerializerUtil.copyBean(state, Objects.requireNonNull(getState()));
    }

}
