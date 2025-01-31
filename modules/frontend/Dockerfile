FROM node:23-alpine@sha256:6732970c50bfe5aaf9ebfb2a6897d610c42d1bf287cd78fb072fd65b24d6164a

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
