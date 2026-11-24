FROM gradle:jdk25 AS build
# 이 컨테이너에서 Gradle로 소스를 컴파일하고 실행 JAR를 만든다.
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
ARG MODULE
RUN gradle :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:25-jre
# 이 이미지에서 JAR를 실행한다. curl은 앱의 health 주소를 호출할 때 사용한다.
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
ARG MODULE
COPY --from=build /home/gradle/project/${MODULE}/build/libs/${MODULE}.jar app.jar
USER 10001
# 앱 프로세스는 일반 사용자 권한으로 실행한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]