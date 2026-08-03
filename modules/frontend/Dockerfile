FROM node:24-alpine@sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3

RUN npm r -g npm

COPY build /app

WORKDIR /app
USER 1001

CMD ["node", "."]
