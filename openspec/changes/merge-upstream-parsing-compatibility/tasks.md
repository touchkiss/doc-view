- [x] 核查 fork 与上游的 ParamPsiUtils/SpringPsiUtils/CustomPsiCommentUtils/DocViewUtils/DubboPsiUtils 差异并逐段解决冲突
- [x] 保留 record、Jackson/contentUsing、Proto getter、Jakarta/javax、字段示例、@see/@link 和参数常量逻辑
- [x] 融合深度上下文、取消、依赖源码注释、版本信息和 OpenAPI 元数据
- [x] 标记合成 element/key/value 节点，真实同名字段不标记
- [x] 增加 ForkParsingCompatibilityTest 八个交叉回归场景；更新 Schema 能力契约测试
- [x] 由整合主任务运行编译和相关测试，记录实际结果

## 决策

- 用户要求绝不丢失 fork 新功能。Schema requiredMode 存在时采用上游优先级；缺失 mode 但存在 required 时保留原有读取和写回。上游“旧依赖一律拒绝”测试改成“完全没有可用成员时先拒绝”，另断言旧 required 写回保留名称、描述和可选状态。
- 合成容器采用显式 syntheticContainerNode，而不是通过 collection/map 或 PSI 是否存在猜测，避免无 PSI 缓存后误删业务节点。
- 生产代码与测试已由主任务统一验证，实际结果见 docs/upstream-merge-validation-20261105.md。

- 全量测试发现并修正无校验约束时额外追加一个空格；有约束的最小/最大/长度/默认值说明继续保留。增加 Jakarta Min 描述与空描述的交叉断言。上游容器层级测试显式设置 camelCase，以隔离 fork 默认 snake_case；注释名字断言保留 fork 原有完整首行，不恢复15字符截断。

## 最终统一验证
主测试342 + 常量13 + contentUsing3共358项零失败/错误/跳过；buildPlugin、verifyPluginStructure、IU-262.11879.100 Plugin Verifier及三个OpenSpec严格校验通过。详见 docs/upstream-merge-validation-20261105.md；未进行真实外部平台上传或手工IDE GUI验收。
