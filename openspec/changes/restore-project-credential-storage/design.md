# 设计

- 恢复现有项目PersistentStateComponent字段：YApiSettings.token、ShowDocSettings.apiKey/apiToken、YuQueSettings.token。沿用原Storage文件及State名称，旧XML可直接加载；toString排除凭据。
- 设置页保留masked密码框，Reset加载配置值，Apply先校验普通项再保存全部草稿，留空Apply即清除；Cancel/Reset不改已保存配置。不保留授权弹窗、读取状态任务、保存modal和PasswordSafe服务。
- UI上传与MCP从同一项目配置读取，MCP仍构造本次上传快照，保留ReadAction边界、upsert/详情链接、取消和错误脱敏。
- 不读取或删除PasswordSafe内容，避免迁移触发系统授权；只有保存在PasswordSafe中的值需用户重新填写一次。凭据明确保存在项目.idea XML内，不自动写入业务源码。
- 测试覆盖旧XML字段、序列化往返、项目隔离、特殊字符、toString脱敏、UI草稿Apply/Reset/Cancel/清空、无效YApi项目ID原子性和上传/MCP取消。
