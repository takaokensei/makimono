FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew ./gradlew
COPY gradle/wrapper ./gradle/wrapper
COPY backend ./backend
RUN chmod +x ./gradlew && ./gradlew --no-daemon -p backend installDist

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/build/install/makimono-pairing-backend/ ./
USER 10001:10001
ENV PORT=8080
ENV MAKIMONO_PAIRING_HOST=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["/app/bin/makimono-pairing-backend"]
