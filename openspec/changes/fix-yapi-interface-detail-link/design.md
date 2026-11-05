# 设计

依据官方 OpenAPI 文档的 GET /api/interface/list_cat（token、catid，page/limit）逐页匹配 method+path。响应行缺少 catid 时使用该请求分类上下文，显式其他分类不得匹配。继续支持直接数组及 data.list 对象，校验必要字段，拒绝重复/重叠页并传播取消。

保存成功响应若 data 对象含有效 _id/id，或 data 是唯一有效接口的数组，写回 save.id；空数组/缺少有效ID时继续分类查找，不把普通写入计数当作ID。已存在有效ID不用重复查。原 String resolve 保留兼容；新增带明确 detail/fallback 状态和固定安全 warning 的结果，UI/MCP可显式展示定位失败。

官方依据：
- https://github.com/YMFE/yapi/blob/master/docs/openapi-doc.html （分类列表接口）
- https://github.com/YMFE/yapi/blob/master/server/controllers/interface.js （save：已有结果为_id数组；新建可能仍返回空数组）
