# 基础镜像允许由企业镜像仓库覆盖，默认仍使用社区官方镜像名称。
# 默认基础镜像固定到已验收摘要，防止同名标签在扫描和发布之间发生漂移。
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21-alpine@sha256:5a8b906c4faa11d33f6c74758f67db8eac25441e14b0729f6d50ff78992be58a
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

# 构建阶段只负责解析依赖并生成可执行 Spring Boot 包。
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -B -DskipTests package

# 运行阶段不包含 Maven 和源代码，并使用非特权用户降低容器逃逸后的权限。
FROM ${RUNTIME_IMAGE}
RUN apk add --no-cache wget \
    && addgroup -S agentmind \
    && adduser -S -G agentmind agentmind
WORKDIR /app
COPY --from=builder /workspace/target/agentmind-backend-0.1.0-SNAPSHOT.jar /app/agentmind.jar
RUN chown -R agentmind:agentmind /app
USER agentmind
EXPOSE 8081
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=4 \
  CMD wget -qO- http://localhost:8081/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/agentmind.jar"]
