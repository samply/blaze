FROM eclipse-temurin:21.0.6_7-jre-jammy@sha256:0f48eeecb11c875243f4738ffd7ade040b38aad0529a5fd26393aa8a8bf75145

ADD https://github.com/synthetichealth/synthea/releases/download/v3.1.1/synthea-with-dependencies.jar /gen/synthea.jar
COPY synthea.properties /gen/

RUN chown -R 1000:1000 /gen

WORKDIR /gen
USER 1000

ENTRYPOINT ["java", "-jar", "synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-a", "0-100", "-c", "synthea.properties", "-p"]
