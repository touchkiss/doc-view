## ADDED Requirements

### Requirement: 未设置默认值标记不作为业务默认值
解析 SHALL 把Spring完整DEFAULT_NONE视为无默认值，不得写入参数表、示例、描述、cURL或上传数据。未指定required时RequestParam/Header SHALL 默认必填；required=false SHALL 可选；真实默认值（包括空串、false、0和常量）SHALL 保留并使参数可选。

#### Scenario: eventBusByBizKey
- WHEN bizKey仅指定绑定名，limit指定required=false，withPayload指定defaultValue=false
- THEN 参数必填 SHALL 依次为true/false/false，默认值 SHALL 依次为无/无/false，任何输出 SHALL 不含DEFAULT_NONE

#### Scenario: 显式标记与空字符串
- WHEN 分别显式指定DEFAULT_NONE、空字符串和false常量
- THEN 只有后两者 SHALL 被视为真实默认值，DEFAULT_NONE SHALL 不产生默认值描述
