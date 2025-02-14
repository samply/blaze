FROM node:23-alpine@sha256:6eaab645f9568369419be82b44474f70325a8166afb4c98f278d3595cf3062ff

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
