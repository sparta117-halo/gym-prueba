FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY backend/app/pom.xml pom.xml
RUN mvn dependency:go-offline -q 2>/dev/null || true
COPY backend/app/src src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
