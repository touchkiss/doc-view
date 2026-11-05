# 上游核心更新整合计划

依据：docs/upstream-master-review-20261104.md，用户已批准除暂缓项外全部整合，并要求绝不丢失本地功能。

## 全局约束
- 新分支 codex/merge-upstream-1.5-core；base=f12c48e；upstream=877f0c9。
- 保留 MCP/YApi upsert/详情链接、JSON5、cURL/域名、URL 重写、Proto/gRPC、record、Jackson/contentUsing、Jakarta 注解能力。
- 不引入欢迎页、双语资源、图标改版和上游维护流程；因必要核心代码使用的新消息键保留为单语言资源。
- 当前 Java 25、本地 IDEA 2026.2/MCP/Proto 依赖保留；增加上游测试与验证。
- 原有未跟踪文件不得删除、覆盖；只提交明确属于本任务的文件。

## Task 1: 合并与基线
- [x] 记录原工作区与基线测试；保留冲突文件旧版本；执行 no-commit merge。
- [x] 排除暂缓项与相关测试，保留本地维护文档和 openspec。

## Task 2: parsing-compatibility spec
- [x] 合并 utils 的 Spring/JavaDoc/递归保护/OpenAPI/依赖源码改进，保留 fork parser 扩展。
- [x] 添加本地扩展与上游行为交叉测试，验证解析和响应树。

## Task 3: document-ui-compatibility spec
- [x] 合并 DocViewData/模板生命周期/参数编辑/设置 UI，保留 POJO、JSON5、cURL、域名和 URL 重写配置。
- [x] 回归配置 Apply/Reset/Cancel、字段表与模板变量。

## Task 4: integration-runtime spec
- [x] 合并后台预览、缓存、上传、导出及 PasswordSafe，适配 MCP 编排和 YApi factory。
- [x] 修复无 PSI 转换、取消、凭据和本地 JCEF 兼容。
- [x] 添加交叉回归，保持原有 HTTP/MCP 结构化错误契约。

## Task 5: 验收与提交
- [x] 编译、完整测试、constantValueTest/contentUsingTest、插件打包与可用的 Plugin Verifier。
- [x] 独立整分支审查，修复发现的实质问题。
- [x] 核对本地独有文件和功能保留清单，提交 merge 和修复，记录验证边界。

进度与决策记录追加在各 spec/tasks.md 与本文件。

## 验收
358项测试通过；插件构建、结构和本机IDE API验证通过；三个spec严格校验通过。独立审查发现的缓存/合成层/取消/分页问题均已修复并回归。保留新分支、本地提交，不推送、不安装。详情：docs/upstream-merge-validation-20261105.md。
