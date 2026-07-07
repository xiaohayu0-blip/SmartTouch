# ==================== SmartTouch Server Docker镜像 ====================
# 构建：docker build -t smart-touch-server .
# 多阶段构建，减小最终镜像体积

# ---- Stage 1: 编译阶段 ----
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# 先复制POM文件，利用Docker缓存层加速重复构建
COPY pom.xml ./
COPY smart-touch-server/pom.xml smart-touch-server/
RUN mvn dependency:go-offline -pl smart-touch-server -B

# 复制源码并编译
COPY smart-touch-server/src smart-touch-server/src
RUN mvn clean package -pl smart-touch-server -DskipTests -B

# ---- Stage 2: 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非root用户运行（安全最佳实践）
RUN addgroup -S app && adduser -S app -G app
USER app

# 复制编译产物
COPY --from=builder /build/smart-touch-server/target/smart-touch-server.jar app.jar

# JVM参数可通过环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
