# Doc Editor 注释来源实施计划

**目标** 实现源码注释独立编辑、补充说明独立展示和兼容的文档输出。

- [x] 在 `DocumentOnlyParamEditorTest` 复现补充说明与编辑内容混在一起，以及只有补充说明时应从空白新增注释。
- [x] 增加不可变描述模型与来源片段。在字段、record 组件、接口和参数提取时构造模型，随 Body 树、缓存、表格行及快照传递。
- [x] 修改 Doc Editor 及源码编辑弹窗。验证手改、单行及批量生成使用源码正文，补充说明列不可编辑，新增和写回保持原有引用。
- [x] 运行 `./gradlew test`，检查完整测试结果和 `git diff --check`。

验证通过。本地 IDEA JBR 25 运行 `./gradlew --offline test`，共 460 项测试。
