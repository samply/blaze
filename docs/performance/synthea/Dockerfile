FROM eclipse-temurin:21.0.7_6-jre-jammy@sha256:daebe9ae03913ec4b2dadd8df60f3ea3df1aa6108fecd5d324d000bdd5c4c816

ADD https://github.com/synthetichealth/synthea/releases/download/v3.1.1/synthea-with-dependencies.jar /gen/synthea.jar
COPY synthea.properties /gen/

RUN chown -R 1000:1000 /gen

WORKDIR /gen
USER 1000

ENTRYPOINT ["java", "-jar", "synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-a", "0-100", "-c", "synthea.properties", "-p"]
