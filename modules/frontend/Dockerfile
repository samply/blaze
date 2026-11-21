FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5

LABEL org.opencontainers.image.title="Blaze Frontend"
LABEL org.opencontainers.image.description="Frontend of the Blaze FHIR® Server"
LABEL org.opencontainers.image.url="https://blaze-server.org"
LABEL org.opencontainers.image.documentation="https://blaze-server.org"
LABEL org.opencontainers.image.source="https://github.com/samply/blaze"
LABEL org.opencontainers.image.licenses="Apache-2.0"

RUN npm r -g npm

COPY build /app

WORKDIR /app
USER 1001

CMD ["node", "."]
