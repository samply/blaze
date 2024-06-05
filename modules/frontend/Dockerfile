FROM node:21-alpine@sha256:fe8d99fad66b7578d9ee320b9295294538e6bd1207324288a65a46aa4cb712d9

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
