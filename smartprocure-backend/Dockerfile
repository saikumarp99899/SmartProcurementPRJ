# ============================================
# STAGE 1: Build the Spring Boot application
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy everything and build
COPY pom.xml .
COPY src ./src

# Build the JAR file (skip tests for faster build)
RUN mvn package -DskipTests

# ============================================
# STAGE 2: Run the application
# ============================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from Stage 1
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080 (Spring Boot default)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
