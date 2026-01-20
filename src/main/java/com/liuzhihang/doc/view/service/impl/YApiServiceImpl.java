package com.liuzhihang.doc.view.service.impl;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.YApiSettings;
import com.liuzhihang.doc.view.config.YApiSettingsConfigurable;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.dto.*;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.integration.YApiFacadeService;
import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YApiHeader;
import com.liuzhihang.doc.view.integration.dto.YApiQuery;
import com.liuzhihang.doc.view.integration.dto.YapiSave;
import com.liuzhihang.doc.view.integration.impl.YApiFacadeServiceImpl;
import com.liuzhihang.doc.view.notification.DocViewNotification;
import com.liuzhihang.doc.view.service.DocViewUploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 上传到 yapi
 * <p>
 * 将 docViewMap 转换为 YApi 对象并调用 YApiFacadeService
 *
 * @author liuzhihang
 * @date 2021/6/8 23:57
 */
@Slf4j
@Service
public final class YApiServiceImpl implements DocViewUploadService {


    @Override
    public boolean checkSettings(@NotNull Project project) {

        YApiSettings apiSettings = YApiSettings.getInstance(project);

        if (StringUtils.isBlank(apiSettings.getUrl())
                || apiSettings.getProjectId() == null
                || StringUtils.isBlank(apiSettings.getToken())) {
            // 说明没有配置 YApi 上传地址, 跳转到配置页面
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.yapi.info.settings"));
            ShowSettingsUtil.getInstance().showSettingsDialog(project, YApiSettingsConfigurable.class);
            return false;
        }
        return true;
    }

    @Override
    public void doUpload(@NotNull Project project, @NotNull DocView docView) {

        try {
            YApiSettings settings = YApiSettings.getInstance(project);

            YApiFacadeService facadeService = ApplicationManager.getApplication().getService(YApiFacadeServiceImpl.class);

            YApiCat cat = getOrAddCat(settings, docView.getDocTitle());

            YapiSave save = new YapiSave();
            save.setYapiUrl(settings.getUrl());
            save.setToken(settings.getToken());
            save.setProjectId(settings.getProjectId());
            save.setCatId(cat.getId());

            if ("Dubbo".equals(docView.getMethod())) {
                // dubbo 接口处理
                save.setPath("/Dubbo/" + docView.getPsiMethod().getName());
                save.setMethod("POST");
            } else {
                save.setMethod(docView.getMethod());
                save.setPath(docView.getPath());
            }
            // 枚举: raw,form,json
            save.setReqBodyType(docView.getContentType().toString().toLowerCase());
            save.setReqBodyForm(new ArrayList<>());
            save.setReqParams(new ArrayList<>());
            save.setReqHeaders(buildReqHeaders(docView.getHeaderList()));
            save.setReqQuery(buildReqQuery(docView.getReqParamList()));
            save.setResBodyType("json");
            save.setResBody(buildJsonSchema(docView.getRespBody().getChildList()));
            save.setMarkdown(buildDesc(docView));
            save.setTitle(docView.getName());

            if (docView.getContentType().equals(ContentTypeEnum.JSON)) {
                save.setReqBodyIsJsonSchema(true);
                save.setReqBodyOther(buildJsonSchema(docView.getReqBody().getChildList()));
            }

            facadeService.save(save);

            String yapiInterfaceUrl = settings.getUrl() + "/project/" + settings.getProjectId() + "/interface/api/cat_" + cat.getId();

            DocViewNotification.uploadSuccess(project, "YApi", yapiInterfaceUrl);
        } catch (Exception e) {
            DocViewNotification.notifyError(project, DocViewBundle.message("notify.yapi.upload.error", e.getMessage()));
            log.error("上传单个文档失败:{}", docView, e);
        }

    }

