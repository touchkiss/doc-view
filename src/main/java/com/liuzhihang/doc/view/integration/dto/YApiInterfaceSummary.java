package com.liuzhihang.doc.view.integration.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class YApiInterfaceSummary {
    @SerializedName("_id")
    private Long id;
    @SerializedName("catid")
    private Long catId;
    private String path;
    private String method;
}
