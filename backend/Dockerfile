# Build using Java 21
FROM eclipse-temurin:21-jdk AS source

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src ./src

# Run the pure rule checks without the Compose database.
FROM source AS rule-check

RUN ./mvnw -B -ntp "-Dtest=*RuleCheckTest" test

FROM source AS build

RUN ./mvnw -B -DskipTests package

# Run using Java 21
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
