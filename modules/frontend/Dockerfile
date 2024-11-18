FROM node:23-alpine@sha256:ecefaffd4706c5879af52e022fdb8ea30cbd6590e2a30d05347790d690727c6c

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
