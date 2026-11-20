## ADDED Requirements

### Requirement: 凭据由项目配置保存
YApi/ShowDoc/语雀凭据 SHALL 使用既有项目XML配置保存，不调用PasswordSafe或系统钥匙串。原XML字段 SHALL 能恢复读取并往返持久化；项目 SHALL 相互隔离，特殊字符 SHALL 原样保留，toString和错误日志 SHALL 不泄露凭据。

#### Scenario: 重开项目或沿用旧XML
- WHEN 项目XML已包含token或apiKey/apiToken
- THEN 凭据 SHALL 直接恢复并供上传/MCP使用，不发生授权或凭据存储弹窗

### Requirement: 设置页使用普通配置草稿语义
密码框 SHALL 保持掩码，Reset SHALL 读取保存值，Apply SHALL 保存当前草稿，空值Apply SHALL 清除。取消/Reset SHALL 不提交草稿；无效项目ID SHALL 在任何配置写入前拒绝。

#### Scenario: 编辑或清空凭据
- WHEN 用户修改密码框并Apply，或清空后Apply
- THEN 项目配置 SHALL 相应更新或清空，重新打开 SHALL 展示已保存状态

### Requirement: 上传能力与源码只读保持
UI上传及MCP SHALL 读取当前项目配置，保留upsert、接口详情链接、HTTP加固和取消传播；既有HTTP JSON5功能和业务源码只读 SHALL 不受影响。插件 SHALL 不为迁移读取或删除旧PasswordSafe内容。

#### Scenario: 新配置用于上传
- WHEN 用户保存项目凭据后发起UI或MCP上传
- THEN 上传 SHALL 使用该项目配置凭据，不依赖系统凭据服务，也不改变业务源码
