FROM node:23-alpine@sha256:f42ff22775892b0e2c81a1c64adeb58bfe5029090b388d1c2211fc8d0d045666

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
