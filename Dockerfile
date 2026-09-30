# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY ledger-core ledger-core
COPY ledger-service ledger-service
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package -pl ledger-service -am

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home ledger
WORKDIR /app
COPY --from=build /src/ledger-service/target/ledger-service-*.jar /app/ledger.jar
USER 10001
EXPOSE 8080
# Container-aware heap: size from the pod's memory limit instead of the node's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/ledger.jar"]
