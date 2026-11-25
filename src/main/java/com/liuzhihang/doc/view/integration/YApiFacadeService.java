package com.liuzhihang.doc.view.integration;

import com.liuzhihang.doc.view.integration.dto.YApiCat;
import com.liuzhihang.doc.view.integration.dto.YapiSave;

import java.util.List;
import java.util.Optional;

/**
 * YApi 包装 service
 * <p>
 * https://hellosean1025.github.io/yapi/openapi.html
 *
 * @author liuzhihang
 * @date 2021/6/8 19:20
 */
public interface YApiFacadeService {

    /**
     * 新增接口，或在 DTO 包含接口 ID 时更新接口
     *
     * @param dto 接口内容
     * @throws Exception 请求失败或响应处理失败时抛出
     */
    void save(YapiSave dto) throws Exception;

    /**
     * 获取菜单列表
     *
     * @param yapiUrl
     * @param projectId
     * @param token
     * @return
     * @throws Exception
     */
    List<YApiCat> getCatMenu(String yapiUrl, Long projectId, String token) throws Exception;

    /**
     * 按分类、方法和路径查找已有接口。
     *
     * @param yapiUrl YApi 地址
     * @param projectId 项目 ID
     * @param token 访问令牌
     * @param catId 分类 ID
     * @param method HTTP 方法
     * @param path 接口路径
     * @return 匹配接口的 ID，未找到时为空
     * @throws Exception 请求失败或响应处理失败时抛出
     */
    Optional<Long> findInterfaceId(String yapiUrl, Long projectId, String token,
                                   Long catId, String method, String path) throws Exception;

    /**
     * 添加菜单
     *
     * @param cat
     * @return
     * @throws Exception
     */
    YApiCat addCat(YApiCat cat) throws Exception;
}
