FROM node:23-alpine@sha256:a34e14ef1df25b58258956049ab5a71ea7f0d498e41d0b514f4b8de09af09456

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
