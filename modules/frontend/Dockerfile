FROM node:23-alpine@sha256:1467ea23cce893347696b155b9e00e7f0ac7d21555eb6f27236f1328212e045e

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
