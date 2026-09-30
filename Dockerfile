# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy the POM first and resolve dependencies separately so this layer is cached
# across builds unless pom.xml itself changes (dependency downloads are the slow part).
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user rather than the container default root.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/target/order-inventory-api-*.jar app.jar

USER app
EXPOSE 8080

# Spring profile defaults to "postgres" for container/deployment use; override with
# SPRING_PROFILES_ACTIVE if you need something else. All required credentials still come
# from environment variables at runtime (see docs/deployment.md) — none are baked into the image.
ENV SPRING_PROFILES_ACTIVE=postgres

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
