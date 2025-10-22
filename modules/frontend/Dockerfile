FROM node:22-alpine@sha256:bd26af08779f746650d95a2e4d653b0fd3c8030c44284b6b98d701c9b5eb66b9

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
