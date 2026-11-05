# 设计

以双方共同祖先作三方合并；逐项适配 fork 的新增调用和数据契约。合并前后用现有上游测试、本地回归及交叉用例确认，禁止整文件选择上游抹除本地实现。

合并上游解析修复，同时保持 record、Jackson/contentUsing、Proto、注释与 Jakarta 扩展。

## 验证

使用 ForkParsingCompatibilityTest、OpenApi3SchemaRequiredModeTest、OpenApi3ParameterMetadataTest、SpringRouteSelectionTest、ParameterDocumentationTest、GenerationCancellationTraversalTest、SinceVersionContractTest，以及本地 constantValueTest/contentUsingTest。真实 Proto PSI 与完整 IDE 渲染需由整体整合验证覆盖，不把纯解析测试当作实机证明。
