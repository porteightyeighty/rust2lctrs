FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -B -q -DskipTests package

FROM docker.io/library/eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/target/rust2lctrs-*.jar app.jar
ENTRYPOINT ["java", "-cp", "/app/app.jar", "project.web.Demo"]
