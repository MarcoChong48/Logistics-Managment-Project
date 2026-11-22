# Third-party notices

This inventory covers the Admin project through Stage 9. The Admin browser pages use the LMS stylesheet, inline icon sprite and system fonts. They load no third-party scripts, web fonts or icon packages. Stages 5–9 add no third-party runtime dependencies.

## Application and build dependencies

| Component | Where it is used | License and source |
| --- | --- | --- |
| Spring Boot 4.1.2 and its managed Spring Web MVC and Spring Data JPA dependencies | Backend application, HTTP endpoints and database mapping in `backend/pom.xml` | Apache License 2.0. [Spring Boot](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) · [Spring Framework](https://github.com/spring-projects/spring-framework/blob/main/LICENSE.txt) · [Spring Data JPA](https://github.com/spring-projects/spring-data-jpa/blob/main/LICENSE.txt) |
| Spring Security, managed by Spring Boot | Request protection and BCrypt in `backend/pom.xml`, added in Stage 1 | Apache License 2.0. [Spring Security](https://github.com/spring-projects/spring-security/blob/main/LICENSE.txt) |
| PostgreSQL JDBC Driver, managed by Spring Boot | Runtime database driver in `backend/pom.xml` | BSD 2-Clause. [pgJDBC](https://jdbc.postgresql.org/license/) |
| ZXing Core 3.5.4 | Product SKU QR generation in `backend/pom.xml`, added in Stage 4 | Apache License 2.0. [ZXing 3.5.4](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4) |
| Spring Boot Web MVC test starter | Test-scope dependency in `backend/pom.xml` for rule and flow checks | Apache License 2.0 for the starter. The test dependencies it resolves have their own upstream notices. [Spring Boot](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) |
| Apache Maven Wrapper 3.3.4 and Apache Maven 3.10.0 | Copied wrapper scripts `backend/mvnw` and `backend/mvnw.cmd` and the build distribution declared in `backend/.mvn/wrapper/maven-wrapper.properties` | Apache License 2.0. [Apache license](https://www.apache.org/licenses/LICENSE-2.0) · [Maven Wrapper](https://maven.apache.org/wrapper/) |

Maven resolves transitive dependencies from [the POM](backend/pom.xml). The packaged Spring Boot JAR carries its runtime dependency JARs under `BOOT-INF/lib/`. Their upstream license and notice files remain in their own archives. The test dependencies are used during checks.

## Container images

| Image | Use | License and source |
| --- | --- | --- |
| `eclipse-temurin:21-jdk` and `eclipse-temurin:21-jre` | Build and runtime in `backend/Dockerfile` | Temurin OpenJDK is supplied with GPLv2 and the Classpath Exception where applicable. OpenJDK and the bundled programs carry their respective licenses under the Java installation's `legal/` directory. [Eclipse Temurin image](https://hub.docker.com/_/eclipse-temurin) |
| `postgres:17` | Local PostgreSQL service in `compose.yaml` | PostgreSQL License for the database server. The image also carries its base distribution's notices. [PostgreSQL license](https://www.postgresql.org/about/licence/) · [PostgreSQL image](https://hub.docker.com/_/postgres) |
