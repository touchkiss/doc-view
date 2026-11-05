# 修复 Spring 未设置默认值标记

实际Spring ValueConstants.DEFAULT_NONE为16字符，上次合并少了尾部6字符，导致隐式哨兵泄漏到文档并把必填误判为false。修复统一默认值解析，保留JSON5/cURL/Jackson和校验增强。
