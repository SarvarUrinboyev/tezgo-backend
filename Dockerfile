# ─── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Maven wrapper
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

# Avval faqat dependency'larni yuklab olish (cache uchun)
COPY pom.xml .
RUN ./mvnw dependency:go-offline -q

# Kod nusxalash va build
COPY src src
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user xavfsizlik uchun
RUN addgroup -S tezyol && adduser -S tezyol -G tezyol

WORKDIR /app

# Jar nusxalash
COPY --from=builder /app/target/*.jar app.jar

# Upload papkasi
RUN mkdir uploads && chown -R tezyol:tezyol /app

USER tezyol

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Duser.timezone=UTC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
