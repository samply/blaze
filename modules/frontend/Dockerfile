FROM node:21-alpine

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
