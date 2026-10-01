# Built with the project's own wrapper and declared toolchain (Java 21), so a container build and a
# local build cannot drift apart.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# gradle.properties asks for a 2 GB heap, which is right on a 16 GB laptop and wrong on the box this
# gets built on: `make deploy` compiles here while the old stack is still serving. Overriding it in
# the image rather than editing gradle.properties keeps local builds fast and makes the server build
# fit a small VPS. Verified: this exact setting builds the jar inside a hard 1 GB container.
# parallel=false too - rare deploys do not need the speed, and predictable memory is worth more.
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=256m -Dorg.gradle.parallel=false"

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
