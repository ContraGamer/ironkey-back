# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /home/gradle/src

COPY --chown=root:root . .

RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S ironkey && adduser -S ironkey -G ironkey

COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar

USER ironkey

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar"]
