FROM node:24-alpine@sha256:01743339035a5c3c11a373cd7c83aeab6ed1457b55da6a69e014a95ac4e4700b

RUN npm r -g npm

COPY build /app

WORKDIR /app
USER 1001

CMD ["node", "."]
