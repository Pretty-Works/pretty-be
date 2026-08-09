# PrettyWorks 백엔드 실행 이미지
#
# 빌드는 로컬(IntelliJ)에서 하고, 여기서는 만들어진 jar만 실어 나른다.
# EC2에서 gradle 빌드를 돌리면 메모리를 다 먹고 죽는다.
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

# 로그 파일 경로가 상대경로(logs/prettyworks.log)라 workdir 아래에 생긴다.
RUN mkdir -p logs

# IntelliJ에서 bootJar 하면 build/libs/ 아래 생기는 파일.
# -plain.jar 이 아니라 실행 가능한 쪽을 넣어야 한다.
ARG JAR_FILE=build/libs/PrettyWorks_BE-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# 컨테이너 기본 타임존이 UTC라 로그 시각이 9시간 어긋난다.
ENV TZ=Asia/Seoul

# 컨테이너에 할당된 메모리의 60%까지만 힙으로 쓴다. 안 그러면 OOM Killer에게 먼저 맞는다.
# ⚠️ 이 옵션이 "컨테이너" 기준으로 동작하려면 compose 에서 mem_limit 을 줘야 한다.
#    limit 이 없으면 JVM 은 호스트 전체 메모리를 기준으로 잡는다. (Phase 8-4 참고)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]