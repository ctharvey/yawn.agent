FROM gradle:8.14.3-jdk21-alpine AS build

WORKDIR /workspace

# Copy agent source
COPY yawn.agent/gradle yawn.agent/gradle
COPY yawn.agent/gradlew yawn.agent/gradlew
COPY yawn.agent/build.gradle.kts yawn.agent/build.gradle.kts
COPY yawn.agent/settings.gradle.kts yawn.agent/settings.gradle.kts
COPY yawn.agent/gradle.properties yawn.agent/gradle.properties

RUN sed -i 's/\r$//' yawn.agent/gradlew \
    && chmod +x yawn.agent/gradlew

# Pre-resolve dependencies — cached layer
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    cd yawn.agent && ./gradlew --no-daemon dependencies

COPY yawn.agent/src yawn.agent/src

RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    cd yawn.agent && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd -r yawn \
    && useradd -r -g yawn yawn \
    && mkdir -p /logs \
    && chown -R yawn:yawn /app /logs

COPY --from=build /workspace/yawn.agent/build/libs/*.jar /app/app.jar

USER yawn

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
