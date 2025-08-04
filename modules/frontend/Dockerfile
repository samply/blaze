FROM node:22-alpine@sha256:f9a425f0ef0b8ab90c7be73eb85cc3a5ae37cfefd3c315db1d787c58eef44fad

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
