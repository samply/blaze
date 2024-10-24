FROM eclipse-temurin:21.0.5_11-jre-jammy@sha256:5d7781c6aabbc2f8673ec1899f2fdf6851f20c94dcdefeb8a1ca1e37e9c13e92

ADD https://github.com/synthetichealth/synthea/releases/download/v3.1.1/synthea-with-dependencies.jar /gen/synthea.jar
COPY synthea.properties /gen/

RUN chown -R 1000:1000 /gen

WORKDIR /gen
USER 1000

ENTRYPOINT ["java", "-jar", "synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-a", "0-100", "-c", "synthea.properties", "-p"]
