- [x] 写回归并观察失败：根 agent 运行 YApiFacadeServiceImplTest，21 tests / 5 failed，日志 /tmp/doc-view-yapi-category-red.log；失败覆盖旧端点、缺catid、小页分页、保存ID和部分重叠页。
- [x] 修复分类分页与保存ID解析
- [x] 链接解析显式暴露降级状态：LinkResolution(url, interfaceDetail, warning)，保留String入口。
- [x] 根 agent 统一验证 facade/resolver、编排和通知/MCP结果，并记录结果。

当前没有运行真实外部YApi上传；使用注入请求器及根 agent 的本地HTTP回归验证。

## 验证结果
完整回归372项零失败；独立审查追加末页计数边界保护后，最终针对性69项零失败/错误/跳过，插件构建、结构检查、IU-262.11879.100 Plugin Verifier Compatible及两个spec严格校验通过。未执行真实YApi写入或安装，见 docs/fix-spring-defaults-yapi-links-20261105.md。
