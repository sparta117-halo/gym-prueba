FROM maven:3.9.9-eclipse-temurin-21 AS build

ARG MODULE

WORKDIR /workspace

COPY . .

RUN chmod +x mvnw \
  && ./mvnw -pl ${MODULE} -am package -DskipTests \
  && find /workspace/${MODULE}/target -maxdepth 1 -name '*.jar' ! -name 'original-*.jar' -exec cp {} /workspace/application.jar \;

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/application.jar /app/app.jar
COPY --from=build /workspace/config-repo /app/config-repo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]