# HTTP JSON5 合并验证记录

已将原 REST Client X 功能直接集成到 Doc View，同一个安装包发布，不依赖独立 REST Client X 插件。

- 插件 ID：`com.liuzhihang.doc-view`，名称 `Doc View`，版本 `1.99.99`。
- 新增代码包：`com.liuzhihang.doc.view.httpclient`。
- SDK：本机 IntelliJ IDEA 2026.3 Ultimate / IU-263.7307.200 / Java 25。
- 最低 build：263。未对旧版 IDEA 做适配，也未验证未来大版本。

## 已通过的验证

最终执行：

```sh
./gradlew --offline test constantValueTest contentUsingTest httpClientDisabled buildPlugin verifyPluginStructure
```

结果 `BUILD SUCCESSFUL`。

| 测试集 | 数量 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| 主回归（含新增 HTTP 测试） | 401 | 0 / 0 / 0 |
| 常量解析 | 13 | 0 / 0 / 0 |
| contentUsing | 3 | 0 / 0 / 0 |
| 禁用 HTTP Client 的独立环境 | 1 | 0 / 0 / 0 |
| 合计 | **418** | **0 / 0 / 0** |

HTTP 部分包含纯转换、PSI 注入与格式化、变量和私有环境、动态 Content-Type、Content-Length、复制失败剪贴板保护和原生动作端到端测试。端到端实际调用工具栏 cURL、Run All 以及单条 Run，向随机回环端口发送请求，并断言 cURL 与 IDEA 收包一致、前置和响应脚本正常、请求顺序正确、源文件未改变。

禁用 HTTP Client 的测试不引用 HTTP API 类，实际关闭该插件后验证 Doc View 的服务、预览菜单和原有 Copy cURL 仍加载。主描述符的可选依赖仅在 HTTP Client 启用时加载 `doc-view-http-client.xml`。

结构检查与 ZIP 内容检查通过：只有一个 Doc View 主描述符；新增 HTTP 包共 15 个 class；不存在旧 `com.touchkiss.restclientx` 类；JSON5 及 CommonMark 运行库和第三方许可已打包。

## API 检查的限制

IntelliJ Plugin Verifier 1.411 对当前 IDE 的二进制检查结果为 **Compatible**，无未解析类或缺失依赖问题。Proto 为本机额外安装的可选依赖，验证任务将其 ZIP 放入 Verifier 的依赖缓存，避免验证时缺失 Proto 而产生误报。

**严格 API 门禁未通过**，原因是：

- 8 项弃用 API 使用，包括原有 Doc View 与 HTTP 执行支持包装中的调用。
- 7 项内部 API 使用，集中于原生 HTTP 请求体的注入辅助类。
- 此外工具报告 29 项实验性 API 使用；它们不在原有失败门禁集合中。

未降低 `failureLevel`、未添加忽略列表或隐藏这些报告。当前 263 的实际自动化功能测试通过，不等同于 API 稳定性或其他 IDEA 版本兼容承诺。原始报告位于 `build/reports/pluginVerifier/IU-263.7307.200/`。

可以用 `-PpluginVerifierCli=/path/to/verifier-cli-1.411-all.jar` 指定已下载的工具执行严格检查；`verifyPlugin` 因上述门禁仍会返回失败。

## 工程适配

263 拆分和改名后的插件 ID 已同步；原来由 IDE 间接提供的 CommonMark 改为显式运行依赖。旧 Swing 手工示例和凭据存储 fixture 的依赖限定在测试范围，常量 fixture 更新为当前 SDK 的测试类路径。端到端测试按原生控制台对象的生命周期清理资源，避免轻量测试项目复用时出现重复释放。

已有 README 未提交改动和其他未跟踪开发文件均保留。未提交、未发布，未安装或操作用户正在运行的 IDEA；自动化验证不能替代桌面视觉验收。

## 交付

安装包：`build/distributions/doc-view-1.99.99.zip`，7,340,409 字节。

SHA-256：`1226a4060b4e629bec58a1a7989e21def9f3d410a658fd6fc87c4f0dedb73511`。

若已安装独立 REST Client X，先卸载独立包，再安装新的 Doc View ZIP 并重启 IDEA。已知 HTTP 功能边界见 [使用说明](user-guide/http-json5.md)。