    /**
     * 构造描述信息
     */
    @NotNull
    private String buildDesc(DocView docView) {
        DocViewData docViewData = new DocViewData(docView);
        return "**接口名称:**\n\n"
                + docView.getName() + "\n\n"
                + "**接口描述:**\n\n"
                + docView.getDesc() + "\n\n"
                + "**请求示例:**\n\n"
                + "```" + docView.getContentType() + "\n" +
                (StringUtils.isBlank(docViewData.getRequestJson5()) ? docView.getReqBodyExample() : docViewData.getRequestJson5()) + "\n" +
                "```" + "\n\n"
                + "**返回示例:**\n\n"
                + "```json\n" +
                (StringUtils.isBlank(docViewData.getResponseJson5()) ? docView.getRespExample() : docViewData.getResponseJson5()) + "\n" +
                "```\n\n";
    }

    /**
     * JsonSchema 信息如下
     * <p>
     * type: 数据类型 object array
     * required: 必填字段列表
     * title:标题
     * description:描述
     * properties: 字段列表
     * <p>
     * items: 数组类型时内部元素
     */
    private String buildJsonSchema(List<Body> bodyList) {

        List<String> requiredList = new LinkedList<>();

        Map<String, Object> properties = new LinkedHashMap<>();

        buildProperties(requiredList, properties, bodyList);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", requiredList);
        schema.put("title", " ");
        schema.put("description", " ");
        schema.put("properties", properties);

        return new Gson().toJson(schema);
    }

    /**
     * {
     * "type": "String",
     * "mock": {
     * "mock": "@string"
     * }
     * }
     */
    private void buildProperties(List<String> requiredList, Map<String, Object> properties, List<Body> bodyList) {

        for (Body body : bodyList) {

            Map<String, Object> innerProperties = new LinkedHashMap<>();

            // 统一到前端类型：string/number/boolean/array/object
            String schemaType = toYapiSchemaType(body);

            // 数组类型：items 需要根据元素类型决定（基础类型用基础类型；对象用 object + properties）
            if ("array".equals(schemaType)) {
                innerProperties.put("type", "array");
                innerProperties.put("description", body.getDesc());

                Map<String, Object> items = buildArrayItemsSchema(body);
                innerProperties.put("items", items);

            } else if ("object".equals(schemaType)) {
                // 对象 / Map
                List<String> objectRequiredList = new LinkedList<>();
                Map<String, Object> objectProperties = new LinkedHashMap<>();
                buildProperties(objectRequiredList, objectProperties, body.getChildList());

                innerProperties.put("type", "object");
                innerProperties.put("required", objectRequiredList);
                innerProperties.put("description", body.getDesc());
                innerProperties.put("properties", objectProperties);

            } else {
                // 基础类型：string/number/boolean
                innerProperties.put("type", schemaType);
                innerProperties.put("description", body.getDesc());
            }

            // 是否必填
            if (Boolean.TRUE.equals(body.getRequired())) {
                requiredList.add(body.getName());
            }
            properties.put(body.getName(), innerProperties);

        }

    }

    /**
     * YApi Schema: string/number/boolean/array/object
     */
    private static String toYapiSchemaType(@NotNull Body body) {
        if (body.isCollection()) {
            return "array";
        }
        if (body.isMap()) {
            return "object";
        }
        String type = body.getType();
        if (StringUtils.isBlank(type)) {
            return "object";
        }
        // 原始/包装数字
        if ("byte".equals(type) || "short".equals(type) || "int".equals(type) || "long".equals(type)
                || "float".equals(type) || "double".equals(type)
                || "Byte".equals(type) || "Short".equals(type) || "Integer".equals(type) || "Long".equals(type)
                || "Float".equals(type) || "Double".equals(type)
                || "BigDecimal".equals(type)) {
            return "number";
        }
        if ("boolean".equals(type) || "Boolean".equals(type)) {
            return "boolean";
        }
        if (type.endsWith("[]")) {
            return "array";
        }
        // 其他已知基础类型（String/Date/...）统一按 string
        if (FieldTypeConstant.FIELD_TYPE.containsKey(type) || CommonClassNames.JAVA_LANG_STRING_SHORT.equals(type)) {
            return "string";
        }
        // 默认对象
        return "object";
    }

