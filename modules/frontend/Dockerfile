FROM node:26-alpine@sha256:c6ad9e9c1d80f6eb607c26b4b0a132ff7a2344fad1c78ea3baa2a7d4c8de6d94

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
