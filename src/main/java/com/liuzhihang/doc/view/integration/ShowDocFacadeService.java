package com.liuzhihang.doc.view.integration;

import com.liuzhihang.doc.view.integration.dto.ShowDocUpdateRequest;
import com.liuzhihang.doc.view.integration.dto.ShowDocUpdateResponse;

/**
 * ShowDoc 文档对接
 * <p>
 * <p>
 * https://www.showdoc.com.cn/page/102098
 *
 * @author liuzhihang
 * @date 2021/7/27 12:43
 */
public interface ShowDocFacadeService {

    /**
     * 上传到 ShowDoc
     *
     * @param request 上传内容
     * @return 上传结果
     * @throws Exception 请求失败或响应处理失败时抛出
     */
    ShowDocUpdateResponse updateByApi(ShowDocUpdateRequest request) throws Exception;

}
