# ----------- Stage 1: Build the JAR -----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ----------- Stage 2: Runtime Container -----------
FROM eclipse-temurin:17-jdk-jammy

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# These will be passed via ECS environment vars or Secrets Manager
ENV SERVER_PORT=8080
ENV abelini_redis_host=localhost
ENV abelini_redis_pass=pass
ENV abelini_jwt_token=token

# Expose the Spring Boot app port
EXPOSE ${SERVER_PORT}

# ECS health check support
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
  CMD curl --fail http://localhost:${SERVER_PORT}/actuator/health || exit 1

# Start the app with injected config
ENTRYPOINT ["sh", "-c", "java -Dserver.port=$SERVER_PORT -jar app.jar"]
