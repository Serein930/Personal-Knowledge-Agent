# 发布身份只能读取 AgentMind 生产运行秘密，不能枚举、写入或删除其他路径。
path "secret/data/agentmind/production" {
  capabilities = ["read"]
}

path "secret/data/agentmind/tls" {
  capabilities = ["read"]
}
