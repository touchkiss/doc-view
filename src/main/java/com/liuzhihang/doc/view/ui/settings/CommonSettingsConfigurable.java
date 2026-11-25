package com.liuzhihang.doc.view.ui.settings;

import java.util.Objects;

import javax.swing.*;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.liuzhihang.doc.view.common.Consts;
import com.liuzhihang.doc.view.common.util.HttpUtil;
import com.liuzhihang.doc.view.service.gpt.ModelRequestException;
import com.liuzhihang.doc.view.service.gpt.impl.OpenAiGptSupplier;
import com.liuzhihang.doc.view.config.EasyDocConfig;
import com.liuzhihang.doc.view.config.EasyDocConfigComponent;
import com.liuzhihang.doc.view.service.translator.TranslatorService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * 通用设置 可配置
 *
 * @author wangchao
 * @date 2019/08/25
 */
public class CommonSettingsConfigurable implements Configurable {

    private static final String HTTP_SCHEME_PREFIX = "http";
    private static final String FROM_PLACEHOLDER = "{from}";
    private static final String TO_PLACEHOLDER = "{to}";
    private static final String QUERY_PLACEHOLDER = "{query}";

    /** 配置 */
    private EasyDocConfig config = ApplicationManager.getApplication().getService(EasyDocConfigComponent.class).getState();
    /** 视图（懒加载：Settings 树 isBeta/asPromo 会实例化 Configurable，不应在此时构建 UI） */
    private CommonSettingsView view;

    private CommonSettingsView getView() {
        if (view == null) {
            view = new CommonSettingsView();
        }
        return view;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "源码注释与 AI";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return getView().getComponent();
    }

