# 基础镜像
FROM amazoncorretto:17-alpine AS builder
# 指定工作目录
WORKDIR /app
# 绑定参数
ARG JAR_FILE=./*.jar
# 将jar包copy至镜像容器中
COPY ${JAR_FILE} application.jar
# 通过工具spring-boot-jarmode-layertools从application.jar中提取拆分后的构建结果
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher

# 正式构建镜像
FROM amazoncorretto:17-alpine
# 指定工作目录
WORKDIR /app
# 定义一些环境变量，方便环境变量传参
ENV JVM_OPTS=""
ENV JAVA_OPTS=""
ENV DATA_DIR="/data"

# 给 JDK17 打开 Hessian2 反射用到的模块
ENV JAVA_TOOL_OPTIONS="--add-opens=java.base/java.math=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED"

RUN echo "https://mirrors.aliyun.com/alpine/v3.22/main/" > /etc/apk/repositories && \
    echo "https://mirrors.aliyun.com/alpine/v3.22/community/" >> /etc/apk/repositories && \
    apk update \
    && apk add ca-certificates font-noto fontconfig tzdata \ 
    && rm -rf /var/cache/apk/* \
    && mkdir -p $DATA_DIR

# 前一阶段从jar中提取除了多个文件，这里分别执行COPY命令复制到镜像空间中，每次COPY都是一个layer
COPY --from=builder app/application/dependencies/ ./
COPY --from=builder app/application/spring-boot-loader/ ./
COPY --from=builder app/application/snapshot-dependencies/ ./
COPY --from=builder app/application/application/ ./

# 挂载本地目录
VOLUME ["${DATA_DIR}"]
# 暴露端口号（看具体服务需要暴露的端口号）
#EXPOSE 8080
# 启动 jar 的命令
ENTRYPOINT ["java", "-Xmx512m", "-Xms512m","org.springframework.boot.loader.launch.JarLauncher"]
