# 原生 HTTP Client JSON5 增强

已将 REST Client X 合入 Doc View 的同一个安装包。插件 ID 仍是 `com.liuzhihang.doc-view`，代码包为 `com.liuzhihang.doc.view.httpclient`。不需要独立安装 REST Client X；若已安装独立包，请先卸载独立包，安装新 Doc View 后重启 IDEA，避免两个插件同时包装原生动作。

启用 IDEA HTTP Client 后，直接在 `.http` / `.rest` 中编写：

```http
POST http://127.0.0.1:8080/users
Content-Type: application/json

{
  // 用户昵称
  name: '张三',
  // age: 28,
  tags: ['测试',],
}
```

编辑、格式化时保留注释；原生单条 Run、Run All Requests in File 和左上角 Convert to cURL and Copy 输出标准 JSON。保留原生变量/环境、请求顺序、前置脚本、响应脚本与响应显示。数字使用任意精度处理；无效语法、重复键、NaN/Infinity 会报错；复制失败不覆盖剪贴板。

## 范围

- 面向 IntelliJ IDEA 2026.3 Ultimate / build 263。
- `application/json`、`application/*+json` 内联文本请求体，包括 charset 参数和作为变量填写的 Content-Type。
- 不需要新增请求标记，也不更换文件格式。
- 未启用原生 HTTP Client 时，相关扩展与动作不注册，Doc View 原有功能继续加载。
- 外部文件、multipart、GraphQL、WebSocket、gRPC 沿用原生处理。
- 原生预校验仍会检查注释中的 `{{变量}}`：运行时这些变量需已定义。普通 JSON5 注释不受影响；cURL 转换阶段会跳过注释变量。
- cURL 导出与原生行为一致，不执行前置脚本；只在前置脚本中产生的变量不能保证在导出时解析。
- 本版增强普通 Run 和工具栏/菜单的 cURL 动作，不包含 Debug 和 Alt+Enter 独立转换 intention。

完整示例：`src/test/http/json5.http`。

## 开发

`core` 提供独立 JSON5 → JSON 转换；`idea` 包装原生语言注入、执行转换器与 cURL builder。第三方 json5-java 3.0.0 的指数/尾小数点解析由 `Json5Numbers` 适配，避免误改数值。许可随插件 JAR 打包。

构建复用 `/Applications/IntelliJ IDEA.app`。2026.3 的 Protocol Buffers 为独立插件，默认读取当前用户目录中的兼容插件；可用 `-PprotoPluginPath=/path/to/protoeditor` 指定其他安装位置。

运行 HTTP 测试：`./gradlew test --tests 'com.liuzhihang.doc.view.httpclient.*'`。


可选 HTTP Client 缺失回归：`./gradlew httpClientDisabled`。此任务使用独立测试环境禁用 HTTP Client，检查 Doc View 原有服务与菜单仍可加载。

完整验证结果与 API 门禁限制见 [合并验证记录](../merge-http-json5-20261117.md)。
