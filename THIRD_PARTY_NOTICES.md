# Third-party notices

Stage 0 adds no third-party libraries, fonts, scripts or icons. Its frontend uses the project stylesheet and inline SVG sprite from the Admin design note v2.3. System fonts are rendered by the browser.

The copied backend retains the Maven-managed dependencies, Maven wrapper and Eclipse Temurin image declared in the database-setup snapshot. The copied Compose stack retains the PostgreSQL 17 image. See [the POM](backend/pom.xml), [the Dockerfile](backend/Dockerfile) and [Compose](compose.yaml) for the inherited declarations.

Stage 1 adds the Spring Boot-managed Spring Security starter for request protection and BCrypt. Spring Boot and Spring Security are licensed under Apache License 2.0. [Spring Boot 4.1.2 dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) · [Spring Security license](https://github.com/spring-projects/spring-security/blob/main/LICENSE.txt)
