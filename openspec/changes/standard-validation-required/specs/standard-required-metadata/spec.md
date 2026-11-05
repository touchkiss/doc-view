## ADDED Requirements

### Requirement: 使用已有标准校验注解判断必填
文档 SHALL 读取jakarta.validation.constraints和javax.validation.constraints下的NotNull、NotEmpty、NotBlank；字段、record、HTTP与Dubbo参数 SHALL 保留识别结果。没有这些约束时 SHALL 继续现有Spring/Swagger等显式规则，Size/Min/Valid本身 SHALL 不被误作非空约束。业务源码 SHALL 不变，不添加插件标签或标准注解。

#### Scenario: 两个命名空间的标准约束
- WHEN 已有业务字段/record/参数声明三种标准约束中的任一种
- THEN 生成的字段表和上传元数据 SHALL 保留必填标记，默认配置 SHALL 无需DocView专属标签

### Requirement: 标准约束数组可生成文档
带标准非空约束的数组 SHALL 保留容器类型、必填及元素字段；解析 SHALL 不将PsiArrayType强转为PsiClassType。

#### Scenario: 标量与对象数组
- WHEN 字段或record使用NotEmpty并声明字符串、基础类型或对象数组
- THEN 容器 SHALL 标为必填，标量 SHALL 不展开实现字段，对象元素 SHALL 保留字段和其必填约束，业务源 SHALL 不被修改

#### Scenario: 数组JSON5示例的值类型与维度
- WHEN 带NotEmpty的字段为基础类型数组或多维对象数组
- THEN JSON5 SHALL 保留数组维度，数字/布尔元素 SHALL 不作为字符串，Java数组初始化语法 SHALL 不作为单个元素值
