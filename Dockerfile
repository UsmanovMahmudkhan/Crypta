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
