- [x] 核查本地扩展并解决冲突
- [x] 补充交叉回归测试（ForkDocumentUiCompatibilityTest，7 个用例）
- [x] 执行编译/测试并记录结果（由 root 统一运行）

## 合并决定与保留证据
- DocViewData 保留 requestJson5/responseJson5/curlExample、NONE_POJO 模板分流和描述换行；显式传 Project 避免无 PSI 缓存空指针，删除敏感文档全文日志。
- 业务集合与合成层采用 syntheticContainerNode 区分，真实 collection/map 不再被上游字段表投影误删；JSON5 只折叠标记的 element 节点。
- SettingsForm 保留独立 Copy/文档/gRPC 域名与 URL 重写草稿/深拷贝；snake_case 移入生成规则页。上游的行标记/普通接口选项独立性保留。
- POJO 模板作为第三框架 tab 保留，加入 Reset/Apply/Restore Defaults 草稿、预览、变更监听和资源释放。
- 删除旧 Settings.form/TemplateSettingForm.form 前已迁移其所有生效设置；不引入欢迎页、双语资源和图标改版。
- 上游模板生命周期测试的编辑器数量由 3 调整为 4，框架 tab 由 2 调整为 3，生成规则 checkbox 由 12 调整为 13，分别对应保留 POJO 和 snake_case，而非削弱验证。
- 单语言范围下移除模板体验测试的双语断言，仍检查变量名称/描述可用，保留其余 UI 行为断言。

## 用户指南同步
- [x] 保留并更新 docs/user-guide/templates.md，以 fork 当前资源逐字同步 Spring/Dubbo/POJO 默认模板。
- [x] 说明三份模板生命周期、POJO/record/Proto 路由、JSON5/cURL 变量、独立域名与 URL 重写，以及 YapiSaveFactory 固定正文边界。
- [x] TemplateGuideExamplesTest 增加第三份默认模板匹配与文档 JSON5/cURL 示例执行用例；测试等待 root 统一运行。

## 最终统一验证
主测试342 + 常量13 + contentUsing3共358项零失败/错误/跳过；buildPlugin、verifyPluginStructure、IU-262.11879.100 Plugin Verifier及三个OpenSpec严格校验通过。详见 docs/upstream-merge-validation-20261105.md；未进行真实外部平台上传或手工IDE GUI验收。
