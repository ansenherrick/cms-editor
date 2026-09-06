# Build and test the Ktor distribution in Linux before producing the runtime image.
FROM gradle:8.12.1-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared/build.gradle.kts ./shared/
COPY shared/src ./shared/src
COPY server/build.gradle.kts ./server/
COPY server/src ./server/src
RUN gradle --no-daemon --console=plain :server:test :server:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app

EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]