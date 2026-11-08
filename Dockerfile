ARG GRADLE_VERSION=8.13
FROM gradle:${GRADLE_VERSION}-jdk21 AS gradle-distribution

FROM eclipse-temurin:21-jdk-jammy AS build
ARG GRADLE_VERSION
WORKDIR /workspace
COPY --from=gradle-distribution /opt/gradle /opt/gradle
COPY settings.gradle build.gradle ./
COPY gradle ./gradle
RUN grep -Fq "/gradle-${GRADLE_VERSION}-bin.zip" gradle/wrapper/gradle-wrapper.properties
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle /opt/gradle/bin/gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system frizer && useradd --system --gid frizer frizer
WORKDIR /app
COPY --from=build --chown=frizer:frizer /workspace/build/libs/*.jar app.jar
USER frizer
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
