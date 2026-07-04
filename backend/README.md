# AgentMind Backend

Personal Knowledge Agent 后端服务。

## 当前阶段

当前处于 Stage 1：Spring Boot 基础工程搭建。

已包含：

- Java 21 + Spring Boot 3.x Maven 工程。
- 统一 API 响应结构。
- 统一分页响应结构。
- 全局异常处理。
- 健康检查接口。
- 基础测试用例。

暂未包含：

- 数据库持久化。
- 用户登录与权限。
- 文件上传。
- URL 采集。
- Spring AI。
- RAG 问答。

## 运行方式

要求：

- JDK 21
- Maven 3.9+

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/v1/health
```

预期响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "application": "agentmind-backend",
    "status": "UP",
    "checkedAt": "2026-07-04T22:30:00+08:00"
  },
  "traceId": null,
  "timestamp": "2026-07-04T22:30:00+08:00"
}
```

## 测试

```powershell
cd D:\Program\AgentMind\backend
mvn test
```

本项目在 `backend/.mvn/settings.xml` 中指定了仓库内 `.m2/repository` 作为 Maven 本地仓库，
用于避免受开发机器全局 Maven 配置影响。`.m2/` 目录不提交到 Git。

## 当前环境验证记录

本阶段已尝试执行：

```powershell
mvn test
```

验证结果：

- 已确认项目级 Maven 配置生效，不再写入机器全局只读仓库。
- 当前环境无法联网访问 Maven Central，Spring Boot 依赖下载失败。
- 当前机器 Maven 默认使用 Java 17，而项目规划要求 Java 21；后续本地完整验证需要切换到 JDK 21。

因此，具备网络和 JDK 21 后，应重新执行：

```powershell
cd D:\Program\AgentMind\backend
mvn test
```