    @Override
    public boolean isModified() {
        if (view == null) {
            return false;
        }
        if (!Objects.equals(config.getTranslator(), view.getTranslatorBox().getSelectedItem())) {
            return true;
        }
        if (!Objects.equals(String.valueOf(config.getTimeout()), view.getTimeoutTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getAppId()), view.getAppIdTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getToken()), view.getTokenTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getSecretKey()), view.getSecretKeyTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getSecretId()), view.getSecretIdTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getAccessKeyId()), view.getAccessKeyIdTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getAccessKeySecret()), view.getAccessKeySecretTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getYoudaoAppKey()), view.getYoudaoAppKeyTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getYoudaoAppSecret()), view.getYoudaoAppSecretTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getMicrosoftKey()), view.getMicrosoftKeyTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getMicrosoftRegion()), view.getMicrosoftRegionTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getGoogleKey()), view.getGoogleKeyTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getChatGlmApiKey()), view.getChatGlmApiKeyTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getCustomUrl()), view.getCustomUrlTextField().getText())) {
            return true;
        }
        if (!Objects.equals(config.getCustomHttpMethod(),
            String.valueOf(view.getCustomHttpMethodBox().getSelectedItem()))) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getOpenAiApiKey()), view.getOpenAiApiKeyTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getOpenAiApiUrl()), view.getOpenAiApiUrlTextField().getText())) {
            return true;
        }
        if (!Objects.equals(StringUtils.defaultString(config.getOpenAiModel()), view.getOpenAiModelTextField().getText())) {
            return true;
        }
        return view.isMappingsModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        CommonSettingsView view = getView();
        EasyDocConfig draft = new EasyDocConfig();
        draft.setTranslator(String.valueOf(view.getTranslatorBox().getSelectedItem()));
        draft.setAppId(view.getAppIdTextField().getText());
        draft.setToken(view.getTokenTextField().getText());
        draft.setSecretKey(view.getSecretKeyTextField().getText());
        draft.setSecretId(view.getSecretIdTextField().getText());
        draft.setAccessKeyId(view.getAccessKeyIdTextField().getText());
        draft.setAccessKeySecret(view.getAccessKeySecretTextField().getText());
        draft.setYoudaoAppKey(view.getYoudaoAppKeyTextField().getText());
        draft.setYoudaoAppSecret(view.getYoudaoAppSecretTextField().getText());
        draft.setMicrosoftKey(view.getMicrosoftKeyTextField().getText());
        draft.setMicrosoftRegion(view.getMicrosoftRegionTextField().getText());
        draft.setGoogleKey(view.getGoogleKeyTextField().getText());
        draft.setChatGlmApiKey(view.getChatGlmApiKeyTextField().getText());
        draft.setCustomUrl(StringUtils.strip(view.getCustomUrlTextField().getText()));
        draft.setCustomHttpMethod(String.valueOf(view.getCustomHttpMethodBox().getSelectedItem()));
        draft.setOpenAiApiKey(view.getOpenAiApiKeyTextField().getText());
        draft.setOpenAiApiUrl(view.getOpenAiApiUrlTextField().getText());
        draft.setOpenAiModel(view.getOpenAiModelTextField().getText());
        if (draft.getTranslator() == null || !Consts.ENABLE_TRANSLATOR_SET.contains(draft.getTranslator())) {
            throw new ConfigurationException("请选择正确的翻译方式");
        }
        if (Consts.BAIDU_TRANSLATOR.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getAppId())) {
                throw new ConfigurationException("appId不能为空");
            }
            if (StringUtils.isBlank(draft.getToken())) {
                throw new ConfigurationException("密钥不能为空");
            }
        }
        if (Consts.TENCENT_TRANSLATOR.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getSecretKey())) {
                throw new ConfigurationException("secretKey不能为空");
            }
            if (StringUtils.isBlank(draft.getSecretId())) {
                throw new ConfigurationException("secretId不能为空");
            }
        }
        if (Consts.ALIYUN_TRANSLATOR.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getAccessKeyId())) {
                throw new ConfigurationException("accessKeyId不能为空");
            }
            if (StringUtils.isBlank(draft.getAccessKeySecret())) {
                throw new ConfigurationException("accessKeySecret不能为空");
            }
        }
        if (Consts.YOUDAO_AI_TRANSLATOR.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getYoudaoAppKey())) {
                throw new ConfigurationException("appKey不能为空");
            }
            if (StringUtils.isBlank(draft.getYoudaoAppSecret())) {
                throw new ConfigurationException("appSecret不能为空");
            }
        }
        if (Consts.MICROSOFT_TRANSLATOR.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getMicrosoftKey())) {
                throw new ConfigurationException("microsoftKey不能为空");
            }
        }
        if (Consts.GOOGLE_TRANSLATOR.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getGoogleKey())) {
                throw new ConfigurationException("googleKey不能为空");
            }
        }
        if (Consts.CHATGLM_GPT.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getChatGlmApiKey())) {
                throw new ConfigurationException("apiKey不能为空");
            }
        }
        if (Consts.OPENAI_GPT.equals(draft.getTranslator())) {
            java.net.URI address;
            try {
                address = OpenAiGptSupplier.resolveEndpoint(draft.getOpenAiApiUrl());
            } catch (ModelRequestException e) {
                throw new ConfigurationException(e.getMessage());
            }
            if (StringUtils.isBlank(draft.getOpenAiApiKey()) && !HttpUtil.isLoopback(address.getHost())) {
                throw new ConfigurationException("API 密钥不能为空");
            }
            if (StringUtils.isBlank(draft.getOpenAiModel())) {
                throw new ConfigurationException("API模型不能为空");
            }
        }
        if (Consts.CUSTOM_URL.equals(draft.getTranslator())) {
            if (StringUtils.isBlank(draft.getCustomUrl())) {
                throw new ConfigurationException("自定义地址不能为空");
            }
            if (!draft.getCustomUrl().startsWith(HTTP_SCHEME_PREFIX)) {
                throw new ConfigurationException("自定义地址只支持http或https接口");
            }
            if (!draft.getCustomUrl().contains(FROM_PLACEHOLDER)) {
                throw new ConfigurationException("自定义地址需要包含{from}占位符，请查看说明文档");
            }
            if (!draft.getCustomUrl().contains(TO_PLACEHOLDER)) {
                throw new ConfigurationException("自定义地址需要包含{to}占位符，请查看说明文档");
            }
            if (!draft.getCustomUrl().contains(QUERY_PLACEHOLDER)) {
                throw new ConfigurationException("自定义地址需要包含{query}占位符，请查看说明文档");
            }
        }
        if (StringUtils.isBlank(view.getTimeoutTextField().getText())
            || !view.getTimeoutTextField().getText().matches("^[1-9][0-9]*$")) {
            throw new ConfigurationException("超时时间必须为数字");
        }
        try {
            draft.setTimeout(Integer.parseInt(view.getTimeoutTextField().getText()));
        } catch (NumberFormatException e) {
            throw new ConfigurationException("超时时间超过可用范围");
        }
        applyTranslationSettings(draft);
        view.applyMappings();
        ApplicationManager.getApplication().getService(TranslatorService.class).clearCache();
    }

    private void applyTranslationSettings(EasyDocConfig draft) {
        config.setTranslator(draft.getTranslator());
        config.setAppId(draft.getAppId());
        config.setToken(draft.getToken());
        config.setSecretKey(draft.getSecretKey());
        config.setSecretId(draft.getSecretId());
        config.setAccessKeyId(draft.getAccessKeyId());
        config.setAccessKeySecret(draft.getAccessKeySecret());
        config.setYoudaoAppKey(draft.getYoudaoAppKey());
        config.setYoudaoAppSecret(draft.getYoudaoAppSecret());
        config.setMicrosoftKey(draft.getMicrosoftKey());
        config.setMicrosoftRegion(draft.getMicrosoftRegion());
        config.setGoogleKey(draft.getGoogleKey());
        config.setChatGlmApiKey(draft.getChatGlmApiKey());
        config.setCustomUrl(draft.getCustomUrl());
        config.setCustomHttpMethod(draft.getCustomHttpMethod());
        config.setOpenAiApiKey(draft.getOpenAiApiKey());
        config.setOpenAiApiUrl(draft.getOpenAiApiUrl());
        config.setOpenAiModel(draft.getOpenAiModel());
        config.setTimeout(draft.getTimeout());
    }

    @Override
    public void reset() {
        getView().refresh();
    }
    @Override
    public void disposeUIResources() {
        view = null;
    }
}
