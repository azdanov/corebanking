# syntax=docker/dockerfile:1

FROM bellsoft/liberica-openjdk-debian:25 AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test \
    && mv build/libs/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination build/extracted


FROM bellsoft/liberica-openjre-debian:25
WORKDIR /app

RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /sbin/nologin app

COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
