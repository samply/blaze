FROM node:21-alpine@sha256:577f8eb599858005100d84ef3fb6bd6582c1b6b17877a393cdae4bfc9935f068

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
