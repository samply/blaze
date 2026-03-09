FROM node:24-alpine@sha256:7fddd9ddeae8196abf4a3ef2de34e11f7b1a722119f91f28ddf1e99dcafdf114

RUN npm r -g npm

COPY build /app

WORKDIR /app
USER 1001

CMD ["node", "."]
