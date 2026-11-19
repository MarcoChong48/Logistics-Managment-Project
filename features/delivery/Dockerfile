# Decision U-01, B: repeatable builds are ready; real cloud setup is a Place holder.
FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY backend/ ./backend/
COPY frontend/ ./frontend/
COPY THIRD_PARTY_NOTICES.md ./
RUN mvn -f backend/pom.xml -B clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=backend /workspace/backend/target/delivery-app.jar /app/delivery-app.jar
USER 10001
EXPOSE 8080
VOLUME ["/app/data"]
ENV SPRING_PROFILES_ACTIVE=demo
ENTRYPOINT ["java", "-jar", "/app/delivery-app.jar", "--server.address=0.0.0.0"]
