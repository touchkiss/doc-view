# 预览列表切换的PSI读访问修复

基线：`e0fba5c`，分支 `codex/merge-upstream-1.5-core`。

## 根因

用户异常栈显示Swing选择事件在EDT调用 `PreviewForm.applyDocViewList` 内的 `currentDocView.getPsiClass().getQualifiedName()`，此事件没有读锁。EDT身份本身不能保证任何回调都拥有PSI读访问；同文件批量导出快照也直接读取 `PsiClass.getName()`。

## 修复

- 在现有后台生成任务的read action内，同时冻结文档列表与类名快照；用identity map区分每个DocView，不触发可变DTO的结构哈希。
- 当前加载成功且未过期时才向UI发布列表与名称；选择回调和批量导出只读取字符串，不再查询PSI名称。
- 构造时缓存Project；PSI文件/HTML渲染仍在后台read action中进行。关闭清理元数据并阻止迟到回调；刷新只替换本预览自己的选择监听器。
- 保持导出类名、文档列表快照、Markdown/JSON5/cURL、MCP/YApi和业务源码只读边界。

## 回归

新增测试用真实后台加载/渲染和EDT选择/导出流程，通过代理PsiClass记录UI名称读取，不依赖fixture可能隐含的读锁。旧代码下：选择切换2次EDT名称读取、批量导出1次，均明确失败；修复后两条路径为0，后台读名称确认持有read access。覆盖刷新监听器不累积，既有精确导出快照用例继续验证相同文件名/内容。

独立只读审查未发现快照生命周期、取消或导出语义阻断。未手工操作用户运行中的IDE，未安装或推送插件。新包加载后才影响当前IDE。

依据：[IntelliJ线程模型](https://github.com/JetBrains/intellij-sdk-docs/blob/main/topics/basics/architectural_overview/threading/threading_model.md)。

## 验收

统一命令 `./gradlew --offline test constantValueTest contentUsingTest buildPlugin verifyPluginStructure verifyPlugin --console=plain` 退出0，BUILD SUCCESSFUL。主测试372、常量13、contentUsing3，共388项零失败/错误/跳过；最后一轮主测试实际运行，部分未变化任务复用本分支前轮成功结果。Plugin Verifier对本机IU-262.11879.100结果Compatible，内部/弃用API等门禁保持开启。OpenSpec严格校验及diff检查通过。

新包：build/distributions/doc-view-1.4.99.zip，SHA-256 `b4ca7a01835d40ba5f73d6033add1ee3f4d5bcd689769a58a4bf72bf1fcabc01`。日志：/tmp/doc-view-preview-psi-full.log。
