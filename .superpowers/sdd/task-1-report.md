# Task 1 报告

## 状态

已完成并提交。实现了仅监听 `127.0.0.1` 的 JDK `HttpServer` Streamable HTTP `/mcp` 适配层；MCP 处理使用 SDK 的 protocol/core、stateless transport 和 Jackson JSON mapper API，不使用 Servlet transport/provider，也未引入 Jakarta Servlet、Jetty、Tomcat 或 Spring 运行时依赖。

## 提交

`e7faa90707124fdf03e66ffa30de5d8f11441ee0` (`feat: add local streamable http mcp server`)

提交文件（仅 Task 1 所有）：

- `build.gradle`
- `src/main/resources/META-INF/plugin.xml`
- `src/main/java/com/liuzhihang/doc/view/mcp/McpServerService.java`
- `src/main/java/com/liuzhihang/doc/view/mcp/McpStartupActivity.java`
- `src/test/java/com/liuzhihang/doc/view/mcp/McpServerServiceTest.java`

## 测试

命令：

```bash
./gradlew test --tests 'com.liuzhihang.doc.view.mcp.McpServerServiceTest'
```

结果：通过（BUILD SUCCESSFUL；2 个测试均通过）。测试使用注入的 fake transport，未绑定真实端口；覆盖构造后未启动、启动后的 loopback `/mcp` 端点、重复启动幂等、停止释放 transport，以及 transport 未提供端点时保持 stopped 状态。

另外核验了 `runtimeClasspath`：仅包含 `mcp-core:2.0.0`、`mcp-json-jackson3:2.0.0` 及其 JSON/reactive 传递依赖；无 Jakarta Servlet、Jetty、Tomcat 或 Spring 运行时依赖。

## 疑问与风险

- MCP SDK 2.0.0 的 `mcp-core` JAR 上游自身包含 Servlet 适配类，但本项目排除了 `jakarta.servlet-api` 传递依赖，且没有导入、实例化或加载任何 Servlet transport/provider。若未来 SDK 将 core 与 Servlet transport 拆分为独立 artifact，可进一步缩小 classpath。
- 适配层目前提供一个 `doc_view_status` 健康状态工具，后续 Task 才会接入实际 Doc View 功能。
- 使用 JDK 25 编译时，编译器对 `McpServerService` 给出 deprecated API 提示；这不影响本任务聚焦测试，但后续应在平台/JDK 升级时确认 `com.sun.net.httpserver.HttpServer` 的长期替代方案。

---

## 审查修复（2026-09-21）

### 状态

已修复审查项并提交。`/mcp` 仅接受 `POST`、`Content-Type: application/json`，以及包含 `application/json` 或 `text/event-stream`（且 `q>0`）的 `Accept`。`Host` 必须是当前监听端口上的 loopback authority；`Origin` 缺失可接受，存在时仅接受同端口 loopback HTTP origin。跨站 origin 被拒绝。

JSON-RPC 现在显式区分 request（`method` + `id`）、notification（仅 `method`）和 response（`result`/`error` 且无 `method`）；response 与其他畸形 payload 均返回 400，不会再被当作 request 分发。

新增真实 listener 测试：覆盖非 POST、Content-Type/Accept/Host/Origin、畸形 JSON、response、正常 request/notification，以及停止后端口释放；原有 fake transport 生命周期测试保留。

### 测试

命令：

```bash
./gradlew test --tests 'com.liuzhihang.doc.view.mcp.McpServerServiceTest'
```

输出：

```text
BUILD SUCCESSFUL in 2s
14 actionable tasks: 4 executed, 10 up-to-date
Configuration cache entry reused.
```

### 提交

`974fbc5` (`fix: harden local mcp http adapter`)

提交仅包含：

- `src/main/java/com/liuzhihang/doc/view/mcp/McpServerService.java`
- `src/test/java/com/liuzhihang/doc/view/mcp/McpServerServiceTest.java`

### 剩余 concerns

- JDK 25 仍会报告 `HttpServer` 的 deprecated API 编译提示；该警告来自既有 Task 1 监听方案，未影响本次测试。
- `localhost` 与 IPv6 loopback authority 也被作为 loopback 接受，但服务实际仍仅绑定 `127.0.0.1`。

---

## 二次审查修复（2026-09-21）

### 修复内容

- Streamable HTTP `POST` 现在要求 `Accept` 同时声明 `application/json` 与 `text/event-stream`，且两者的 `q` 值均大于零；缺失、仅单类型、`q=0`、非法 `q` 或格式非法的 Accept 值均返回 `406`。
- 处理全部 `Accept` header 行，而非只读取第一行。
- 纯文本错误响应统一使用 `Content-Type: text/plain; charset=utf-8`；JSON-RPC 成功响应继续使用 `application/json`。
- 保留并未改变 Host、Origin、Content-Type 与 JSON-RPC request/notification/response 分类逻辑。

### 测试

```bash
./gradlew test --tests 'com.liuzhihang.doc.view.mcp.McpServerServiceTest'
```

结果：通过（BUILD SUCCESSFUL；3 个测试均通过）。新增断言覆盖单类型、`q=0`、非法 q、两个正 q 值、多个 Accept header 行，以及文本错误响应的 Content-Type。
