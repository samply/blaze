FROM node:23-alpine@sha256:d319827b3b99cca0153f6049fb584a5a4a0ae49252b6dda2314ef564f9857cf2

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
