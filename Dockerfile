# syntax=docker/dockerfile:1

# ---- 1. Build the React UI ---------------------------------------------------
FROM node:22-alpine AS ui
WORKDIR /ui
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. Build the Spring Boot service with the UI bundled as static content ---
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /app
# Some Docker networks blackhole IPv6; Java would stall there while curl falls back instantly.
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=ui /ui/dist ./src/main/resources/static
# The cache mount keeps ~/.m2 between builds, so only the first build downloads dependencies.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package && cp target/confirmiq-*.jar app.jar

# ---- 3. Runtime ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
# Fonts are needed by PDFBox when rendering pages to read QR codes.
RUN apk add --no-cache fontconfig ttf-dejavu \
 && addgroup -S confirmiq && adduser -S confirmiq -G confirmiq \
 && mkdir -p /data && chown confirmiq:confirmiq /data
WORKDIR /opt/confirmiq
COPY --from=api /app/app.jar app.jar
USER confirmiq
ENV APP_DATA_DIR=/data \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
