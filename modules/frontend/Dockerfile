FROM node:24-alpine@sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e

RUN npm r -g npm

COPY build /app

WORKDIR /app
USER 1001

CMD ["node", "."]
