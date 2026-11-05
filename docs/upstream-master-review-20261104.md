# 上游 master 合并评估

核查日期：2026-11-04。结论：建议吸收上游核心修复和稳定性改造，但需要保留 fork 扩展并逐项适配，不能整文件覆盖或机械解决冲突。

## 1. 对比基线与范围

- 本地：`master`，`f12c48e`（与本次 fetch 后的 `origin/master` 一致）。
- 上游：`git@github.com:liuzhihang/doc-view.git`，`master`，`877f0c9cafefc065a92650d99989b42a53ff1cde`，提交时间 2026-11-03 23:30:03 +0800，记录 1.5.0 发布归档。
- 共同祖先：`89295c3b1cadca30fc45f6061a3ea1043709a221`，2025-08-03。
- 相对共同祖先：本地独有 59 个提交，上游独有 36 个提交；这些数字包含 merge 和文档提交，不等于功能数量。`--cherry-mark` 未标出补丁等价提交，但不代表双方没有功能重叠。
- 上游改动 435 个文件，fork 改动 190 个文件，交集 33 个文件，其中 23 个位于 `src/main/`。上游文件数包含大量 OpenSpec、文档和测试。
- `git merge-tree --write-tree HEAD upstream/master` 预演失败，列出 29 个冲突路径；其中 `CLAUDE.md~HEAD` 是文件类型冲突产生的附加路径，对应 28 个原始路径。

已执行 fetch、历史/源码差异检查和不改工作区的合并预演；未执行 merge、checkout、源码修改、编译或测试。已有未跟踪文件均保留。报告中的行为判断来自静态源码；未声称已在 IDEA 中复现或验证上游修复。

## 2. 优先吸收的更新

| 优先级 | 更新 | 本地现状与收益 | 合并注意事项 |
| --- | --- | --- | --- |
| 高 | 模板 Apply/Reset/Cancel 生命周期 | `TemplateSettingForm.reset()` 直接给持久设置写默认模板，而不是重新载入保存值；有覆盖自定义模板的风险。上游增加生命周期测试与编辑器释放 | 保留 fork 的 POJO 模板、cURL/JSON5 变量和默认模板差异；上游重写 UI 并删除旧 `.form`，不能只复制 Java 文件 |
| 高 | Clear Cache 只清内存 | 本地 `WindowClearAction` 调用 `CustomFileUtils.delete(project, "Doc View")`，后者关闭编辑器并删除 Scratch 路径；上游改为清项目内存缓存 | 与缓存服务一起处理，保留 Scratch 文档及用户编辑内容 |
| 高 | OpenAPI 3 名称、描述、参数元数据 | 本地 `DocViewUtils.getName()` 仍读 `@Operation.name`；上游读取非空 `summary`，并修正空值回退、字符串求值，支持直接参数/Header 的 `@Parameter` | 保留 fork 的注释增强、示例、Jackson、Jakarta 支持；迁入相关契约测试 |
| 高 | Spring 路由数组、`path` 别名、常量 | 本地路由方法只调用 `getStringAttributeValue(annotation, "value")`；上游取数组首项并支持别名与常量，避免路径丢失 | 本地也已加入参数名称常量解析，两者不是同一个修复；保留 URL 重写和 cURL 路径行为 |
| 高 | Javadoc 参数精确匹配、多行描述 | 本地 `paramDocComment()` 仍用 `startsWith("@param " + name)`，且遇换行截断；上游按完整名称匹配并共享 Spring/Dubbo 解析 | 不能覆盖 fork 的 `@see`、`@link`、枚举与 record 注释增强 |
| 高 | 后台生成、取消、预览资源释放、目录刷新 | 本地预览构造函数同步生成；上游处理后台生成、过期回调、关闭后回调、编辑器释放、重复刷新节点与排序 | 属于连贯改造，不能只合 `PreviewForm` 或几个后台任务调用；保留本地 Markdown/JCEF 兼容修复 |
| 高 | DTO 深度/递归保护、缓存代际与取消 | 上游增加默认深度 8、取消检查，以及随源码/设置/模板变化失效的 schema 缓存；本地没有该完整机制 | 需适配 fork 的集合扁平结构、Jackson `contentUsing`、record、Proto；深度限制也会改变深层 DTO 输出 |
| 高 | 平台凭据与 HTTP 上传加固 | 本地 `YApiSettings.token` 仍序列化到项目 XML；`HttpUtils` 未配置显式等待上限，异常可能携带响应正文，客户端未按上游方式关闭。上游迁入 PasswordSafe，限制网络等待、重定向和重试，严格检查响应并减少敏感日志 | 必须把 MCP 上传、YApi 工厂和通知一并适配，详见下一节；这是凭据存储和错误契约变更 |
| 中高 | IDEA 内部/弃用 API 清理与验证门禁 | 本地仍有 `ActionToolbarImpl`、启动通知的 `PluginManagerCore`、旧索引调用；上游替换相关调用并增加 Plugin Verifier 门禁 | 本地独有 `ProtoPluginSupport` 也使用 `PluginManagerCore`，上游未覆盖；上游 2026.1 验证不能替代本地 2026.2 验证 |
| 中 | 集合/Map Markdown 展示、版本信息与依赖源码注释 | 上游增加 `BodyTableData` 展示投影，仅隐藏合成 element/key/value，保留真实同名字段；支持方法/字段 `@since`、`@version` 与已关联依赖源码注释；修正描述含标签文字被清空 | fork 已有版本列和集合处理，属部分重叠；fork 某些集合解析直接扁平化，不能再次无条件扁平；必须保留 JSON5 和 YApi schema |
| 中 | Doc Editor 复制字段表、项目文档导出 | 上游新增编辑值复制 Markdown，以及按模块/类组织的项目导出、索引、源码链接、冲突保护 | 按需求引入；项目导出依赖后台快照和文件身份/写入保护，不能只拿菜单入口 |

