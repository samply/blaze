FROM eclipse-temurin:21.0.4_7-jre-jammy@sha256:ce644c2da74d34ca9b9ddd6d3aa72d0f80a4d8f90454270985af8fc986c60dfc

ADD https://github.com/synthetichealth/synthea/releases/download/v3.1.1/synthea-with-dependencies.jar /gen/synthea.jar
COPY synthea.properties /gen/

RUN chown -R 1000:1000 /gen

WORKDIR /gen
USER 1000

ENTRYPOINT ["java", "-jar", "synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-a", "0-100", "-c", "synthea.properties", "-p"]
