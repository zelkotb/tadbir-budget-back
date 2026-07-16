# syntax=docker/dockerfile:1
#
# Runtime image for the tadbir-budget backend.
# The project is built ON YOUR MACHINE (mvn package); this image only packages the
# resulting fat jar into a small JRE. No Maven/JDK or source ends up on the server.
#
# Build (locally, after `mvn -DskipTests clean package`):
#   docker build -t tadbir-backend:1.0.0 .
#
FROM eclipse-temurin:17-jre

# Run as a non-root user
RUN useradd -r -u 1001 appuser

WORKDIR /app

# The Spring Boot executable jar produced by the app module
COPY tadbir-budget-app/target/*.jar app.jar

# Uploads and log files live here (mounted as volumes in docker-compose). Create them owned by
# the non-root user so the app can write (named volumes inherit this ownership on first use).
RUN mkdir -p /data/files /var/log/tadbir-budget \
    && chown -R appuser:appuser /app /data /var/log/tadbir-budget

USER appuser
EXPOSE 8080

# Container-aware heap sizing (limit is set in docker-compose)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
