package com.liuzhihang.doc.view.integration.dto;

import lombok.Data;

import java.util.List;

@Data
public class YApiListRespData {
    /**
     * 当前页码
     */
    private Integer count;
    /**
     * 总数
     */
    private Integer total;
    /**
     * 接口列表
     */
    private List<YApiInterfaceSummary> list;
}