主要来源提交：

- [e9dab95：后台生成与缓存基线](https://github.com/liuzhihang/doc-view/commit/e9dab95)
- [545d1ea：移除内部工具栏 API](https://github.com/liuzhihang/doc-view/commit/545d1ea)
- [2833ad4](https://github.com/liuzhihang/doc-view/commit/2833ad4)、[db40901](https://github.com/liuzhihang/doc-view/commit/db40901)：启动通知内部 API 清理。
- [7b70ed4：1.3.13，包含 summary 与契约测试](https://github.com/liuzhihang/doc-view/commit/7b70ed4)
- [cc03fc1：1.4.0 综合改造](https://github.com/liuzhihang/doc-view/commit/cc03fc1)
- [9a4ee80：1.5.0 问题修复](https://github.com/liuzhihang/doc-view/commit/9a4ee80)

这些提交中部分是大型综合提交；表格是按行为拆分的吸收建议，不是可直接按顺序 cherry-pick 的清单。

## 3. 必须人工适配的交叉点

### 3.1 PasswordSafe 与 MCP/YApi 上传

上游删除 `YApiSettings.token`，改由 `IntegrationCredentialService` 读取凭据。本地 `YApiUploadOrchestrator` 在校验、分类查询/创建与错误脱敏等路径多次调用 `settings.getToken()`；`YapiSaveFactory.create()` 也直接读取它。

因此，即便 `YApiSettings.java` 自动合并成功，MCP 和 DTO 工厂也会因 getter 消失无法编译。需要在后台读取凭据，显式传入工厂/编排流程，同时保留本地 upsert、接口 ID 查询、详情链接、结构化结果、ReadAction 边界和脱敏行为。不要把上游旧版 `YApiServiceImpl` 整体覆盖到本地已经拆分的工厂/编排结构上。

上游明确不读取、迁移或删除旧 XML 凭据，升级后需要重新填写。这是上游选择，不代表 fork 必须照搬；实施时应确定迁移体验，并回归空白保留、显式删除、取消和 Apply。

### 3.2 无 PSI 缓存与本地文档数据转换

上游 `DtoSchemaCacheService.getBody/putBody` 使用 `copyBody(..., false)`，副本不保留 `psiElement`。本地 `DocViewData.buildBodyDataList()` 仍从 `body.getPsiElement().getProject()` 取设置。仅引入缓存会使嵌套字段转换存在空指针风险，进而影响 Markdown/JSON5/YApi 描述生成。

必须同步引入上游显式传入 Project 的转换方式，区分用于展示的快照与用于编辑写回的 PSI 数据，并检查本地其他消费者。保留本地 JSON wire type、示例、默认值、集合处理；缓存键、失效规则应覆盖实际影响解析结果的设置。

### 3.3 OpenAPI requiredMode 是行为变化

上游由 `Schema.required` 切换为 `requiredMode`，显式 REQUIRED/NOT_REQUIRED 优先；依赖注解没有 requiredMode 时，编辑器拒绝批量写入。对较老 Swagger 注解依赖，不能当作无行为差异的兼容修复。建议吸收显式模式与安全写入逻辑，再按 fork 的实际依赖决定旧注解回退策略；测试 REQUIRED、NOT_REQUIRED、AUTO、旧依赖与批量确认原子性。

### 3.4 设置和模板 UI 重构

上游将常规、生成规则、模板/输出、平台集成分组，并删除 `Settings.form`、`TemplateSettingForm.form`。本地在这些区域新增 cURL 域名、URL 重写及 POJO 模板，另有 MCP 设置页。需要将其逐项迁到新布局并保留服务/扩展注册，不能以“采用上游 UI”为由丢弃本地设置。

### 3.5 构建与版本策略

- 本地：Java 25、Gradle 9.6.1、IntelliJ Gradle plugin 2.17.0、本机 IDEA 2026.2、MCP SDK 2.0.0、可选 Proto 插件。
- 上游：Java 21、Gradle 9.5.0、IntelliJ Gradle plugin 2.16.0、下载式 IU 2026.1 与验证门禁。
- 上游不是整体工具链升级，不应直接覆盖本地构建文件。优先吸收参数化、自动测试和验证门禁，保留本地实际依赖与自定义测试 source set；是否扩展到 2026.1 应由依赖/API 验证决定。
- 本地 `1.4.99` 的“避免官方市场覆盖”理由已落后于上游 `1.5.0`。它不能作为长期版本隔离保障；实际自动更新还受 IDE 设置、插件 ID/渠道和兼容条件影响，需另行确定版本或插件身份策略。
- 本地未跟踪的 `gradlew.bat` 与上游新增同名文件重叠，实际 merge 前要保留并比较，避免 Git 因未跟踪文件会被覆盖而中止。merge-tree 不检查这一工作区障碍。

## 4. 可暂缓或无需原样合入

- 英中双语 UI、欢迎页、图标和默认工具窗口位置：体验改进，可在核心修复稳定后处理。资源文件/插件注册与 UI 调用要成套迁移。
- `.codex/skills`、上游 `AGENTS.md`、OpenSpec 归档、路线图和发布结果：可参考，不必替换 fork 的维护规则、设计与发布历史。`CLAUDE.md` 在上游改为链接，与本地普通文件存在类型冲突。
- 上游 1.3.14 的 Commons Collections 修复：本地已显式打包 commons-collections4，且使用 `org.apache.commons.collections4.CollectionUtils`，不能认定仍存在上游原始缺类问题。可择机吸收标准集合判断，但无需仅为这个问题搬整个发布提交。
- 上游版本号、Marketplace 发布配置与发布归档：不应直接代表 fork 的版本和交付状态。

## 5. 建议实施顺序与验收

1. 创建隔离整合分支，固定双方 SHA，保留未跟踪文件；避免在日常 master 上直接处理这批冲突。
2. 先合文档正确性修复：summary/描述、Spring 路由、Javadoc 精确匹配、版本标签、依赖注释，保留本地解析能力。
3. 合 Clear Cache、模板生命周期、内部 API 清理，以及相应测试；模板 UI 迁移时保留 POJO/cURL 变量。
4. 成套合后台任务、取消、预览/目录生命周期与缓存，并同步适配无 PSI 快照。
5. 成套合 PasswordSafe、HTTP/平台响应校验，适配本地 YApi upsert/MCP 共用链路。
6. 按需加入项目导出、双语 UI、欢迎页等体验功能。

验收不能只检查冲突标记：需要编译和插件打包，运行吸收的上游契约测试及本地 MCP/常量/contentUsing 测试；回归 REST/Feign/Dubbo/POJO/record/Proto、Jackson 覆盖、JSON5、cURL 域名、URL 重写、YApi 上传及链接。再做 IDEA 2026.2 Plugin Verifier 和真实 UI 检查，覆盖取消/关闭/快速切换、配置 Apply/Reset/Cancel、缓存失效与 Scratch 保留。只有确实需要支持 2026.1 时，再增加该版本验证。

## 6. 冲突文件清单

以下按原始路径统计，共 28 个；另有 `CLAUDE.md~HEAD` 为预演产物，仅存在于 Git 合并结果对象中，没有写到工作区。

```text
.gitignore
CHANGELOG.md
CLAUDE.md
README.md
build.gradle
gradle.properties
gradle/wrapper/gradle-wrapper.properties
openspec/config.yaml
src/main/java/com/liuzhihang/doc/view/action/upload/AbstractUploadAction.java
src/main/java/com/liuzhihang/doc/view/config/ApplicationSettings.java
src/main/java/com/liuzhihang/doc/view/dto/DocViewData.java
src/main/java/com/liuzhihang/doc/view/integration/impl/YApiFacadeServiceImpl.java
src/main/java/com/liuzhihang/doc/view/service/impl/SpringDocViewServiceImpl.java
src/main/java/com/liuzhihang/doc/view/service/impl/YApiServiceImpl.java
src/main/java/com/liuzhihang/doc/view/ui/ParamDocEditorForm.java
src/main/java/com/liuzhihang/doc/view/ui/PreviewForm.java
src/main/java/com/liuzhihang/doc/view/ui/Settings.form
src/main/java/com/liuzhihang/doc/view/ui/SettingsForm.java
src/main/java/com/liuzhihang/doc/view/ui/TemplateSettingForm.form
src/main/java/com/liuzhihang/doc/view/ui/TemplateSettingForm.java
src/main/java/com/liuzhihang/doc/view/ui/treeview/ParamTreeTableView.java
src/main/java/com/liuzhihang/doc/view/utils/CustomPsiCommentUtils.java
src/main/java/com/liuzhihang/doc/view/utils/DocViewUtils.java
src/main/java/com/liuzhihang/doc/view/utils/DubboPsiUtils.java
src/main/java/com/liuzhihang/doc/view/utils/ParamPsiUtils.java
src/main/java/com/liuzhihang/doc/view/utils/SpringPsiUtils.java
src/main/resources/META-INF/plugin.xml
src/main/resources/messages/DocViewBundle.properties
```
