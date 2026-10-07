FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --quiet dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode --quiet -DskipTests package \
    && cp target/TaskNest-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system tasknest && useradd --system --gid tasknest tasknest \
    && mkdir -p /app/photos && chown tasknest:tasknest /app/photos
WORKDIR /app
ENV PHOTOS_DIRECTORY=/app/photos
COPY --from=build /workspace/app.jar app.jar
USER tasknest
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
