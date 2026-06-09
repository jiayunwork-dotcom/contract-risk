FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-chi-tra && \
    apk add --no-cache poppler-utils && \
    rm -rf /var/cache/apk/*

ENV TESSDATA_PREFIX=/usr/share/tessdata

COPY --from=build /app/target/*.jar app.jar

RUN mkdir -p /app/uploads /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
