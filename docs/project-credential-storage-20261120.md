# 项目配置凭据存储回退

基线：master `faaa3521`，工作分支 `codex/config-file-credentials`。用户明确要求不再使用 PasswordSafe，避免系统授权。

## 行为

- 凭据恢复为项目 `.idea` 下既有 XML 文件中的普通字段：YApi 的 `token`、ShowDoc 的 `apiKey/apiToken`、语雀的 `token`。原有 Storage/State 名称保持，旧 XML 字段可直接加载并持久化；模型 toString 排除凭据。
- 三组设置页继续用掩码密码框。Reset 加载保存值，Apply 保存当前值，留空 Apply 即清除；取消/关闭只放弃草稿。无效 YApi 项目 ID 在任何字段写入前拒绝。
- 删除 IntegrationCredentialService、PasswordSafe/credentialStore 调用、异步状态读取、保存 Modal、清空复选框和相关测试依赖。UI/MCP 直接从同一项目设置读取；MCP 仍冻结本批上传配置。
- 不读取、迁移或删除系统凭据。仅存于 PasswordSafe 的凭据需重新填写一次；之后使用配置文件，不触发授权。
- 上传 upsert/接口详情链接、HTTP 加固、脱敏与取消保持。HTTP Client JSON5 及其可选集成描述符没有修改；业务源码仍只读。

## 验证

- 先运行 ProjectCredentialPersistenceTest：5 项中 4 项明确失败（缺少 XML token/apiKey 等字段），1 项 toString 通过；恢复持久字段后通过。
- 配置 XML 往返/旧字段/特殊字符、项目隔离、UI Apply/Reset/Cancel/清空/无效 ID 原子性、上传取消、默认 MCP 工作流读取配置均通过。
- `./gradlew --offline test constantValueTest contentUsingTest buildPlugin verifyPluginStructure --console=plain` 退出0。主测试409项、常量13项、contentUsing3项，共425项零失败/错误/跳过；其中23项覆盖原生 HTTP/JSON5。主套件在首轮实际运行，后续同输入命令复用其成功结果，两个独立套件随后实际运行。
- 包内核查：IntegrationCredentialService.class 不存在；本插件 class 中无 PasswordSafe/credentialStore API 引用；HTTP Client 可选描述符仍存在。
- OpenSpec 严格校验、diff 检查通过，独立只读审查未发现本次回退阻断。

## IDE 验证限制

不能声称严格 Plugin Verifier 通过。首次 Gradle verifyPlugin 因验证器依赖解析未定位可执行文件失败；独立CLI最初还报告可选Proto依赖缺失。随后通过项目已有的 `-PpluginVerifierCli` 指定缓存的Verifier 1.411，Gradle补齐本地Proto依赖后实际完成验证，最终二进制兼容性为Compatible，无缺失依赖兼容性问题，但仍有8项弃用、29项experimental、7项internal、1项override-only用法。严格门禁因弃用/internal/override-only未通过，位置为本轮未修改的HTTP Client等代码。

未为通过门禁关闭检查或加入忽略名单，也没有回退/重写已有 HTTP Client 功能。最终报告在 `build/reports/pluginVerifier/IU-263.7307.200`，日志 `/tmp/doc-view-config-credentials-gradle-verifier.log`；此前的诊断报告保留在 `/tmp/doc-view-project-credentials-verifier/IU-263.7307.200`。

## 交付边界

产物：`build/distributions/doc-view-1.99.99.zip`。没有安装插件、推送远端、真实平台上传或手工 IDE 验收。原 master 和用户已有未跟踪文件未改动；新包需要 IDE 加载后生效。

SHA-256: `633b7e82f652c378013a6647809c14c148165592e68c1afd7e997a57f9a462b7`
