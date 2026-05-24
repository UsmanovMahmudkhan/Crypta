# ==============================================================================
# Build Stage
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom.xml and actual source code to build the package
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ==============================================================================
# Runtime Stage
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create a secure, non-privileged system group and user
RUN addgroup -S sovereign && adduser -S sovereign -G sovereign

# Copy the compiled JAR from the builder stage
COPY --from=builder /app/target/sovereign-comm-platform-0.1.0-SNAPSHOT.jar app.jar

# Set ownership of the runtime assets to the non-root user
RUN chown -R sovereign:sovereign /app

# Switch to the non-root user for security hardening
USER sovereign

# Expose the default Spring Boot port
EXPOSE 8080

# Configure execution entrypoint
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
