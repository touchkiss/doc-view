package com.star.easydoc.config;

import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.alibaba.fastjson2.annotation.JSONField;

import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.star.easydoc.common.Consts;

/**
 * 持久化配置文件
 *
 * @author wangchao
 * @date 2019/08/25
 */
public class EasyDocConfig {

    /** 方法返回值为code类型 */
    public static final String CODE_RETURN_TYPE = "code";
    /** 方法返回值为link类型 */
    public static final String LINK_RETURN_TYPE = "link";
    /** 方法返回值为doc类型 */
    public static final String DOC_RETURN_TYPE = "doc";

    /** 普通模式 */
    public static final String NORMAL_PARAM_TYPE = "普通模式";
    /** 中括号模式 */
    public static final String LINK_PARAM_TYPE = "中括号模式";

    /** 类注释优先 */
    public static final String DOC_FIRST = "docFirst";
    /** 仅翻译 */
    public static final String ONLY_TRANSLATE = "onlyTranslate";

    /** 覆盖模式 */
    public static final String COVER_MODE_IGNORE = "忽略";
    public static final String COVER_MODE_MERGE = "智能合并";
    public static final String COVER_MODE_FORCE = "强制覆盖";

    /** 版本 */
    private String version;

    /** 覆盖模式 */
    private String coverMode = COVER_MODE_IGNORE;

    /**
     * 作者
     */
    private String author = "admin";
    private String kdocAuthor = "admin";
    /**
     * 日期格式
     */
    private String dateFormat = "yyyy/MM/dd";
    private String kdocDateFormat = "yyyy/MM/dd";
    /**
     * 属性是否使用简单模式
     */
    private Boolean simpleFieldDoc = false;
    private Boolean kdocSimpleFieldDoc = false;
    /**
     * 属性是否使用简单模式
     */
    private String methodReturnType = LINK_RETURN_TYPE;
    /** kdoc 参数类型 */
    private String kdocParamType = LINK_PARAM_TYPE;

    /** 注释优先级 */
    private String docPriority = DOC_FIRST;
    /** 默认超时 */
    public static final int DEFAULT_TIMEOUT = 1000;

    /**
     * 翻译方式
     */
    private String translator = Consts.LOCAL_DICTIONARY;

    /**
     * 翻译方式
     */
    private int timeout = DEFAULT_TIMEOUT;
    /**
     * 百度app id
     */
    private String appId;
    /**
     * 百度密钥
     */
    private String token;
    /**
     * 腾讯secretKey
     */
    private String secretKey;
    /**
     * 腾讯secretId
     */
    private String secretId;
    /**
     * 阿里云accessKeyId
     */
    private String accessKeyId;
    /**
     * 阿里云accessKeySecret
     */
    private String accessKeySecret;
    /**
     * 有道APP_KEY
     */
    private String youdaoAppKey;
    /**
     * 有道APP_SECRET
     */
    private String youdaoAppSecret;
    /**
     * 谷歌key
     */
    private String googleKey;

    /**
     * chatglmApiKey
     */
    private String chatGlmApiKey;
    /** 通用大模型API密钥 */
    private String openAiApiKey;
    /** 通用大模型API地址 */
    private String openAiApiUrl = "https://api.openai.com/v1";
    /** 通用大模型模型名称 */
    private String openAiModel = "gpt-4o";
    /**
     * 微软key
     */
    private String microsoftKey;
    /** Microsoft 区域 */
    private String microsoftRegion;
    /** 自定义URL */
    private String customUrl;
    /** 自定义HTTP方法（GET/POST） */
    private String customHttpMethod = "GET";
    /**
     * 单词映射
     */
    private SortedMap<String, String> wordMap = new TreeMap<>();

    /** 项目级别单词映射 */
    private SortedMap<String, TreeMap<String, String>> projectWordMap = new TreeMap<>();

