FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN sed -i 's/\r$//' mvnw && bash ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/picus-0.0.1-SNAPSHOT.jar picus.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "picus.jar"]
