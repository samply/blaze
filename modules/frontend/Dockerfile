FROM node:22-alpine@sha256:2d20b9dbc1765bfc9ea488548701f04e071d45dc1dd730f4f280510a24075ac4

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
