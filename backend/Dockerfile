# Built with the project's own wrapper and declared toolchain (Java 21), so a container build and a
# local build cannot drift apart.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies resolve in their own layer. They change far less often than source, so editing a
# Java file reuses the cached Gradle graph instead of re-downloading it.
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies

COPY src src
# Tests are not run here. They run locally and in CI, where a failure stops a merge; a deploy that
# silently reruns them is slow, and one that skips them while implying otherwise is worse.
RUN ./gradlew --no-daemon --quiet clean bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /src/build/libs/visionone-api-*.jar app.jar
USER app
EXPOSE 8080

# Size the heap from the container's limit, not the host's. Without this the JVM reads the VPS's
# total memory, sizes a heap it is not allowed to have, and gets OOM-killed under load.
# SerialGC because this is a single small container, where G1's overhead buys nothing.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
