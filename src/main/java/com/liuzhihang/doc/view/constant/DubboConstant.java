package com.liuzhihang.doc.view.constant;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * dubbo 相关常量
 *
 * @author liuzhihang
 * @date 2022/4/4 20:16
 */
public final class DubboConstant {

    private DubboConstant() {
    }

    public static final String DUBBO_SERVICE = "org.apache.dubbo.config.annotation.DubboService";
    public static final String SERVICE_1 = "org.apache.dubbo.config.annotation.Service";
    public static final String SERVICE_2 = "com.alibaba.dubbo.config.annotation.Service";

    /** 包含类注解名称 */
    public static final Set<String> SERVICE_ANNOTATIONS = new HashSet<>(List.of(DUBBO_SERVICE, SERVICE_1, SERVICE_2));
}
