FROM node:24-alpine@sha256:06d7adf3e0ef61a13f1d28682726366f97a15aa1888c934d3ecec86c33918b53

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
