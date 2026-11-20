- [x] 复现旧配置凭据不能加载/持久化
- [x] 恢复项目XML字段和UI直接保存
- [x] 删除PasswordSafe服务和上传/MCP依赖
- [x] 执行回归、构建、结构和IDE验证，记录未通过的严格API门禁

## 验收

425项测试零失败/错误/跳过（含23项原生HTTP/JSON5）；构建、结构、OpenSpec严格校验、产物无凭据存储API核查通过，独立审查未发现本次回退阻断。IDE验证二进制Compatible，但未修改模块的弃用/internal/override-only用法仍使严格门禁失败，未关闭检查或掩盖结果。详情见 docs/project-credential-storage-20261120.md。
