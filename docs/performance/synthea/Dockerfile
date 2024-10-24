FROM eclipse-temurin:21.0.5_11-jre-jammy@sha256:5f8358c9d5615c18e95728e8b8528bda7ff40a7a5da2ac9a35b7a01f5d9b231a

ADD https://github.com/synthetichealth/synthea/releases/download/v3.1.1/synthea-with-dependencies.jar /gen/synthea.jar
COPY synthea.properties /gen/

RUN chown -R 1000:1000 /gen

WORKDIR /gen
USER 1000

ENTRYPOINT ["java", "-jar", "synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-a", "0-100", "-c", "synthea.properties", "-p"]
