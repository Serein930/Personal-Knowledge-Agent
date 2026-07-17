package com.agentmind.common.ratelimit;

/** 分布式接口限流的运行模式。 */
public enum RateLimitMode {
    /** 开发和单元测试默认关闭，不访问 Redis。 */
    DISABLED,

    /** 使用 Redis Lua 脚本在所有后端实例之间共享配额。 */
    REDIS
}
