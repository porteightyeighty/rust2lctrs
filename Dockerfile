FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/rust2lctrs-*.jar app.jar
ENTRYPOINT ["java", "-cp", "/app/app.jar", "project.web.Demo"]