    /**
     * 构建数组 items 的 schema。
     * - 若 childList 为空：尝试从 body.type 的泛型推断元素类型，基础类型返回 {type:string/number/boolean}
     * - 若 childList 非空：返回 object + properties（兼容 List<UserDTO>）
     */
    private Map<String, Object> buildArrayItemsSchema(@NotNull Body body) {
        Map<String, Object> items = new LinkedHashMap<>();

        // 有子字段：说明数组元素是对象
        if (CollectionUtils.isNotEmpty(body.getChildList())) {
            List<String> itemRequiredList = new LinkedList<>();
            Map<String, Object> itemProperties = new LinkedHashMap<>();
            buildProperties(itemRequiredList, itemProperties, body.getChildList());

            items.put("type", "object");
            items.put("required", itemRequiredList);
            items.put("description", body.getDesc());
            items.put("properties", itemProperties);
            return items;
        }

        // 无子字段：数组元素为基础类型（例如 List<Integer>）
        String itemType = extractCollectionItemType(body.getType());
        String itemSchemaType = toYapiSchemaType(simpleBodyForType(itemType));

        items.put("type", itemSchemaType);
        items.put("description", body.getDesc());

        // 对于 object 类型，YApi schema 通常还需要 properties，这里给空对象
        if ("object".equals(itemSchemaType)) {
            items.put("properties", new LinkedHashMap<>());
        }

        return items;
    }

    /**
     * 从 List<Integer> / Set<Long> / Collection<Boolean> 提取元素类型名。
     */
    private static String extractCollectionItemType(String type) {
        if (StringUtils.isBlank(type)) {
            return type;
        }
        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) {
            return type;
        }
        String inner = type.substring(lt + 1, gt).trim();
        int comma = inner.indexOf(',');
        if (comma > -1) {
            inner = inner.substring(0, comma).trim();
        }
        return inner;
    }

    /**
     * 仅用于 items 类型推断的轻量 Body。
     */
    private static Body simpleBodyForType(String type) {
        Body b = new Body();
        b.setType(type);
        b.setCollection(false);
        b.setMap(false);
        b.setRequired(false);
        b.setName("");
        b.setDesc("");
        return b;
    }

    private List<YApiQuery> buildReqQuery(List<Param> paramList) {

        if (CollectionUtils.isEmpty(paramList)) {
            return new ArrayList<>();
        }

        return paramList.stream().map(param -> {
            YApiQuery apiQuery = new YApiQuery();
            apiQuery.setName(param.getName());
            apiQuery.setType(param.getType());
            apiQuery.setExample(param.getExample());
            apiQuery.setDesc(param.getDesc());
            apiQuery.setRequired(param.getRequired() ? "1" : "0");
            return apiQuery;
        }).collect(Collectors.toList());

    }

    private List<YApiHeader> buildReqHeaders(List<Header> headerList) {

        if (CollectionUtils.isEmpty(headerList)) {
            return new ArrayList<>();
        }

        return headerList.stream().map(header -> {
            YApiHeader apiHeader = new YApiHeader();
            apiHeader.setName(header.getName());
            apiHeader.setDesc(header.getDesc());
            apiHeader.setValue(header.getValue());
            apiHeader.setRequired(header.getRequired() ? "1" : "0");
            return apiHeader;
        }).collect(Collectors.toList());


    }

    @NotNull
    private YApiCat getOrAddCat(@NotNull YApiSettings settings, @NotNull String name) throws Exception {

        YApiFacadeService facadeService = ApplicationManager.getApplication().getService(YApiFacadeServiceImpl.class);

        // 检查 catId (菜单是否存在)
        List<YApiCat> catMenu = facadeService.getCatMenu(settings.getUrl(), settings.getProjectId(), settings.getToken());

        Optional<YApiCat> catOptional = catMenu.stream()
                .filter(yApiCat -> yApiCat.getName().equals(name))
                .findAny();

        if (catOptional.isPresent()) {
            return catOptional.get();
        }
        YApiCat cat = new YApiCat();
        cat.setYapiUrl(settings.getUrl());
        cat.setProjectId(settings.getProjectId());
        cat.setName(name);
        cat.setToken(settings.getToken());

        return facadeService.addCat(cat);

    }

}
