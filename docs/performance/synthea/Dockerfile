FROM eclipse-temurin:21.0.5_11-jre-jammy@sha256:ebeb51a2a147be42b7d42342fecbeb2d9cb764f7742054024ac9a17bc1c8a21b

ADD https://github.com/synthetichealth/synthea/releases/download/v3.1.1/synthea-with-dependencies.jar /gen/synthea.jar
COPY synthea.properties /gen/

RUN chown -R 1000:1000 /gen

WORKDIR /gen
USER 1000

ENTRYPOINT ["java", "-jar", "synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-a", "0-100", "-c", "synthea.properties", "-p"]
