FROM node:21-alpine@sha256:78c45726ea205bbe2f23889470f03b46ac988d14b6d813d095e2e9909f586f93

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
