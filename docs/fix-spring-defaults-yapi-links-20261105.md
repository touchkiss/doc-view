# Spring 默认值与 YApi 详情链接修复

基线：`f71f3ae`，分支 `codex/merge-upstream-1.5-core`。本轮不改变已整合功能范围，不推送、不安装、不执行真实平台上传。

## 已确认根因

1. `SpringPsiUtils` 的无默认值标记常量只有10字符，真实Spring `ValueConstants.DEFAULT_NONE` 为16字符，漏掉尾部6个换行/制表符。二进制注解的有效默认值会因此被误当成业务值，污染默认值列并把bizKey错判为非必填。之前简化源注解夹具同样使用了错误的短标记，未覆盖二进制依赖行为。
2. YApi查找使用项目列表`/api/interface/list`，没有使用用户要求的分类列表`/api/interface/list_cat?catid=...`；同时强制每行必须返回catid，导致合法分类响应也可能被拒绝。详情查找异常又被静默降级为分类URL，因此通知仍打开分类。

## 修复后的语义

| 参数 | 必填 | 默认值 |
| --- | --- | --- |
| bizKey | Y | 未设置 |
| limit | N | 未设置 |
| withPayload | N | false |

- RequestParam/Header只将显式、可求值且不等于完整DEFAULT_NONE的值当成业务默认值。显式空字符串、false、0和常量保持有效；无默认值的DTO仍为null，不改变原有渲染约定。
- 必填判断、example和默认值描述复用同一个过滤函数，标记不会进入参数表、cURL或上传参数。
- YApi按分类分页扫描，以method+path匹配；响应可以省略行内catid，显式不同分类不能误匹配；支持直接数组或data.list，以及服务端实际页大小小于请求limit的情况。
- 保存响应包含有效_id/id或唯一对象数组时直接取详情ID；空数组、无效、上下文不符或歧义数据继续分类查找，不能把写入条数当接口ID。
- 保存成功但详情定位失败仍保持created/updated，通知改为警告并明确按钮“打开分类”；MCP结构化成功项也携带warning message，不泄露凭据或响应。
- 分类分页重复、重叠或末页实际数量与count矛盾时报告响应无效，不误认接口不存在后新建。

## 回归设计

- Spring注解/ValueConstants通过真实javac编译JAR，只挂二进制库且没有源码，断言常量长度16和PsiCompiledElement。旧生产代码下三个真实业务用例全部失败；修复后通过，覆盖用户完整参数组合、显式哨兵/空串/常量、Header。
- YApi首先复现错误端点、缺catid、小页分页、保存ID、重叠分页；修复后验证查找及详情链接。另用独立用例复现末页漏计数并补保护。
- 通知测试校验分类降级为WARNING并标注打开分类；详情链接保持普通成功。编排及MCP测试确认保存后获得ID、不多查以及警告向上传递。

## 一手依据

- [Spring ValueConstants](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/web/bind/annotation/ValueConstants.java)
- [Spring RequestParam](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/web/bind/annotation/RequestParam.java)
- [YApi 分类接口文档](https://github.com/YMFE/yapi/blob/master/docs/openapi-doc.html)
- [YApi控制器：save与listByCat](https://github.com/YMFE/yapi/blob/master/server/controllers/interface.js)

## 边界

本次没有对真实YApi执行写入，没有手工验证用户正在运行的IDE通知。源码、自动化测试及新插件包已修复后，仍需让IDE加载新包才能影响当前运行行为。

## 验证结果

- 完整回归：主测试356项、常量13项、contentUsing3项，共372项零失败/错误；命令退出0，日志 `/tmp/doc-view-followup-full.log`。
- 之后对末页计数矛盾用例先观察旧代码失败，再补保护。最终针对性回归69项零失败/错误/跳过，覆盖Spring真实二进制注解、OpenAPI元数据、YApi facade/resolver/编排、通知和MCP；日志 `/tmp/doc-view-followup-verified.log`。
- buildPlugin、verifyPluginStructure、Plugin Verifier通过，本机IU-262.11879.100结果Compatible，内部/弃用API门禁保持开启。
- 两个bugfix spec通过严格校验。产物仍为 `build/distributions/doc-view-1.4.99.zip`（需更新IDE加载才生效）；之前合并记录的旧包散列不代表本轮新包。
