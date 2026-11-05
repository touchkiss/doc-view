# 设计

统一读取显式defaultValue，排除完整Spring哨兵；RequestParam/Header必填判断、参数示例、校验描述共用同一语义。无默认值保持null；显式空串、false、0仍是真实默认值，参数可选。真实16字符注解夹具覆盖用户接口和Header，避免简化夹具掩盖问题。不执行真实平台上传。
