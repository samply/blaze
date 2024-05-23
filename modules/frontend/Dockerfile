FROM node:21-alpine@sha256:d44678a321331f2f003b51303cc5105f9787637e0524cf94d4323d08050a99c9

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
