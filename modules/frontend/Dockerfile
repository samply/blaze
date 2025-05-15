FROM node:23-alpine@sha256:931edbc0e631c9fa6057653d70174691ea9307c269f41e15aa2814a776727ab1

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
