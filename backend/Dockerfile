FROM m.daocloud.io/docker.io/library/maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B verify

FROM m.daocloud.io/docker.io/library/eclipse-temurin:21-jre-alpine
RUN addgroup -S app \
    && adduser -S app -G app \
    && mkdir -p /data/reports /data/ops-evidence \
    && chown -R app:app /data/reports /data/ops-evidence
WORKDIR /app
COPY --from=build /workspace/target/bike-backend-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
