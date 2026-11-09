# Decision U-01, B: repeatable builds are ready; real cloud setup is a Place holder.
FROM node:24-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY backend/ ./backend/
COPY --from=frontend /workspace/frontend/dist/ ./frontend/dist/
RUN mvn -f backend/pom.xml -B clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=backend /workspace/backend/target/delivery-app.jar /app/delivery-app.jar
USER 10001
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "/app/delivery-app.jar", "--server.address=0.0.0.0", "--spring.profiles.active=demo"]

