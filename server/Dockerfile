# 后端镜像：Maven 多阶段构建，产物由 JRE 运行
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只复制 POM 预热依赖，源码变更时可复用此层缓存
COPY pom.xml .
COPY ruoyi-common/pom.xml ruoyi-common/
COPY ruoyi-framework/pom.xml ruoyi-framework/
COPY ruoyi-system/pom.xml ruoyi-system/
COPY ruoyi-quartz/pom.xml ruoyi-quartz/
COPY ruoyi-generator/pom.xml ruoyi-generator/
COPY ruoyi-admin/pom.xml ruoyi-admin/
RUN mvn -B dependency:go-offline -q || true

COPY . .
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN TZ=Asia/Shanghai && ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN addgroup -S app && adduser -S app -G app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai"
ENV SERVER_PORT=9527
ENV LOG_PATH=/app/logs
# 外部配置目录：compose 将宿主机 ./server/config 挂到此处
# 用 additional-location 而非 location，jar 内的 application.yml 仍作为基线，
# 外部同名键覆盖它；optional: 前缀保证目录缺失时不至于启动失败
ENV CONFIG_LOCATION=optional:file:/app/config/

COPY --from=builder /build/ruoyi-admin/target/ruoyi-admin.jar app.jar

# 预创建日志目录并归属 app 用户：logback 以非 root 运行时需要写权限
RUN mkdir -p /app/logs && chown -R app:app /app

USER app

EXPOSE 9527
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:${SERVER_PORT}/ || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.config.additional-location=$CONFIG_LOCATION"]
