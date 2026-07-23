package com.liuzhihang.doc.view;

import com.liuzhihang.doc.view.dto.DocView;
import com.liuzhihang.doc.view.dto.Header;
import com.liuzhihang.doc.view.dto.Param;
import com.liuzhihang.doc.view.enums.ContentTypeEnum;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.utils.CurlUtils;

import java.util.List;

/**
 * curl 命令生成自检.
 */
public class CurlUtilsTest {

    public static void main(String[] args) {

        // 1. GET + 路径变量 + 查询参数
        DocView getDoc = baseDoc("GET", "/user/{id}");
        getDoc.setReqParamList(List.of(param("id", "42"), param("active", "true")));
        String curl = CurlUtils.build(getDoc);
        checkContains("GET 路径变量与查询", curl, "curl -X GET");
        checkContains("GET URL", curl, "{{host}}/user/42?active=true");

        // 2. POST JSON body
        DocView postDoc = baseDoc("POST", "/user");
        postDoc.setContentType(ContentTypeEnum.JSON);
        postDoc.setReqBodyExample("{\"name\":\"Tom\"}");
        curl = CurlUtils.build(postDoc);
        checkContains("POST JSON", curl, "-H 'Content-Type: application/json'");
        checkContains("POST body", curl, "-d '{\"name\":\"Tom\"}'");

        // 3. Headers
        DocView headerDoc = baseDoc("GET", "/ping");
        Header header = new Header();
        header.setName("X-Token");
        header.setValue("abc");
        headerDoc.setHeaderList(List.of(header));
        curl = CurlUtils.build(headerDoc);
        checkContains("Header", curl, "-H 'X-Token: abc'");

        // 4. 重写后的路径 (模拟 UrlRewrite 结果已写入 DocView.path)
        DocView rewritten = baseDoc("GET", "/seller/c/order/list");
        curl = CurlUtils.build(rewritten);
        checkContains("重写路径", curl, "{{host}}/seller/c/order/list");

        // 5. Dubbo 类型在 DocViewData 中跳过 curl; CurlUtils 本身不区分框架
        DocView dubbo = baseDoc("POST", "/rpc");
        dubbo.setType(FrameworkEnum.DUBBO);
        check("Dubbo 框架枚举", FrameworkEnum.DUBBO != FrameworkEnum.SPRING);

        // 6. 空 method/path
        check("空路径", "", CurlUtils.build(new DocView()));

        // 7. 单引号转义
        checkContains("转义", CurlUtils.escapeSingleQuoted("it's"), "it'\\''s");

        System.out.println("所有 curl 自检通过 ✓");
    }

    private static DocView baseDoc(String method, String path) {
        DocView docView = new DocView();
        docView.setMethod(method);
        docView.setPath(path);
        docView.setType(FrameworkEnum.SPRING);
        return docView;
    }

    private static Param param(String name, String example) {
        Param param = new Param();
        param.setName(name);
        param.setExample(example);
        return param;
    }

    private static void check(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + " 失败: 期望[" + expected + "] 实际[" + actual + "]");
        }
        System.out.println("[PASS] " + name);
    }

    private static void checkContains(String name, String actual, String fragment) {
        if (actual == null || !actual.contains(fragment)) {
            throw new AssertionError(name + " 失败: 未包含 [" + fragment + "] 实际[" + actual + "]");
        }
        System.out.println("[PASS] " + name + " -> contains " + fragment);
    }
}
