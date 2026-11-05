# 标准校验注解必填识别

基线：21b5bd4。文档读取业务代码中已有的 `@NotNull`、`@NotEmpty`、`@NotBlank`，两套命名空间 `jakarta.validation.constraints` / `javax.validation.constraints` 均支持，不添加任何注解或插件专属标签。

默认配置在前轮已经包含这六个FQN。本轮真实回归发现两个遗漏：Dubbo方法参数被固定标为非必填；字段与record数组被强转为PsiClassType引发ClassCastException。已分别改为复用标准参数必填识别、先保留数组元数据再解析元素。既有Spring/Swagger显式规则及来源配置不变。

覆盖字段、record、HTTP参数、Dubbo参数、无PSI缓存、Markdown/YApi required标记，以及标量/对象/多维数组。无标准非空约束的Size/Min/Valid或旧插件tag不据此变成必填；业务源文件前后逐字不变。

标准校验不等于实际服务端校验执行：插件只提取声明，不执行验证分组等运行时逻辑。[Jakarta规范](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1)说明NotNull拒绝null，NotEmpty拒绝null/空值，NotBlank拒绝null/空白字符，而Size本身允许null。

验证：最初四例复现Dubbo参数false与两个数组强转异常，修复后集中14项通过。独立复核再补真实JSON5用例，先复现数字数组初始化式被当字符串与多维数组丢层；修复后保留基础类型数字/布尔JSON5和维度，数组使用元素示例，不把Java初始化表达式作为元素文本。

最终统一 `test constantValueTest contentUsingTest buildPlugin verifyPluginStructure verifyPlugin` 退出0。主测试378、常量13、contentUsing3，共394项，失败/错误/跳过均为0。主测试实际运行，部分输入不变任务复用前轮成功结果。IDE Verifier本机IU-262.11879.100为Compatible，严格API门禁开启；OpenSpec严格校验及diff检查通过。

新包：build/distributions/doc-view-1.4.99.zip，SHA-256 `8a4439581bf4e68733921e25a9c1753a825bb7580a6a76a185703b596e647d9d`。日志：/tmp/doc-view-standard-required-verified.log。未安装、推送或进行真实平台上传。