    /**
     * 类模板配置
     */
    private TemplateConfig classTemplateConfig = new TemplateConfig();
    private TemplateConfig kdocClassTemplateConfig = new TemplateConfig();
    /**
     * 方法模板配置
     */
    private TemplateConfig methodTemplateConfig = new TemplateConfig();
    private TemplateConfig kdocMethodTemplateConfig = new TemplateConfig();
    /**
     * 属性模板配置
     */
    private TemplateConfig fieldTemplateConfig = new TemplateConfig();
    private TemplateConfig kdocFieldTemplateConfig = new TemplateConfig();

    /** 批量生成是否生成类注释 */
    private Boolean genAllClass = true;
    /** 批量生成是否生成方法注释 */
    private Boolean genAllMethod = true;
    /** 批量生成是否生成属性注释 */
    private Boolean genAllField = true;
    /** 批量生成是否递归内部类 */
    private Boolean genAllInnerClass;
    /** 排除的字段名列表（逗号分隔，如：serialVersionUID,log,logger） */
    private String excludeFieldNames = "serialVersionUID";

    public void reset() {
        XmlSerializerUtil.copyBean(EasyDocConfigComponent.createDefaultConfig(), this);
        mergeProject();
    }

    public void mergeProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            getProjectWordMap().computeIfAbsent(projectKey(project), k -> Maps.newTreeMap());
        }
    }

    /**
     * 模板配置
     */
    public static class TemplateConfig {
        /**
         * 是否默认的
         */
        private Boolean isDefault;
        /**
         * 模板
         */
        private String template;
        /**
         * 自定义映射
         */
        private Map<String, CustomValue> customMap;

        public TemplateConfig() {
            isDefault = true;
            template = "";
            customMap = new TreeMap<>();
        }

        public Boolean getIsDefault() {
            return isDefault;
        }

        public void setIsDefault(Boolean isDefault) {
            this.isDefault = isDefault;
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }

        public Map<String, CustomValue> getCustomMap() {
            return customMap;
        }

        public void setCustomMap(Map<String, CustomValue> customMap) {
            this.customMap = customMap;
        }
    }

    /**
     * 自定义值
     */
    public static class CustomValue {
        /**
         * 类型
         */
        private VariableType type;
        /**
         * 值
         */
        private String value;

        public CustomValue() {
        }

        public CustomValue(VariableType type, String value) {
            this.type = type;
            this.value = value;
        }

        public VariableType getType() {
            return type;
        }

        public void setType(VariableType type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * 变量类型
     */
    public enum VariableType {
        /**
         * 固定值
         */
        STRING("固定值"),
        /**
         * groovy脚本
         */
        GROOVY("Groovy脚本");

        private final String desc;

        VariableType(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }

        public static VariableType fromDesc(String desc) {
            for (VariableType value : values()) {
                if (value.desc.equals(desc)) {
                    return value;
                }
            }
            return null;
        }
    }

    public SortedMap<String, TreeMap<String, String>> getProjectWordMap() {
        if (projectWordMap == null) {
            projectWordMap = Maps.newTreeMap();
        }
        return projectWordMap;
    }

    public void setProjectWordMap(SortedMap<String, TreeMap<String, String>> projectWordMap) {
        this.projectWordMap = projectWordMap;
    }

    public String getKdocAuthor() {
        return kdocAuthor;
    }

    public void setKdocAuthor(String kdocAuthor) {
        this.kdocAuthor = kdocAuthor;
    }

    public String getKdocDateFormat() {
        return kdocDateFormat;
    }

    public void setKdocDateFormat(String kdocDateFormat) {
        this.kdocDateFormat = kdocDateFormat;
    }

    public Boolean getKdocSimpleFieldDoc() {
        return kdocSimpleFieldDoc;
    }

    public void setKdocSimpleFieldDoc(Boolean kdocSimpleFieldDoc) {
        this.kdocSimpleFieldDoc = kdocSimpleFieldDoc;
    }

    public TemplateConfig getKdocClassTemplateConfig() {
        return kdocClassTemplateConfig;
    }

    public void setKdocClassTemplateConfig(TemplateConfig kdocClassTemplateConfig) {
        this.kdocClassTemplateConfig = kdocClassTemplateConfig;
    }

    public TemplateConfig getKdocMethodTemplateConfig() {
        return kdocMethodTemplateConfig;
    }

    public void setKdocMethodTemplateConfig(TemplateConfig kdocMethodTemplateConfig) {
        this.kdocMethodTemplateConfig = kdocMethodTemplateConfig;
    }

    public TemplateConfig getKdocFieldTemplateConfig() {
        return kdocFieldTemplateConfig;
    }

    public void setKdocFieldTemplateConfig(TemplateConfig kdocFieldTemplateConfig) {
        this.kdocFieldTemplateConfig = kdocFieldTemplateConfig;
    }

    public String getKdocParamType() {
        return kdocParamType;
    }

    public void setKdocParamType(String kdocParamType) {
        this.kdocParamType = kdocParamType;
    }

    public String getTranslator() {
        return translator;
    }

    public void setTranslator(String translator) {
        this.translator = translator;
    }

    public Boolean getSimpleFieldDoc() {
        return simpleFieldDoc;
    }

    public void setSimpleFieldDoc(Boolean simpleFieldDoc) {
        this.simpleFieldDoc = simpleFieldDoc;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public SortedMap<String, String> getWordMap() {
        if (wordMap == null) {
            wordMap = Maps.newTreeMap();
        }
        return wordMap;
    }

    public static String projectKey(Project project) {
        String basePath = project.getBasePath();
        return basePath != null ? basePath : project.getLocationHash();
    }

    public void migrateLegacyProjectMappings() {
        var openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            String key = projectKey(project);
            String legacyKey = project.getName();
            if (key.equals(legacyKey) || !getProjectWordMap().containsKey(legacyKey)) {
                continue;
            }
            long matches = java.util.Arrays.stream(openProjects).filter(other -> legacyKey.equals(other.getName())).count();
            if (matches == 1) {
                var legacy = getProjectWordMap().remove(legacyKey);
                legacy.forEach(projectMappings(project)::putIfAbsent);
            }
        }
    }

    public Map<String, String> projectMappings(Project project) {
        return getProjectWordMap().computeIfAbsent(projectKey(project), ignored -> new TreeMap<>());
    }

    public Map<String, String> getWordMapWithProject(Project project) {
        Map<String, String> effective = new TreeMap<>();
        mergeMappings(effective, getWordMap());
        if (project != null) {
            mergeMappings(effective, getProjectWordMap().get(projectKey(project)));
        }
        return effective;
    }

    private static void mergeMappings(Map<String, String> effective, Map<String, String> scoped) {
        if (scoped != null) {
            scoped.forEach((key, value) -> effective.put(key.toLowerCase(Locale.ROOT), value));
        }
    }

    public void setWordMap(SortedMap<String, String> wordMap) {
        this.wordMap = wordMap;
    }

    public TemplateConfig getClassTemplateConfig() {
        if (classTemplateConfig == null) {
            classTemplateConfig = new TemplateConfig();
        }
        return classTemplateConfig;
    }

    public void setClassTemplateConfig(TemplateConfig classTemplateConfig) {
        this.classTemplateConfig = classTemplateConfig;
    }

    public TemplateConfig getMethodTemplateConfig() {
        if (methodTemplateConfig == null) {
            methodTemplateConfig = new TemplateConfig();
        }
        return methodTemplateConfig;
    }

    public void setMethodTemplateConfig(TemplateConfig methodTemplateConfig) {
        this.methodTemplateConfig = methodTemplateConfig;
    }

    public TemplateConfig getFieldTemplateConfig() {
        if (fieldTemplateConfig == null) {
            fieldTemplateConfig = new TemplateConfig();
        }
        return fieldTemplateConfig;
    }

    public void setFieldTemplateConfig(TemplateConfig fieldTemplateConfig) {
        this.fieldTemplateConfig = fieldTemplateConfig;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getYoudaoAppKey() {
        return youdaoAppKey;
    }

    public void setYoudaoAppKey(String youdaoAppKey) {
        this.youdaoAppKey = youdaoAppKey;
    }

    public String getYoudaoAppSecret() {
        return youdaoAppSecret;
    }

    public void setYoudaoAppSecret(String youdaoAppSecret) {
        this.youdaoAppSecret = youdaoAppSecret;
    }

    public String getMethodReturnType() {
        return methodReturnType;
    }

    public void setMethodReturnType(String methodReturnType) {
        this.methodReturnType = methodReturnType;
    }

    @JSONField(serialize = false, deserialize = false)
    public boolean isCodeMethodReturnType() {
        return CODE_RETURN_TYPE.equals(methodReturnType);
    }

    @JSONField(serialize = false, deserialize = false)
    public boolean isLinkMethodReturnType() {
        return LINK_RETURN_TYPE.equals(methodReturnType);
    }

    @JSONField(serialize = false, deserialize = false)
    public boolean isDocMethodReturnType() {
        return DOC_RETURN_TYPE.equals(methodReturnType);
    }

    public Boolean getGenAllClass() {
        return genAllClass;
    }

    public void setGenAllClass(Boolean genAllClass) {
        this.genAllClass = genAllClass;
    }

    public Boolean getGenAllMethod() {
        return genAllMethod;
    }

    public void setGenAllMethod(Boolean genAllMethod) {
        this.genAllMethod = genAllMethod;
    }

    public Boolean getGenAllField() {
        return genAllField;
    }

    public void setGenAllField(Boolean genAllField) {
        this.genAllField = genAllField;
    }

    public Boolean getGenAllInnerClass() {
        return genAllInnerClass;
    }

    public void setGenAllInnerClass(Boolean genAllInnerClass) {
        this.genAllInnerClass = genAllInnerClass;
    }

    public String getExcludeFieldNames() {
        return excludeFieldNames;
    }

    public void setExcludeFieldNames(String excludeFieldNames) {
        this.excludeFieldNames = excludeFieldNames;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGoogleKey() {
        return googleKey;
    }

    public void setGoogleKey(String googleKey) {
        this.googleKey = googleKey;
    }

    public String getMicrosoftKey() {
        return microsoftKey;
    }

    public void setMicrosoftKey(String microsoftKey) {
        this.microsoftKey = microsoftKey;
    }

    public String getDocPriority() {
        return docPriority;
    }

    public void setDocPriority(String docPriority) {
        this.docPriority = docPriority;
    }

    public String getChatGlmApiKey() {
        return chatGlmApiKey;
    }

    public void setChatGlmApiKey(String chatGlmApiKey) {
        this.chatGlmApiKey = chatGlmApiKey;
    }

    public String getCoverMode() {
        return coverMode;
    }

    public void setCoverMode(String coverMode) {
        this.coverMode = coverMode;
    }

    public String getMicrosoftRegion() {
        return microsoftRegion;
    }

    public void setMicrosoftRegion(String microsoftRegion) {
        this.microsoftRegion = microsoftRegion;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getCustomUrl() {
        return customUrl;
    }

    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
    }

    public String getCustomHttpMethod() {
        return customHttpMethod;
    }

    public void setCustomHttpMethod(String customHttpMethod) {
        this.customHttpMethod = customHttpMethod;
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public void setOpenAiApiKey(String openAiApiKey) {
        this.openAiApiKey = openAiApiKey;
    }

    public String getOpenAiApiUrl() {
        return openAiApiUrl;
    }

    public void setOpenAiApiUrl(String openAiApiUrl) {
        this.openAiApiUrl = openAiApiUrl;
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public void setOpenAiModel(String openAiModel) {
        this.openAiModel = openAiModel;
    }
}
