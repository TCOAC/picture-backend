FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 gallery && mkdir -p /app/data/uploads && chown -R gallery:gallery /app
COPY --from=build --chown=gallery:gallery /workspace/target/picture-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER gallery
EXPOSE 8123
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
