FROM node:24-alpine@sha256:156b55f92e98ccd5ef49578a8cea0df4679826564bad1c9d4ef04462b9f0ded6

RUN npm r -g npm

COPY build /app

WORKDIR /app
USER 1001

CMD ["node", "."]
