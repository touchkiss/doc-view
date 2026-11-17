# Merge REST Client X into Doc View

用户要求把已经确认并实现的 REST Client X 能力直接合并到 Doc View；当前会话内执行。

## 范围与实现
- Doc View 插件 ID、版本号和已有文档生成/MCP/上传功能保留。
- 迁入 com.liuzhihang.doc.view.httpclient 源码、20 项测试、JSON5 依赖、第三方许可、示例。
- 主描述符通过 optional HTTP Client 依赖加载独立附加描述符；不打包第二个插件。
- 目标为本机 IDEA 2026.3 / build 263 / Java 25，不为旧版本适配。
- 保留已有 README 修改。合并后若已装独立 REST Client X，安装时需移除独立包，避免两个实现接管同一原生动作。
- 业务源文件只读边界保持原要求。

## 执行清单
- [x] 新增描述符契约测试，验证合并前失败。
- [x] 迁入源码/测试/许可和示例；接入 optional 描述符与 Gradle 依赖。
- [x] 更新本机 SDK 对应的 263 插件 ID；运行迁入测试和 Doc View 既有回归。
- [x] 验证 HTTP Client 不可用时 Doc View 的基础能力可加载。
- [x] 打包与结构验证，检查仍是 Doc View ID 的一个安装包。
- [x] 独立审查、差异检查、交付记录与安装说明。

## 初始状态
目标仓库 master；README 已有用户未提交改动，另有开发工具未跟踪文件，均保留。迁移前基线构建因 IDEA 263 已不存在 `intellij.libraries.misc.plugin` 而失败；先修正本机 SDK 依赖映射再验证功能。


## 最终记录
- 用户后续指定不保留独立插件包名，迁入代码统一为 `com.liuzhihang.doc.view.httpclient`。
- 保留 Doc View ID `com.liuzhihang.doc-view` 与版本 `1.99.99`；未创建第二个插件。
- 最终全量验证 `test constantValueTest contentUsingTest httpClientDisabled buildPlugin verifyPluginStructure` 成功：418 tests / 0 failures / 0 errors / 0 skipped。
- 原生 cURL、Run All、单条运行均实际向本地回环服务发送；前置脚本、响应脚本、请求顺序和源文件保持不变的断言通过。
- 主描述符声明 XML 实际插件 ID `com.intellij.xml`，确保已有 Dubbo DOM 功能在 263 的新模块结构下具备明确依赖。
- API 校验工具 1.411 使用本机 Proto 插件的缓存 ZIP 解析可选依赖。结果 Compatible，无二进制兼容问题；严格 API 门禁因 8 项弃用、7 项内部 API 使用未通过，门禁未降低或过滤。另有 29 项实验性 API 提示。
- 保留已有 README 改动及开发工具未跟踪文件；未提交、未发布、未安装到用户正在运行的 IDE。
- 安装包及详细结果见 `docs/merge-http-json5-20261117.md`。
