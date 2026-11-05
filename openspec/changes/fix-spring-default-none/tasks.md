- [x] 使用真实完整Spring注解默认值复现
- [x] 修复统一默认值解析并回归输出
- [x] 构建、测试和IDE验证

## 验证结果
完整回归372项零失败；独立审查追加末页计数边界保护后，最终针对性69项零失败/错误/跳过，插件构建、结构检查、IU-262.11879.100 Plugin Verifier Compatible及两个spec严格校验通过。未执行真实YApi写入或安装，见 docs/fix-spring-defaults-yapi-links-20261105.md。
