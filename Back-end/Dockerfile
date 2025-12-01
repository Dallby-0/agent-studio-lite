# 多阶段构建：构建阶段
FROM maven:3.8.5-openjdk-17 AS build

# 设置工作目录
WORKDIR /app

# 复制pom.xml并下载依赖（利用Docker缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jdk

# 设置工作目录
WORKDIR /app

# 从构建阶段复制构建好的jar包
COPY --from=build /app/target/back-end-1.0.0.jar app.jar

# 设置时区
RUN apt-get update && \
    apt-get install -y tzdata curl && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    rm -rf /var/lib/apt/lists/*

# 添加健康检查
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health/status || exit 1

# 配置JVM参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 暴露端口
EXPOSE 8080

# 启动命令
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
