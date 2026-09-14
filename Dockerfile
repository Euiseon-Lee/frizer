FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system frizer && useradd --system --gid frizer frizer
WORKDIR /app
COPY --from=build --chown=frizer:frizer /workspace/build/libs/*.jar app.jar
USER frizer
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
